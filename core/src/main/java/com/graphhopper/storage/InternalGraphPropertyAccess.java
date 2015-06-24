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

/**
 *
 * @author Peter Karich
 */
interface InternalGraphPropertyAccess
{
    /**
     * This method reverses the flags at the specified place (edgePointer)
     */
    long reverseFlags( long edgePointer, long flags );

    /**
     * This method creates an accessor to one edge
     */
    BaseGraph.SingleEdge createSingleEdge( int edgeId, int nodeId );

    /**
     * This method returns the edgeId of the first edge in the linked list
     */
    int getEdgeRef( int nodeId );

    /**
     * This method sets the edgeId of the first edge in the insert-first linked list
     */
    void setEdgeRef( int nodeId, int edgeId );
}
