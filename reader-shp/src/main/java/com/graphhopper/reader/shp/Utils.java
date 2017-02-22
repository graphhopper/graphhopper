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
package com.graphhopper.reader.shp;

import com.graphhopper.util.PointList;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;

/**
 * @author Phil
 */
public class Utils {
    public static String toWKT(PointList list) {
        int n = list.size();
        GeometryFactory factory = new GeometryFactory();
        Coordinate[] coords = new Coordinate[n];
        for (int i = 0; i < coords.length; i++) {
            coords[i] = new Coordinate(list.getLon(i), list.getLat(i));
        }
        return factory.createLineString(coords).toText();
    }

    public static RuntimeException asUnchecked(Throwable e) {
        if (RuntimeException.class.isInstance(e)) {
            return (RuntimeException) e;
        }
        return new RuntimeException(e);
    }

}
