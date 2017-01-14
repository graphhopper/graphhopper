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

import java.util.ArrayList;
import java.util.List;

/**
 * SpatialRuleLookup implementation using an array as datastructure.
 * Currently limited to 255 rules
 * <p>
 * The covered area is indexed as tiles, with every tile being "quadratic" having the same degree length on every side.
 * <p>
 * Currently one one rule per Tile is allowed. The later added rule will be added to the tile.
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupArray extends AbstractSpatialRuleLookup {

    // resolution in full decimal degrees
    private final double resolution;
    // When filling a tile with a rule we do a 5 point check, diff is the distance from the center we place a check point
    private final double checkDiff;
    private final BBox bounds;
    private final boolean exact;
    private final int EMPTY_RULE_INDEX = 0;

    private final byte[][] lookupArray;
    private final List<SpatialRuleContainer> rules = new ArrayList<>();

    /**
     * @param bounds     the outer bounds for the Lookup
     * @param resolution of the array in decimal degrees, see: https://en.wikipedia.org/wiki/Decimal_degrees
     *                   The downside of using decimal degrees is that this is not fixed to a certain m range as
     * @param exact      if exact it will also perform a polygon contains for border tiles, might fail for small holes
     *                   in the Polygon that are not represented in the tile array.
     */
    public SpatialRuleLookupArray(BBox bounds, double resolution, boolean exact) {
        this.bounds = bounds;
        this.resolution = resolution;
        this.checkDiff = (resolution / 2) - (resolution / 10);
        this.exact = exact;

        this.lookupArray = new byte[getNumberOfXGrids()][getNumberOfYGrids()];
        // Byte array is initialized with 0, => at index 0 is the EMPTY_RULE
        rules.add(EMPTY_RULE_CONTAINER);
    }

    private int getNumberOfYGrids() {
        return (int) Math.ceil(Math.abs(bounds.maxLat - bounds.minLat) / resolution);
    }

    private int getNumberOfXGrids() {
        return (int) Math.ceil(Math.abs(bounds.maxLon - bounds.minLon) / resolution);
    }

    @Override
    public SpatialRule lookupRule(double lat, double lon) {
        if (lon < bounds.minLon) {
            return EMPTY_RULE;
        }
        if (lon > bounds.maxLon) {
            return EMPTY_RULE;
        }
        if (lat < bounds.minLat) {
            return EMPTY_RULE;
        }
        if (lat > bounds.maxLat) {
            return EMPTY_RULE;
        }
        int xIndex = getXIndexForLon(lon);
        int yIndex = getYIndexForLat(lat);
        int ruleIndex = getRuleIndex(xIndex, yIndex);
        SpatialRuleContainer ruleContainer = rules.get(ruleIndex);
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

        return EMPTY_RULE;
    }

    protected int getRuleIndex(int xIndex, int yIndex) {
        if (xIndex < 0 || xIndex > lookupArray.length) {
            return EMPTY_RULE_INDEX;
        }
        if (yIndex < 0 || yIndex > lookupArray[0].length) {
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
                    if (ruleIndex != getRuleIndex(i, j))
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
        SpatialRuleContainer spatialRuleContainer = new SpatialRuleContainer().addRule(rule);
        int ruleIndex = this.rules.indexOf(spatialRuleContainer);
        if (ruleIndex < 0) {
            rules.add(spatialRuleContainer);
            ruleIndex = rules.size() - 1;
        }

        if (ruleIndex > 255) {
            throw new IllegalStateException("Cannot fit more than 255 rules");
        }

        for (Polygon polygon : rule.getBorders()) {
            for (int i = getXIndexForLon(polygon.getMinLon()); i < getXIndexForLon(polygon.getMaxLon()) + 1; i++) {
                for (int j = getYIndexForLat(polygon.getMinLat()); j < getYIndexForLat(polygon.getMaxLat()) + 1; j++) {
                    GHPoint center = getCoordinatesForIndex(i, j);
                    // TODO: Consider creating a new method in Polygon that does the 5 checks - p.partOfTile?
                    if (polygon.contains(center) ||
                            polygon.contains(center.getLat() - checkDiff, center.getLon() - checkDiff) ||
                            polygon.contains(center.getLat() - checkDiff, center.getLon() + checkDiff) ||
                            polygon.contains(center.getLat() + checkDiff, center.getLon() - checkDiff) ||
                            polygon.contains(center.getLat() + checkDiff, center.getLon() + checkDiff)) {

                        if (i >= lookupArray.length)
                            throw new IllegalArgumentException("longitudes have incorrect boundaries " + polygon.getMinLon() + " -> " + polygon.getMaxLon());

                        if (j >= lookupArray[i].length)
                            throw new IllegalArgumentException("latitudes have incorrect boundaries " + polygon.getMinLat() + " -> " + polygon.getMaxLat());

                        if (lookupArray[i][j] == EMPTY_RULE_INDEX) {
                            lookupArray[i][j] = (byte) ruleIndex;
                        } else {
                            // Merge Rules
                            SpatialRuleContainer curContainer = getContainerForIndex(i, j);
                            SpatialRuleContainer newContainer = curContainer.copy().addRule(rule);
                            int newIndex = this.rules.indexOf(newContainer);
                            if (newIndex < 0) {
                                this.rules.add(newContainer);
                                newIndex = rules.size() - 1;
                            }
                            if (newIndex > 255) {
                                throw new IllegalStateException("Cannot fit more than 255 rules");
                            }

                            lookupArray[i][j] = (byte) newIndex;
                        }
                    }
                }
            }
        }
    }

    private int castByteToInt(byte b) {
        return b & 0xFF;
    }


    private SpatialRuleContainer getContainerForIndex(int x, int y) {
        return this.rules.get(getRuleIndex(x, y));
    }

    private GHPoint getCoordinatesForIndex(int x, int y) {
        double lon = bounds.minLon + x * resolution + resolution / 2;
        double lat = bounds.minLat + y * resolution + resolution / 2;
        return new GHPoint(lat, lon);
    }

    public void visualize(int stepSize) {
        for (int i = 0; i < lookupArray.length; i += stepSize) {
            for (int j = 0; j < lookupArray[0].length; j += stepSize) {
                System.out.print(lookupArray[i][j]);
            }
            System.out.println();
        }
    }

}
