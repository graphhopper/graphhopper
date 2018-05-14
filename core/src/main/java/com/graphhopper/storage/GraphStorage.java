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
package com.graphhopper.storage;

import com.graphhopper.routing.util.EncodingManager;

public interface GraphStorage extends Storable<GraphStorage> {
    Directory getDirectory();

    EncodingManager getEncodingManager();

    void setSegmentSize(int bytes);

    String toDetailsString();

    StorableProperties getProperties();

    /**
     * Schedule the deletion of the specified node until an optimize() call happens
     */
    void markNodeRemoved(int index);

    /**
     * Checks if the specified node is marked as removed.
     */
    boolean isNodeRemoved(int index);

    /**
     * Performs optimization routines like deletion or node rearrangements.
     */
    void optimize();
}
