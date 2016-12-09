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
 *
 * The covered area is indexed as tiles, with every tile being "quadratic" having the same degree length on every side.
 *
 * @author Robin Boldt
 */
public class SpatialRuleLookupArray extends AbstractSpatialRuleLookup {

    // resolution in full decimal degrees
    private final double resolution;
    private final BBox bounds;

    private final byte[][] lookupArray;
    private final List<SpatialRule> rules = new ArrayList<>();

    /**
     * @param bounds the outer bounds for the Lookup
     * @param resolution of the array in decimal degrees, see: https://en.wikipedia.org/wiki/Decimal_degrees
     *                   The downside of using decimal degrees is that this is not fixed to a certain m range as
     *                   longitude close to the polar are very small. Start with resolution =.1
     */
    public SpatialRuleLookupArray(BBox bounds, double resolution) {
        this.bounds = bounds;
        this.resolution = resolution;

        this.lookupArray = new byte[getNumberOfXGrids()][getNumberOfYGrids()];
        // Byte array is initialized with 0, => at index 0 is the EMPTY_RULE
        rules.add(EMPTY_RULE);
    }

    private int getNumberOfYGrids(){
        return (int) Math.ceil(Math.abs(bounds.maxLat - bounds.minLat)/resolution);
    }

    private int getNumberOfXGrids(){
        return (int) Math.ceil(Math.abs(bounds.maxLon - bounds.minLon)/resolution);
    }

    @Override
    public SpatialRule lookupRule(double lat, double lon) {
        if(lon < bounds.minLon){
            return EMPTY_RULE;
        }
        if(lon > bounds.maxLon){
            return EMPTY_RULE;
        }
        if(lat < bounds.minLat){
            return EMPTY_RULE;
        }
        if(lat > bounds.maxLat){
            return EMPTY_RULE;
        }
        int xIndex = getXIndexForLon(lon);
        int yIndex = getYIndexForLat(lat);
        int ruleIndex = (int) lookupArray[xIndex][yIndex];
        return rules.get(ruleIndex);
    }

    @Override
    public SpatialRule lookupRule(GHPoint point) {
        return lookupRule(point.getLat(), point.getLon());
    }

    private int getXIndexForLon(double lon){
        return (int) Math.floor(Math.abs(lon-bounds.minLon)/resolution);
    }

    private int getYIndexForLat(double lat){
        return (int) Math.floor(Math.abs(lat-bounds.minLat)/resolution);
    }

    @Override
    public void addRule(SpatialRule rule, Polygon polygon) {
        rules.add(rule);
        int ruleIndex = rules.size()-1;

        if(ruleIndex > 255){
            throw new IllegalStateException("Cannot fit more than 255 rules");
        }

        // TODO could be done more efficiently by using the bounds of the polygon
        for (int i = 0; i < this.lookupArray.length; i++) {
            for (int j = 0; j < this.lookupArray[0].length; j++) {
                if(polygon.contains(getCoordinatesForIndex(i,j))){
                    lookupArray[i][j]= (byte) ruleIndex;
                }
            }
        }
    }

    private GHPoint getCoordinatesForIndex(int x, int y){
        double lon = bounds.minLon + x*resolution + resolution/2;
        double lat = bounds.minLat + y*resolution + resolution/2;
        return new GHPoint(lat, lon);
    }

}
