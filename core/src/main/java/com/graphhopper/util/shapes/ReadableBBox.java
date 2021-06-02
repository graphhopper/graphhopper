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
package com.graphhopper.util.shapes;

import com.graphhopper.util.PointList;

public interface ReadableBBox {

    boolean isValid();
    
    boolean hasElevation();

    double getMinLon();

    double getMaxLon();

    double getMinLat();

    double getMaxLat();

    double getMinEle();
    
    double getMaxEle();

    boolean contains(ReadableBBox b);

    boolean contains(double lat, double lon);

    /**
     * This method calculates if this BBox intersects with the specified BBox
     */
    boolean intersects(ReadableBBox o);

    /**
     * This method calculates if this BBox intersects with the specified BBox
     */
    boolean intersects(double minLon, double maxLon, double minLat, double maxLat);

    boolean intersects(PointList pointList);

    /**
     * Calculates the intersecting BBox between this and the specified BBox
     *
     * @return the intersecting BBox or null if not intersecting
     */
    BBox calculateIntersection(ReadableBBox bBox);

}
