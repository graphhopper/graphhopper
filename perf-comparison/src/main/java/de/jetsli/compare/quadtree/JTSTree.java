/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package de.jetsli.compare.quadtree;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.util.GeometricShapeFactory;

/**
 * Java Topology suite implementaion which is also used in OpenTripPlanner
 *
 * @author Peter Karich
 */
class JTSTree implements SimplisticQuadTree {

    Integer integ = new Integer(1);
    com.vividsolutions.jts.index.quadtree.Quadtree qt = new com.vividsolutions.jts.index.quadtree.Quadtree();

    public JTSTree() {
    }

    public void init(int size) {
    }

    public void put(double lat, double lon) {
        qt.insert(new Envelope(new Coordinate(lat, lon)), integ);
    }

    public int countNodes(double lat, double lon, double radiusInKm) {
        return qt.query(createCircle(lat, lon, radiusInKm).getEnvelopeInternal()).size();
    }

    @Override
    public String toString() {
        return "JTS";
    }

    public int size() {
        return qt.size();
    }

    private static Geometry createCircle(double x, double y, double radiusInWhat) {
        GeometricShapeFactory shapeFactory = new GeometricShapeFactory();
        shapeFactory.setNumPoints(32);
        shapeFactory.setCentre(new Coordinate(x, y));
        shapeFactory.setSize(radiusInWhat * 2);
        return shapeFactory.createCircle();
    }

    public long getEmptyEntries(boolean b) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
