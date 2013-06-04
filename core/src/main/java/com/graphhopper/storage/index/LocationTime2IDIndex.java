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
package com.graphhopper.storage.index;

/**
 *
 * @author Thomas Buerli <tbuerli@student.ethz.ch>
 */
public interface LocationTime2IDIndex extends Location2IDIndex{
        
    /**
     * Finds the node id for a given location and time
     * @param lat
     * @param lon
     * @param time in seconds since midnight
     * @return the node id
     */
     public int findID(double lat, double lon, int time);
     
     /**
      * Finds the exit node of a station for given location. Exit node is used as get off the station
      * @param lat
      * @param lon
      * @return the node id
      */
     public int findExitNode(double lat, double lon);
     
     /**
      * Get the represented time of the transit nod.
      * @param nodeId Id of the transit node
      * @return time in seconds after midnight
      */
     public int getTime(int nodeId);
    
     
}
