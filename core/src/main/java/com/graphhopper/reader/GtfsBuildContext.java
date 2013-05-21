/*
 * Copyright 2013 Thomas Buerli <tbuerli@student.ethz.ch>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.reader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
class GtfsBuildContext {

    public HashSet<AgencyAndId> stops = new HashSet<AgencyAndId>();
    public Map<Stop, TransitStop> stopNodes = new HashMap<Stop, TransitStop>();
    
    private static int nodeId = 0;
    
    public static int getNewNodeId() {
        return nodeId++;
    }
    
    public void close() {
        nodeId = 0;
    }
}
