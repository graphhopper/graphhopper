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
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * @author Robin Boldt
 */
public class LevensteinDistanceEdgeFilter extends NameSimilarityEdgeFilter{

    public LevensteinDistanceEdgeFilter(FlagEncoder encoder, String soughtName) {
        super(encoder, soughtName);
    }

    @Override
    public final boolean accept(EdgeIteratorState iter) {
        if(!super.accept(iter)){
            return false;
        }

        // Don't check if PointHint is empty anyway
        if(soughtName.isEmpty()){
            return true;
        }

        String name = iter.getName();
        name = removeRelation(name);
        name = prepareName(name);

        int perfectDistance = Math.abs(soughtName.length()-name.length());
        int levDistance = StringUtils.getLevenshteinDistance(soughtName, name);
        // TODO: Maybe we should match 5% diff as good or something like that?
        return levDistance <= perfectDistance;
    }
}
