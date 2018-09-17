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

package com.conveyal.gtfs.error;

public enum Priority {
    /** 
     * Something that is likely to break routing results,
     * e.g. stop times out of sequence or high-speed travel
     */
    HIGH,
    
    /** 
     * Something that is likely to break display, but still give accurate routing results,
     * e.g. broken shapes or route long name containing route short name.
     */
    MEDIUM,
    
    /**
     * Something that will not affect user experience but should be corrected as time permits,
     * e.g. unused stops.
     */
    LOW,
    
    /**
     * An error for which we do not have a priority
     */
    UNKNOWN
}
