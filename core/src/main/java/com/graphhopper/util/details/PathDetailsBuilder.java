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
package com.graphhopper.util.details;

import com.graphhopper.core.util.details.PathDetail;
import com.graphhopper.util.EdgeIteratorState;

import java.util.List;
import java.util.Map;

/**
 * Calculate details for a path and keeps the AbstractPathDetailsBuilder corresponding to this detail.
 * Every PathDetailsBuilder is responsible for a set of values, for example the speed.
 * On request it can provide the current value as well as a check if the value is different to the last.
 *
 * @author Robin Boldt
 */
public interface PathDetailsBuilder {

    boolean isEdgeDifferentToLastEdge(EdgeIteratorState edge);

    Map.Entry<String, List<PathDetail>> build();

    void startInterval(int firstIndex);

    void endInterval(int lastIndex);

    String getName();

}
