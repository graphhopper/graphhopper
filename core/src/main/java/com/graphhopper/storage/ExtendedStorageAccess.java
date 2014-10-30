/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
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

public interface ExtendedStorageAccess
{
    /**
     * Write to the node's extended storage
     * @param storageName the storage identifier to which you want to write
     * @param nodeId the node
     * @param value the value to write
     */
    void writeToExtendedNodeStorage( String storageName, int nodeId, int value );

    /**
     * Reads previously written values from the node's extended storage
     */
    int readFromExtendedNodeStorage( String storageName, int nodeId );

    /**
     * Writes to the edge's extended storage. You may also use the EdgeIteratorState's setAdditionalField
     * method for conveniece, so you don't have to provide an edge ID
     */
    void writeToExtendedEdgeStorage( String storageName, int edgeId, int value );

    /**
     * Reads previously written values from the edge's extended storage
     */
    int readFromExtendedEdgeStorage( String storageName, int edgeId );
}
