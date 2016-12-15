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

/**
 * A shape interface to implement circles or rectangles.
 * <p>
 *
 * @author Peter Karich
 */
public interface Shape {
    /**
     * @return true if edges or areas of this and the specified shapes overlap
     */
    boolean intersect(Shape o);

    /**
     * @return true only if lat and lon are inside (or on the edge) of this shape
     */
    boolean contains(double lat, double lon);

    /**
     * @return true if the specified shape is fully contained in this shape. Only iff
     * <pre> s1.contains(s2) &amp;&amp; s2.contains(s1) </pre> then s1 is equal to s2
     */
    boolean contains(Shape s);

    /**
     * @return the minimal rectangular bounding box of this shape
     */
    BBox getBounds();

    /**
     * @return The center of the shape, if applicable
     */
    GHPoint getCenter();

    /**
     * @return an estimated area in m^2
     */
    double calculateArea();
}
