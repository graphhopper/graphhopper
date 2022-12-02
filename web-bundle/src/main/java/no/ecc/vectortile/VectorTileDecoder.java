/*****************************************************************
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 ****************************************************************/
package no.ecc.vectortile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.locationtech.jts.algorithm.Area;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import vector_tile.VectorTile;
import vector_tile.VectorTile.Tile.GeomType;
import vector_tile.VectorTile.Tile.Layer;

public class VectorTileDecoder {

    private boolean autoScale = true;

    /**
     * Get the autoScale setting.
     *
     * @return autoScale
     */
    public boolean isAutoScale() {
            return autoScale;
    }

    /**
     * Set the autoScale setting.
     *
     * @param autoScale
     *            when true, the encoder automatically scale and return all coordinates in the 0..255 range.
     *            when false, the encoder returns all coordinates in the 0..extent-1 range as they are encoded.
     *
     */
    public void setAutoScale(boolean autoScale) {
        this.autoScale = autoScale;
    }

    public FeatureIterable decode(byte[] data) throws IOException {
        return decode(data, Filter.ALL);
    }

    public FeatureIterable decode(byte[] data, String layerName) throws IOException {
        return decode(data, new Filter.Single(layerName));
    }

    public FeatureIterable decode(byte[] data, Set<String> layerNames) throws IOException {
        return decode(data, new Filter.Any(layerNames));
    }

    public FeatureIterable decode(byte[] data, Filter filter) throws IOException {
        VectorTile.Tile tile = VectorTile.Tile.parseFrom(data);
        return new FeatureIterable(tile, filter, autoScale);
    }

    static int zigZagDecode(int n) {
        return ((n >> 1) ^ (-(n & 1)));
    }
    
    static Geometry decodeGeometry(GeometryFactory gf, GeomType geomType, List<Integer> commands, double scale) {
        int x = 0;
        int y = 0;

        List<List<Coordinate>> coordsList = new ArrayList<List<Coordinate>>();
        List<Coordinate> coords = null;

        int geometryCount = commands.size();
        int length = 0;
        int command = 0;
        int i = 0;
        while (i < geometryCount) {

            if (length <= 0) {
                length = commands.get(i++).intValue();
                command = length & ((1 << 3) - 1);
                length = length >> 3;
            }

            if (length > 0) {

                if (command == Command.MoveTo) {
                    coords = new ArrayList<Coordinate>();
                    coordsList.add(coords);
                }

                if (command == Command.ClosePath) {
                    if (geomType != VectorTile.Tile.GeomType.POINT && !coords.isEmpty()) {
                        coords.add(new Coordinate(coords.get(0)));
                    }
                    length--;
                    continue;
                }

                int dx = commands.get(i++).intValue();
                int dy = commands.get(i++).intValue();

                length--;

                dx = zigZagDecode(dx);
                dy = zigZagDecode(dy);

                x = x + dx;
                y = y + dy;

                Coordinate coord = new Coordinate(x / scale, y / scale);
                coords.add(coord);
            }

        }

        Geometry geometry = null;

        switch (geomType) {
        case LINESTRING:
            List<LineString> lineStrings = new ArrayList<LineString>();
            for (List<Coordinate> cs : coordsList) {
                if (cs.size() <= 1) {
                    continue;
                }
                lineStrings.add(gf.createLineString(cs.toArray(new Coordinate[cs.size()])));
            }
            if (lineStrings.size() == 1) {
                geometry = lineStrings.get(0);
            } else if (lineStrings.size() > 1) {
                geometry = gf.createMultiLineString(lineStrings.toArray(new LineString[lineStrings.size()]));
            }
            break;
        case POINT:
            List<Coordinate> allCoords = new ArrayList<Coordinate>();
            for (List<Coordinate> cs : coordsList) {
                allCoords.addAll(cs);
            }
            if (allCoords.size() == 1) {
                geometry = gf.createPoint(allCoords.get(0));
            } else if (allCoords.size() > 1) {
                geometry = gf.createMultiPointFromCoords(allCoords.toArray(new Coordinate[allCoords.size()]));
            }
            break;
        case POLYGON:
            List<List<LinearRing>> polygonRings = new ArrayList<List<LinearRing>>();
            List<LinearRing> ringsForCurrentPolygon = null;
            Boolean ccw = null;
            for (List<Coordinate> cs : coordsList) {
                Coordinate[] ringCoords = cs.toArray(new Coordinate[cs.size()]);
                double area = Area.ofRingSigned(ringCoords);
                if (area == 0) {
                    continue;
                }
                boolean thisCcw = area < 0;
                if (ccw == null) {
                    ccw = thisCcw;
                }
                LinearRing ring = gf.createLinearRing(ringCoords);
                if (ccw == thisCcw) {
                    if (ringsForCurrentPolygon != null) {
                        polygonRings.add(ringsForCurrentPolygon);
                    }
                    ringsForCurrentPolygon = new ArrayList<>();
                }
                ringsForCurrentPolygon.add(ring);
            }
            if (ringsForCurrentPolygon != null) {
                polygonRings.add(ringsForCurrentPolygon);
            }

            List<Polygon> polygons = new ArrayList<Polygon>();
            for (List<LinearRing> rings : polygonRings) {
                LinearRing shell = rings.get(0);
                LinearRing[] holes = rings.subList(1, rings.size()).toArray(new LinearRing[rings.size() - 1]);
                polygons.add(gf.createPolygon(shell, holes));
            }
            if (polygons.size() == 1) {
                geometry = polygons.get(0);
            }
            if (polygons.size() > 1) {
                geometry = gf.createMultiPolygon(GeometryFactory.toPolygonArray(polygons));
            }
            break;
        case UNKNOWN:
            break;
        default:
            break;
        }

        if (geometry == null) {
            geometry = gf.createGeometryCollection(new Geometry[0]);
        }

        return geometry;
    }

    public static final class FeatureIterable implements Iterable<Feature> {

        private final VectorTile.Tile tile;
        private final Filter filter;
        private boolean autoScale;

        public FeatureIterable(VectorTile.Tile tile, Filter filter, boolean autoScale) {
            this.tile = tile;
            this.filter = filter;
            this.autoScale = autoScale;
        }

        public Iterator<Feature> iterator() {
            return new FeatureIterator(tile, filter, autoScale);
        }

        public List<Feature> asList() {
            List<Feature> features = new ArrayList<VectorTileDecoder.Feature>();
            for (Feature feature : this) {
                features.add(feature);
            }
            return features;
        }

        public Collection<String> getLayerNames() {
            Set<String> layerNames = new HashSet<String>();
            for (VectorTile.Tile.Layer layer : tile.getLayersList()) {
                layerNames.add(layer.getName());
            }
            return Collections.unmodifiableSet(layerNames);
        }

    }

    private static final class FeatureIterator implements Iterator<Feature> {

        private final GeometryFactory gf = new GeometryFactory();

        private final Filter filter;

        private final Iterator<VectorTile.Tile.Layer> layerIterator;

        private Iterator<VectorTile.Tile.Feature> featureIterator;

        private int extent;
        private String layerName;
        private double scale;
        private boolean autoScale;

        private final List<String> keys = new ArrayList<String>();
        private final List<Object> values = new ArrayList<Object>();

        private Feature next;

        public FeatureIterator(VectorTile.Tile tile, Filter filter, boolean autoScale) {
            layerIterator = tile.getLayersList().iterator();
            this.filter = filter;
            this.autoScale = autoScale;
        }

        public boolean hasNext() {
            findNext();
            return next != null;
        }

        public Feature next() {
            findNext();
            if (next == null) {
                throw new NoSuchElementException();
            }
            Feature n = next;
            next = null;
            return n;
        }

        private void findNext() {

            if (next != null) {
                return;
            }

            while (true) {

                if (featureIterator == null || !featureIterator.hasNext()) {
                    if (!layerIterator.hasNext()) {
                        next = null;
                        break;
                    }

                    Layer layer = layerIterator.next();
                    if (!filter.include(layer.getName())) {
                        continue;
                    }

                    parseLayer(layer);
                    continue;
                }

                next = parseFeature(featureIterator.next());
                break;

            }

        }

        private void parseLayer(VectorTile.Tile.Layer layer) {

            layerName = layer.getName();
            extent = layer.getExtent();
            scale = autoScale ? extent / 256.0 : 1.0;

            keys.clear();
            keys.addAll(layer.getKeysList());
            values.clear();

            for (VectorTile.Tile.Value value : layer.getValuesList()) {
                if (value.hasBoolValue()) {
                    values.add(value.getBoolValue());
                } else if (value.hasDoubleValue()) {
                    values.add(value.getDoubleValue());
                } else if (value.hasFloatValue()) {
                    values.add(value.getFloatValue());
                } else if (value.hasIntValue()) {
                    values.add(value.getIntValue());
                } else if (value.hasSintValue()) {
                    values.add(value.getSintValue());
                } else if (value.hasUintValue()) {
                    values.add(value.getUintValue());
                } else if (value.hasStringValue()) {
                    values.add(value.getStringValue());
                } else {
                    values.add(null);
                }

            }

            featureIterator = layer.getFeaturesList().iterator();
        }

        private Feature parseFeature(VectorTile.Tile.Feature feature) {

            int tagsCount = feature.getTagsCount();
            Map<String, Object> attributes = new HashMap<String, Object>(tagsCount / 2);
            int tagIdx = 0;
            while (tagIdx < feature.getTagsCount()) {
                String key = keys.get(feature.getTags(tagIdx++));
                Object value = values.get(feature.getTags(tagIdx++));
                attributes.put(key, value);
            }

            Geometry geometry = decodeGeometry(gf, feature.getType(), feature.getGeometryList(), scale);
            if (geometry == null) {
                geometry = gf.createGeometryCollection(new Geometry[0]);
            }

            return new Feature(layerName, extent, geometry, Collections.unmodifiableMap(attributes), feature.getId());
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    public static final class Feature {

        private final String layerName;
        private final int extent;
        private final long id;
        private final Geometry geometry;
        private final Map<String, Object> attributes;

        public Feature(String layerName, int extent, Geometry geometry, Map<String, Object> attributes, long id) {
            this.layerName = layerName;
            this.extent = extent;
            this.geometry = geometry;
            this.attributes = attributes;
            this.id = id;
        }

        public String getLayerName() {
            return layerName;
        }

        public long getId() {
            return id;
        }

        public int getExtent() {
            return extent;
        }

        public Geometry getGeometry() {
            return geometry;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

    }

}
