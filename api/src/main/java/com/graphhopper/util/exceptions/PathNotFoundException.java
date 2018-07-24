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
package com.graphhopper.util.exceptions;

import java.util.Map;

/**
 * General exception if a path cannot be found. Contains different subclasses to further differentiate different cases
 * and to make it easier to communicate the reason of this exception to the client.
 *
 * @author Robin Boldt
 */
public class PathNotFoundException extends DetailedIllegalArgumentException {
    public PathNotFoundException(String var1, Map<String, Object> details) {
        super(var1, details);
    }

    /**
     * If a path cannot be found due to disconnected graphs.
     */
    public static final class ConnectionNotFoundException extends PathNotFoundException {
        public ConnectionNotFoundException(String var1, Map<String, Object> details) {
            super(var1, details);
        }

    }

    /**
     * If a path cannot be found because the maximum nodes have been exceeded
     */
    public static final class MaximumNodesExceededException extends PathNotFoundException {
        public MaximumNodesExceededException(String var1, Map<String, Object> details) {
            super(var1, details);
        }
    }

    /**
     * If a path cannot be found found because the waypoints are in different subnetworks.
     * Currently, this is only relevant for LM.
     */
    public static final class DifferentSubnetworksException extends PathNotFoundException {
        public DifferentSubnetworksException(String var1, Map<String, Object> details) {
            super(var1, details);
        }
    }
}
