/*
 *  Licensed to Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  Peter Karich licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

/**
 * @author Peter Karich
 */
public interface PointAccess
{
    /**
     * @return true if elevation data is stored and can be retrieved
     */
    boolean is3D();

    /**
     * @return 3 if elevation enabled. 2 otherwise
     */
    int getDimension();

    /**
     * This method ensures that the node with the specified index exists and prepares access to it.
     * The index goes from 0 (inclusive) to graph.getNodes() (exclusive)
     * <p>
     * This methods sets the latitude, longitude and elevation to the specified value.
     */
    void setNode( int nodeId, double lat, double lon );

    /**
     * This method ensures that the node with the specified index exists and prepares access to it.
     * The index goes from 0 (inclusive) to graph.getNodes() (exclusive)
     * <p>
     * This methods sets the latitude, longitude and elevation to the specified value.
     */
    void setNode( int nodeId, double lat, double lon, double ele );

    /**
     * @return the latitude at the specified node index
     */
    double getLatitude( int nodeId );

    double getLat( int nodeId );

    /**
     * @return the longitude at the specified node index
     */
    double getLongitude( int nodeId );

    double getLon( int nodeId );

    /**
     * Returns the elevation of the specified nodeId.
     */
    double getElevation( int nodeId );

    double getEle( int nodeId );
}
