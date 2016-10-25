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
package com.graphhopper.routing.util;

import com.graphhopper.util.EdgeIteratorState;

import java.util.regex.Pattern;

/**
 * Abstract Class that defines the basis for NameSimilarity matching using an EdgeFilter.
 *
 * @author Robin Boldt
 */
public abstract class NameSimilarityEdgeFilter implements EdgeFilter {
    private final DefaultEdgeFilter edgeFilter;
    protected final String soughtName;
    protected static final Pattern nonWordCharacter = Pattern.compile("[\\W\\d]");

    public NameSimilarityEdgeFilter(FlagEncoder encoder, String soughtName) {
        edgeFilter = new DefaultEdgeFilter(encoder);
        this.soughtName = prepareName(soughtName);
    }

    /**
     * Removes any characters in the String that we don't care about in the matching procedure
     */
    protected String prepareName(String name){
        if(name == null){
            name = "";
        }

        name = nonWordCharacter.matcher(name).replaceAll("");
        name = name.toLowerCase();

        return name;
    }

    protected String removeRelation(String edgeName){
        if(edgeName != null && edgeName.contains(", ")){
            edgeName = edgeName.substring(0, edgeName.lastIndexOf(','));
        }
        return edgeName;
    }

    @Override
    public boolean accept(EdgeIteratorState iter) {
        return edgeFilter.accept(iter);
    }
}
