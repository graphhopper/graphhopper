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

import org.locationtech.jts.algorithm.Area;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import vector_tile.VectorTile;
import vector_tile.VectorTile.Tile.GeomType;

import java.math.BigDecimal;
import java.util.*;

/**
 * This is a copy of https://github.com/ElectronicChartCentre/java-vector-tile/commit/15e2e9b127729a00c52ced3a11fd1e9a45b462b1
 * We use this copy because we want to avoid the non-standard no.ecc Maven repository
 */
public class VectorTileEncoder {

    private final Map<String, Layer> layers = new LinkedHashMap<String, Layer>();

    private final int extent;
    
    private final double minimumLength;
    
    private final double minimumArea;

    protected final Geometry clipGeometry;
    
    protected final Envelope clipEnvelope;
    
    protected final PreparedGeometry clipGeometryPrepared;

    private final boolean autoScale;

    private long autoincrement;

    private final boolean autoincrementIds;
    
    private final double simplificationDistanceTolerance;
    
    private final GeometryFactory gf = new GeometryFactory();

    /**
     * Create a {@link VectorTileEncoder} with the default extent of 4096 and
     * clip buffer of 8.
     */
    public VectorTileEncoder() {
        this(4096, 8, true);
    }

    /**
     * Create a {@link VectorTileEncoder} with the given extent and a clip
     * buffer of 8.
     * 
     * @param extent a int to specify vector tile extent. 4096 is a good value.
     */
    public VectorTileEncoder(int extent) {
        this(extent, 8, true);
    }

    public VectorTileEncoder(int extent, int clipBuffer, boolean autoScale) {
        this(extent, clipBuffer, autoScale, false);
    }
    
    public VectorTileEncoder(int extent, int clipBuffer, boolean autoScale, boolean autoincrementIds) {
        this(extent, clipBuffer, autoScale, autoincrementIds, -1.0);
    }

    /**
     * Create a {@link VectorTileEncoder} with the given extent value.
     * <p>
     * The extent value control how detailed the coordinates are encoded in the
     * vector tile. 4096 is a good default, 256 can be used to reduce density.
     * <p>
     * The clip buffer value control how large the clipping area is outside of the
     * tile for geometries. 0 means that the clipping is done at the tile border. 8
     * is a good default.
     *
     * @param extent
     *            a int with extent value. 4096 is a good value.
     * @param clipBuffer
     *            a int with clip buffer size for geometries. 8 is a good value.
     * @param autoScale
     *            when true, the encoder expects coordinates in the 0..255 range and
     *            will scale them automatically to the 0..extent-1 range before
     *            encoding. when false, the encoder expects coordinates in the
     *            0..extent-1 range.
     * @param autoincrementIds 
     *            when true the vector tile feature id is auto incremented when using 
     *            {@link #addFeature(String, Map, Geometry)}
     * @param simplificationDistanceTolerance
     *            a positive double representing the distance tolerance to be used
     *            for non-points before (optional) scaling and encoding. A value
     *            &lt;=0 will prevent simplifying geometry. 0.1 seems to be a good
     *            value when {@code autoScale} is turned on.
     */
    public VectorTileEncoder(int extent, int clipBuffer, boolean autoScale, boolean autoincrementIds, double simplificationDistanceTolerance) {
        this.extent = extent;
        this.autoScale = autoScale;
        this.minimumLength = autoScale ? (256.0 / extent) : 1.0;
        this.minimumArea = this.minimumLength * this.minimumLength;
        this.autoincrementIds = autoincrementIds;
        this.autoincrement = 1;
        this.simplificationDistanceTolerance = simplificationDistanceTolerance;

        final int size = autoScale ? 256 : extent;
        clipGeometry = createTileEnvelope(clipBuffer, size);
        clipEnvelope = clipGeometry.getEnvelopeInternal();
        clipGeometryPrepared = PreparedGeometryFactory.prepare(clipGeometry);
    }

    private static Geometry createTileEnvelope(int buffer, int size) {
        Coordinate[] coords = new Coordinate[5];
        coords[0] = new Coordinate(0 - buffer, size + buffer);
        coords[1] = new Coordinate(size + buffer, size + buffer);
        coords[2] = new Coordinate(size + buffer, 0 - buffer);
        coords[3] = new Coordinate(0 - buffer, 0 - buffer);
        coords[4] = coords[0];
        return new GeometryFactory().createPolygon(coords);
    }

    public void addFeature(String layerName, Map<String, ?> attributes, Geometry geometry) {
        this.addFeature(layerName, attributes, geometry, this.autoincrementIds ? this.autoincrement++ : -1);
    }
    
    /**
     * Add a feature with layer name (typically feature type name), some attributes
     * and a Geometry. The Geometry must be in "pixel" space 0,0 upper left and
     * 256,256 lower right.
     * <p>
     * For optimization, geometries will be clipped and simplified. Features with
     * geometries outside of the tile will be skipped.
     *
     * @param layerName a {@link String} with the vector tile layer name.
     * @param attributes a {@link Map} with the vector tile feature attributes.
     * @param geometry a {@link Geometry} for the vector tile feature.
     * @param id a long with the vector tile feature id field.
     */
    public void addFeature(String layerName, Map<String, ?> attributes, Geometry geometry, long id) {

        // skip small Polygon/LineString.
        if (geometry instanceof MultiPolygon && geometry.getArea() < minimumArea) {
            return;
        }
        if (geometry instanceof Polygon && geometry.getArea() < minimumArea) {
            return;
        }
        if (geometry instanceof LineString && geometry.getLength() < minimumLength) {
            return;
        }

        // special handling of GeometryCollection. subclasses are not handled here.
        if (geometry.getClass().equals(GeometryCollection.class)) {
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                Geometry subGeometry = geometry.getGeometryN(i);
                // keeping the id. any better suggestion?
                addFeature(layerName, attributes, subGeometry, id);
            }
            return;
        }
        
        // About to simplify and clip. Looks like simplification before clipping is
        // faster than clipping before simplification
        
        // simplify non-points
        if (simplificationDistanceTolerance > 0.0 && !(geometry instanceof Point)) {
            if (geometry instanceof LineString || geometry instanceof MultiLineString) {
                geometry = DouglasPeuckerSimplifier.simplify(geometry, simplificationDistanceTolerance);
            } else if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
                Geometry simplified = DouglasPeuckerSimplifier.simplify(geometry, simplificationDistanceTolerance);
                // extra check to prevent polygon converted to line
                if (simplified instanceof Polygon || simplified instanceof MultiPolygon) {
                    geometry = simplified;
                } else {
                    geometry = TopologyPreservingSimplifier.simplify(geometry, simplificationDistanceTolerance);
                }
            } else {
                geometry = TopologyPreservingSimplifier.simplify(geometry, simplificationDistanceTolerance);
            }
        }
        
        // clip geometry
        if (geometry instanceof Point) {
            if (!clipCovers(geometry)) {
                return;
            }
        } else {
            geometry = clipGeometry(geometry);
        }

        // no need to add empty geometry
        if (geometry == null || geometry.isEmpty()) {
            return;
        }

        Layer layer = layers.get(layerName);
        if (layer == null) {
            layer = new Layer();
            layers.put(layerName, layer);
        }

        Feature feature = new Feature();
        feature.geometry = geometry;
        feature.id = id;
        this.autoincrement = Math.max(this.autoincrement, id + 1);

        for (Map.Entry<String, ?> e : attributes.entrySet()) {
            // skip attribute without value
            if (e.getValue() == null) {
                continue;
            }
            feature.tags.add(layer.key(e.getKey()));
            feature.tags.add(layer.value(e.getValue()));
        }

        layer.features.add(feature);
    }

    /**
     * A short circuit clip to the tile extent (tile boundary + buffer) for
     * points to improve performance. This method can be overridden to change
     * clipping behavior. See also {@link #clipGeometry(Geometry)}.
     * 
     * @param geom a {@link Geometry} to check for "covers"
     * @return a boolean true when the current clip geometry covers the given geom.
     */
    protected boolean clipCovers(Geometry geom) {
        if (geom instanceof Point) {
            Point p = (Point) geom;
            return clipGeometry.getEnvelopeInternal().covers(p.getCoordinate());
        }
        return clipEnvelope.covers(geom.getEnvelopeInternal());
    }

    /**
     * Clip geometry according to buffer given at construct time. This method
     * can be overridden to change clipping behavior. See also
     * {@link #clipCovers(Geometry)}.
     *
     * @param geometry a {@link Geometry} to check for intersection with the current clip geometry
     * @return a boolean true when current clip geometry intersects with the given geometry.
     */
    protected Geometry clipGeometry(Geometry geometry) {
        try {
            if (clipEnvelope.contains(geometry.getEnvelopeInternal())) {
                return geometry;
            }
            
            Geometry original = geometry;
            geometry = clipGeometry.intersection(original);

            // some times a intersection is returned as an empty geometry.
            // going via wkt fixes the problem.
            if (geometry.isEmpty() && clipGeometryPrepared.intersects(original)) {
                Geometry originalViaWkt = new WKTReader().read(original.toText());
                geometry = clipGeometry.intersection(originalViaWkt);
            }

            return geometry;
        } catch (TopologyException e) {
            // could not intersect. original geometry will be used instead.
            return geometry;
        } catch (ParseException e1) {
            // could not encode/decode WKT. original geometry will be used
            // instead.
            return geometry;
        }
    }

    /**
     * @return a byte array with the vector tile
     */
    public byte[] encode() {
        
        VectorTile.Tile.Builder tile = VectorTile.Tile.newBuilder();

        for (Map.Entry<String, Layer> e : layers.entrySet()) {
            String layerName = e.getKey();
            Layer layer = e.getValue();

            VectorTile.Tile.Layer.Builder tileLayer = VectorTile.Tile.Layer.newBuilder();
            
            tileLayer.setVersion(2);
            tileLayer.setName(layerName);

            tileLayer.addAllKeys(layer.keys());

            for (Object value : layer.values()) {
                VectorTile.Tile.Value.Builder tileValue = VectorTile.Tile.Value.newBuilder();
                if (value instanceof String) {
                    tileValue.setStringValue((String) value);
                } else if (value instanceof Integer) {
                    tileValue.setSintValue(((Integer) value).intValue());
                } else if (value instanceof Long) {
                    tileValue.setSintValue(((Long) value).longValue());
                } else if (value instanceof Float) {
                    tileValue.setFloatValue(((Float) value).floatValue());
                } else if (value instanceof Double) {
                    tileValue.setDoubleValue(((Double) value).doubleValue());
                } else if (value instanceof BigDecimal) {
                    tileValue.setStringValue(value.toString());
                } else if (value instanceof Number) {
                    tileValue.setDoubleValue(((Number) value).doubleValue());
                } else if (value instanceof Boolean) {
                    tileValue.setBoolValue(((Boolean) value).booleanValue());
                } else {
                    tileValue.setStringValue(value.toString());
                }
                tileLayer.addValues(tileValue.build());
            }

            tileLayer.setExtent(extent);

            for (Feature feature : layer.features) {

                Geometry geometry = feature.geometry;

                VectorTile.Tile.Feature.Builder featureBuilder = VectorTile.Tile.Feature.newBuilder();

                featureBuilder.addAllTags(feature.tags);
                if (feature.id >= 0) {
                    featureBuilder.setId(feature.id);
                }
                
                GeomType geomType = toGeomType(geometry);
                x = 0;
                y = 0;
                List<Integer> commands = commands(geometry);

                // skip features with no geometry commands
                if (commands.isEmpty()) {
                    continue;
                }

                // Extra step to parse and check validity and try to repair. Probably expensive.
                if (simplificationDistanceTolerance > 0.0 && geomType == GeomType.POLYGON) {
                    double scale = autoScale ? (extent / 256.0) : 1.0;
                    Geometry decodedGeometry = VectorTileDecoder.decodeGeometry(gf, geomType, commands, scale);
                    if (!isValid(decodedGeometry)) {
                        // Invalid. Try more simplification and without preserving topology.
                        geometry = DouglasPeuckerSimplifier.simplify(geometry, simplificationDistanceTolerance * 2.0);
                        if (geometry.isEmpty()) {
                            continue;
                        }
                        geomType = toGeomType(geometry);
                        x = 0;
                        y = 0;
                        commands = commands(geometry);
                    }
                }
                
                featureBuilder.setType(geomType);
                featureBuilder.addAllGeometry(commands);

                tileLayer.addFeatures(featureBuilder.build());
            }

            tile.addLayers(tileLayer.build());

        }

        return tile.build().toByteArray();
    }

    private static final boolean isValid(Geometry geometry) {
        try {
            return geometry.isValid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static VectorTile.Tile.GeomType toGeomType(Geometry geometry) {
        if (geometry instanceof Point) {
            return VectorTile.Tile.GeomType.POINT;
        }
        if (geometry instanceof MultiPoint) {
            return VectorTile.Tile.GeomType.POINT;
        }
        if (geometry instanceof LineString) {
            return VectorTile.Tile.GeomType.LINESTRING;
        }
        if (geometry instanceof MultiLineString) {
            return VectorTile.Tile.GeomType.LINESTRING;
        }
        if (geometry instanceof Polygon) {
            return VectorTile.Tile.GeomType.POLYGON;
        }
        if (geometry instanceof MultiPolygon) {
            return VectorTile.Tile.GeomType.POLYGON;
        }
        return VectorTile.Tile.GeomType.UNKNOWN;
    }

    static boolean shouldClosePath(Geometry geometry) {
        return (geometry instanceof Polygon) || (geometry instanceof LinearRing);
    }

    List<Integer> commands(Geometry geometry) {
        
        if (geometry instanceof MultiLineString) {
            return commands((MultiLineString) geometry);
        }
        if (geometry instanceof Polygon) {
            return commands((Polygon) geometry);
        }
        if (geometry instanceof MultiPolygon) {
            return commands((MultiPolygon) geometry);
        }        
        
        return commands(geometry.getCoordinates(), shouldClosePath(geometry), geometry instanceof MultiPoint);
    }
    
    List<Integer> commands(MultiLineString mls) {
        List<Integer> commands = new ArrayList<Integer>();
        for (int i = 0; i < mls.getNumGeometries(); i++) {
            final List<Integer> geomCommands =
                    commands(mls.getGeometryN(i).getCoordinates(), false);
            if (geomCommands.size() > 3) {
                // if the geometry consists of all identical points (after Math.round()) commands
                // returns a single move_to command, which is not valid according to the vector tile
                // specifications.
                // (https://github.com/mapbox/vector-tile-spec/tree/master/2.1#4343-linestring-geometry-type)
                commands.addAll(geomCommands);
            }
        }
        return commands;
    }
    
    List<Integer> commands(MultiPolygon mp) {
        List<Integer> commands = new ArrayList<Integer>();
        for (int i = 0; i < mp.getNumGeometries(); i++) {
            Polygon polygon = (Polygon) mp.getGeometryN(i);
            commands.addAll(commands(polygon));
        }
        return commands;
    }
    
    List<Integer> commands(Polygon polygon) {
        List<Integer> commands = new ArrayList<Integer>();

        // According to the vector tile specification, the exterior ring of a polygon
        // must be in clockwise order, while the interior ring in counter-clockwise order.
        // In the tile coordinate system, Y axis is positive down.
        //
        // However, in geographic coordinate system, Y axis is positive up.
        // Therefore, we must reverse the coordinates.
        // So, the code below will make sure that exterior ring is in counter-clockwise order
        // and interior ring in clockwise order.
        LineString exteriorRing = polygon.getExteriorRing();
        if (Area.ofRingSigned(exteriorRing.getCoordinates()) > 0) {
            exteriorRing = exteriorRing.reverse();
        }
        commands.addAll(commands(exteriorRing.getCoordinates(), true));

        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            LineString interiorRing = polygon.getInteriorRingN(i);
            if (Area.ofRingSigned(interiorRing.getCoordinates()) < 0) {
                interiorRing = interiorRing.reverse();
            }
            commands.addAll(commands(interiorRing.getCoordinates(), true));
        }
        return commands;
    }

    private int x = 0;
    private int y = 0;

    /**
     * // // // Ex.: MoveTo(3, 6), LineTo(8, 12), LineTo(20, 34), ClosePath //
     * Encoded as: [ 9 3 6 18 5 6 12 22 15 ] // == command type 7 (ClosePath),
     * length 1 // ===== relative LineTo(+12, +22) == LineTo(20, 34) // ===
     * relative LineTo(+5, +6) == LineTo(8, 12) // == [00010 010] = command type
     * 2 (LineTo), length 2 // === relative MoveTo(+3, +6) // == [00001 001] =
     * command type 1 (MoveTo), length 1 // Commands are encoded as uint32
     * varints, vertex parameters are // encoded as sint32 varints (zigzag).
     * Vertex parameters are // also encoded as deltas to the previous position.
     * The original // position is (0,0)
     *
     * @param cs
     * @return
     */
    List<Integer> commands(Coordinate[] cs, boolean closePathAtEnd) {
        return commands(cs, closePathAtEnd, false);
    }

    List<Integer> commands(Coordinate[] cs, boolean closePathAtEnd, boolean multiPoint) {

        if (cs.length == 0) {
            return Collections.emptyList();
        }

        List<Integer> r = new ArrayList<Integer>();

        int lineToIndex = 0;
        int lineToLength = 0;

        double scale = autoScale ? (extent / 256.0) : 1.0;

        for (int i = 0; i < cs.length; i++) {
            Coordinate c = cs[i];

            if (i == 0) {
                r.add(commandAndLength(Command.MoveTo, multiPoint ? cs.length : 1));
            }

            int _x = (int) Math.round(c.x * scale);
            int _y = (int) Math.round(c.y * scale);

            // prevent point equal to the previous
            if (i > 0 && _x == x && _y == y) {
                lineToLength--;
                continue;
            }

            // prevent double closing
            if (closePathAtEnd && cs.length > 1 && i == (cs.length - 1) && cs[0].equals(c)) {
                lineToLength--;
                continue;
            }

            // delta, then zigzag
            r.add(zigZagEncode(_x - x));
            r.add(zigZagEncode(_y - y));

            x = _x;
            y = _y;

            if (i == 0 && cs.length > 1 && !multiPoint) {
                // can length be too long?
                lineToIndex = r.size();
                lineToLength = cs.length - 1;
                r.add(commandAndLength(Command.LineTo, lineToLength));
            }

        }

        // update LineTo length
        if (lineToIndex > 0) {
            if (lineToLength == 0) {
                // remove empty LineTo
                r.remove(lineToIndex);
            } else {
                // update LineTo with new length
                r.set(lineToIndex, commandAndLength(Command.LineTo, lineToLength));
            }
        }

        if (closePathAtEnd) {
            r.add(commandAndLength(Command.ClosePath, 1));
        }

        return r;
    }

    static int commandAndLength(int command, int repeat) {
        return repeat << 3 | command;
    }

    static int zigZagEncode(int n) {
        // https://developers.google.com/protocol-buffers/docs/encoding#types
        return (n << 1) ^ (n >> 31);
    }

    private static final class Layer {

        final List<Feature> features = new ArrayList<VectorTileEncoder.Feature>();

        private final Map<String, Integer> keys = new LinkedHashMap<String, Integer>();
        private final Map<Object, Integer> values = new LinkedHashMap<Object, Integer>();

        public Integer key(String key) {
            Integer i = keys.get(key);
            if (i == null) {
                i = Integer.valueOf(keys.size());
                keys.put(key, i);
            }
            return i;
        }

        public List<String> keys() {
            return new ArrayList<String>(keys.keySet());
        }

        public Integer value(Object value) {
            Integer i = values.get(value);
            if (i == null) {
                i = Integer.valueOf(values.size());
                values.put(value, i);
            }
            return i;
        }

        public List<Object> values() {
            return Collections.unmodifiableList(new ArrayList<Object>(values.keySet()));
        }
    }

    private static final class Feature {
        long id;
        Geometry geometry;
        final List<Integer> tags = new ArrayList<Integer>();

    }
}
