/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;

import java.util.*;

/**
 * SpatialRuleLookup implementation using an array as data structure. Currently limited to 255 ruleContainers
 * The covered area is indexed as tiles, with every tile being "quadratic" having the same degree length on every side.
 *
 * @author Robin Boldt
 */
class SpatialRuleLookupArray implements SpatialRuleLookup {

    // resolution in full decimal degrees
    private final double resolution;
    // When filling a tile with a rule we do a 5 point check, diff is the distance from the center we place a check point
    private final double checkDiff;
    private final BBox bounds;
    private final boolean exact;
    private final int EMPTY_RULE_INDEX = 0;

    private final byte[][] lookupArray;
    private final List<SpatialRuleContainer> ruleContainers = new ArrayList<>();
    private final Map<SpatialRule, Integer> singleRulesIndices = new HashMap<>();
    private final List<SpatialRule> singleRules = new ArrayList<>();

    /**
     * @param bounds     the outer bounds for the Lookup
     * @param resolution of the array in decimal degrees, see: https://en.wikipedia.org/wiki/Decimal_degrees
     *                   The downside of using decimal degrees is that this is not fixed to a certain m range as
     * @param exact      if exact it will also perform a polygon contains for border tiles, might fail for small holes
     *                   in the Polygon that are not represented in the tile array.
     */
    SpatialRuleLookupArray(BBox bounds, double resolution, boolean exact) {
        if (bounds == null)
            throw new IllegalArgumentException("BBox cannot be null");
        if (resolution < 1e-100)
            throw new IllegalArgumentException("resolution cannot be that high " + resolution);

        this.bounds = bounds;
        this.resolution = resolution;
        this.checkDiff = (resolution / 2) - (resolution / 10);
        this.exact = exact;

        lookupArray = new byte[getNumberOfXGrids()][getNumberOfYGrids()];
        addSingleRule(SpatialRule.EMPTY);
        ruleContainers.add(new SpatialRuleContainer() {
            {
                this.rules.add(SpatialRule.EMPTY);
            }

            @Override
            public SpatialRuleContainer addRule(SpatialRule spatialRule) {
                throw new IllegalArgumentException("Cannot add to empty rule container");
            }

            @Override
            public SpatialRuleContainer addRules(Collection<SpatialRule> rules) {
                throw new IllegalArgumentException("Cannot add to empty rule container");
            }
        });
    }

    private int getNumberOfYGrids() {
        return (int) Math.ceil(Math.abs(bounds.maxLat - bounds.minLat) / resolution);
    }

    private int getNumberOfXGrids() {
        return (int) Math.ceil(Math.abs(bounds.maxLon - bounds.minLon) / resolution);
    }

    @Override
    public SpatialRule lookupRule(double lat, double lon) {
        if (lon < bounds.minLon || lon > bounds.maxLon || lat < bounds.minLat || lat > bounds.maxLat)
            return SpatialRule.EMPTY;

        int xIndex = getXIndexForLon(lon);
        int yIndex = getYIndexForLat(lat);
        int ruleIndex = getRuleContainerIndex(xIndex, yIndex);
        SpatialRuleContainer ruleContainer = ruleContainers.get(ruleIndex);
        if (ruleContainer.size() == 1) {
            if (!exact)
                return ruleContainer.first();
            if (!isBorderTile(xIndex, yIndex, ruleIndex))
                return ruleContainer.first();
        }

        for (SpatialRule rule : ruleContainer.getRules()) {
            for (Polygon p : rule.getBorders()) {
                if (p.contains(lat, lon)) {
                    return rule;
                }
            }
        }

        return SpatialRule.EMPTY;
    }

    protected int getRuleContainerIndex(int xIndex, int yIndex) {
        if (xIndex < 0 || xIndex >= lookupArray.length) {
            return EMPTY_RULE_INDEX;
        }
        if (yIndex < 0 || yIndex >= lookupArray[0].length) {
            return EMPTY_RULE_INDEX;
        }
        return castByteToInt(lookupArray[xIndex][yIndex]);
    }

    /**
     * Might fail for small holes that do not occur in the array
     */
    protected boolean isBorderTile(int xIndex, int yIndex, int ruleIndex) {
        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                if (i != xIndex && j != yIndex)
                    if (ruleIndex != getRuleContainerIndex(i, j))
                        return true;
            }
        }
        return false;
    }

    @Override
    public SpatialRule lookupRule(GHPoint point) {
        return lookupRule(point.getLat(), point.getLon());
    }

    private int getXIndexForLon(double lon) {
        return (int) Math.floor(Math.abs(lon - bounds.minLon) / resolution);
    }

    private int getYIndexForLat(double lat) {
        return (int) Math.floor(Math.abs(lat - bounds.minLat) / resolution);
    }

    @Override
    public void addRule(SpatialRule rule) {
        if (rule == null)
            throw new IllegalArgumentException("rule cannot be null");

        if (rule.equals(SpatialRule.EMPTY))
            throw new IllegalArgumentException("rule cannot be EMPTY");

        addSingleRule(rule);
        int ruleContainerIndex = addRuleContainer(new SpatialRuleContainer().addRule(rule));
        for (Polygon polygon : rule.getBorders()) {
            for (int i = getXIndexForLon(polygon.getMinLon()); i < getXIndexForLon(polygon.getMaxLon()) + 1; i++) {
                for (int j = getYIndexForLat(polygon.getMinLat()); j < getYIndexForLat(polygon.getMaxLat()) + 1; j++) {
                    if (i >= lookupArray.length || j >= lookupArray[0].length) {
                        continue;
                    }

                    GHPoint center = getCoordinatesForIndex(i, j);
                    // TODO: Consider creating a new method in Polygon that does the 5 checks - p.partOfTile?
                    if (polygon.contains(center) ||
                            polygon.contains(center.getLat() - checkDiff, center.getLon() - checkDiff) ||
                            polygon.contains(center.getLat() - checkDiff, center.getLon() + checkDiff) ||
                            polygon.contains(center.getLat() + checkDiff, center.getLon() - checkDiff) ||
                            polygon.contains(center.getLat() + checkDiff, center.getLon() + checkDiff)) {

                        if (lookupArray[i][j] == EMPTY_RULE_INDEX) {
                            lookupArray[i][j] = (byte) ruleContainerIndex;
                        } else {
                            // Merge Rules
                            SpatialRuleContainer curContainer = getContainerFor2DIndex(i, j);
                            SpatialRuleContainer newContainer = curContainer.copy().addRule(rule);
                            int newRuleContainerIndex = addRuleContainer(newContainer);
                            lookupArray[i][j] = (byte) newRuleContainerIndex;
                        }
                    }
                }
            }
        }
    }

    private void addSingleRule(SpatialRule rule) {
        int index = singleRules.indexOf(rule);
        if (index >= 0)
            throw new IllegalArgumentException("Rule " + rule + " already contained at " + index + ". " + ((index >= ruleContainers.size() ? "" : "Existing:" + ruleContainers.get(index))));

        singleRulesIndices.put(rule, singleRules.size());
        singleRules.add(rule);
    }

    public SpatialRule getSpatialRule(int id) {
        if (id < 0 || id >= ruleContainers.size())
            throw new IllegalArgumentException("SpatialRuleId " + id + " is illegal");

        SpatialRule rule = singleRules.get(id);
        if (rule == null)
            throw new IllegalArgumentException("SpatialRuleId " + id + " not found");
        return rule;
    }

    /**
     * This method adds the container if no such rule container exists in this lookup and returns the index otherwise.
     */
    int addRuleContainer(SpatialRuleContainer container) {
        int newIndex = this.ruleContainers.indexOf(container);
        if (newIndex >= 0)
            return newIndex;

        newIndex = ruleContainers.size();
        if (newIndex >= 255)
            throw new IllegalStateException("No more spatial rule container fit into this lookup as 255 combination of ruleContainers reached");

        this.ruleContainers.add(container);
        return newIndex;
    }

    @Override
    public int getSpatialId(SpatialRule rule) {
        if (rule == null)
            throw new IllegalArgumentException("rule parameter cannot be null");

        Integer integ = singleRulesIndices.get(rule);
        if (integ == null)
            throw new IllegalArgumentException("Cannot find rule " + rule);
        return integ;
    }

    private int castByteToInt(byte b) {
        return b & 0xFF;
    }

    private SpatialRuleContainer getContainerFor2DIndex(int x, int y) {
        return this.ruleContainers.get(getRuleContainerIndex(x, y));
    }

    private GHPoint getCoordinatesForIndex(int x, int y) {
        double lon = bounds.minLon + x * resolution + resolution / 2;
        double lat = bounds.minLat + y * resolution + resolution / 2;
        return new GHPoint(lat, lon);
    }

    @Override
    public int size() {
        return singleRules.size();
    }

    @Override
    public BBox getBounds() {
        return bounds;
    }
}
