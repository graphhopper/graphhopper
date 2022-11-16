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
package com.onthegomap.planetiler;

import com.carrotsearch.hppc.IntArrayList;
import com.onthegomap.planetiler.geo.GeometryType;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vector_tile.VectorTileProto;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Encodes a single output tile containing JTS {@link Geometry} features into the compact binary Mapbox Vector Tile
 * format.
 * <p>
 * This class is copied from <a href=
 * "https://github.com/ElectronicChartCentre/java-vector-tile/blob/master/src/main/java/no/ecc/vectortile/VectorTileEncoder.java">VectorTileEncoder.java</a>
 * and <a href=
 * "https://github.com/ElectronicChartCentre/java-vector-tile/blob/master/src/main/java/no/ecc/vectortile/VectorTileDecoder.java">VectorTileDecoder.java</a>
 * and modified to decouple geometry encoding from vector tile encoding so that encoded commands can be stored in the
 * sorted feature map prior to encoding vector tiles. The internals are also refactored to improve performance by using
 * hppc primitive collections.
 *
 * @see <a href="https://github.com/mapbox/vector-tile-spec/tree/master/2.1">Mapbox Vector Tile Specification</a>
 */
public class VectorTile {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorTile.class);

    // TODO make these configurable
    private static final int EXTENT = 4096;
    private static final double SIZE = 256d;
    private final Map<String, Layer> layers = new LinkedHashMap<>();

    private static int[] getCommands(Geometry input, int scale) {
        CommandEncoder encoder = new CommandEncoder(scale);
        encoder.accept(input);
        return encoder.result.toArray();
    }

    /**
     * Scales a geometry down by a factor of {@code 2^scale} without materializing an intermediate JTS geometry and
     * returns the encoded result.
     */
    private static int[] unscale(int[] commands, int scale, GeometryType geomType) {
        IntArrayList result = new IntArrayList();
        int geometryCount = commands.length;
        int length = 0;
        int command = 0;
        int i = 0;
        int inX = 0, inY = 0;
        int outX = 0, outY = 0;
        int startX = 0, startY = 0;
        double scaleFactor = Math.pow(2, -scale);
        int lengthIdx = 0;
        int moveToIdx = 0;
        int pointsInShape = 0;
        boolean first = true;
        while (i < geometryCount) {
            if (length <= 0) {
                length = commands[i++];
                lengthIdx = result.size();
                result.add(length);
                command = length & ((1 << 3) - 1);
                length = length >> 3;
            }

            if (length > 0) {
                if (command == Command.MOVE_TO.value) {
                    // degenerate geometry, remove it from output entirely
                    if (!first && pointsInShape < geomType.minPoints()) {
                        int prevCommand = result.get(lengthIdx);
                        result.elementsCount = moveToIdx;
                        result.add(prevCommand);
                        // reset deltas
                        outX = startX;
                        outY = startY;
                    }
                    // keep track of size of next shape...
                    pointsInShape = 0;
                    startX = outX;
                    startY = outY;
                    moveToIdx = result.size() - 1;
                }
                first = false;
                if (command == Command.CLOSE_PATH.value) {
                    pointsInShape++;
                    length--;
                    continue;
                }

                int dx = commands[i++];
                int dy = commands[i++];

                length--;

                dx = zigZagDecode(dx);
                dy = zigZagDecode(dy);

                inX = inX + dx;
                inY = inY + dy;

                int nextX = (int) Math.round(inX * scaleFactor);
                int nextY = (int) Math.round(inY * scaleFactor);

                if (nextX == outX && nextY == outY && command == Command.LINE_TO.value) {
                    int commandLength = result.get(lengthIdx) - 8;
                    if (commandLength < 8) {
                        // get rid of lineto section if empty
                        result.elementsCount = lengthIdx;
                    } else {
                        result.set(lengthIdx, commandLength);
                    }
                } else {
                    pointsInShape++;
                    int dxOut = nextX - outX;
                    int dyOut = nextY - outY;
                    result.add(
                            zigZagEncode(dxOut),
                            zigZagEncode(dyOut)
                    );
                    outX = nextX;
                    outY = nextY;
                }
            }
        }
        // degenerate geometry, remove it from output entirely
        if (pointsInShape < geomType.minPoints()) {
            result.elementsCount = moveToIdx;
        }
        return result.toArray();
    }

    private static int zigZagEncode(int n) {
        // https://developers.google.com/protocol-buffers/docs/encoding#types
        return (n << 1) ^ (n >> 31);
    }

    private static int zigZagDecode(int n) {
        // https://developers.google.com/protocol-buffers/docs/encoding#types
        return ((n >> 1) ^ (-(n & 1)));
    }

    /**
     * Encodes a JTS geometry according to
     * <a href="https://github.com/mapbox/vector-tile-spec/tree/master/2.1#43-geometry-encoding">Geometry Encoding
     * Specification</a>.
     *
     * @param geometry the JTS geometry to encoded
     * @return the geometry type and command array for the encoded geometry
     */
    public static VectorGeometry encodeGeometry(Geometry geometry) {
        return encodeGeometry(geometry, 0);
    }

    public static VectorGeometry encodeGeometry(Geometry geometry, int scale) {
        return new VectorGeometry(getCommands(geometry, scale), GeometryType.typeOf(geometry), scale);
    }

    /**
     * Adds features in a layer to this tile.
     *
     * @param layerName name of the layer in this tile to add the features to
     * @param features  features to add to the tile
     * @return this encoder for chaining
     */
    public VectorTile addLayerFeatures(String layerName, List<? extends Feature> features) {
        if (features.isEmpty()) {
            return this;
        }

        Layer layer = layers.get(layerName);
        if (layer == null) {
            layer = new Layer();
            layers.put(layerName, layer);
        }

        for (Feature inFeature : features) {
            if (inFeature != null && inFeature.geometry.commands.length > 0) {
                EncodedFeature outFeature = new EncodedFeature(inFeature);

                for (Map.Entry<String, ?> e : inFeature.attrs.entrySet()) {
                    // skip attribute without value
                    if (e.getValue() != null) {
                        outFeature.tags.add(layer.key(e.getKey()));
                        outFeature.tags.add(layer.value(e.getValue()));
                    }
                }

                layer.encodedFeatures.add(outFeature);
            }
        }
        return this;
    }

    /**
     * Creates a vector tile protobuf with all features in this tile and serializes it as a byte array.
     * <p>
     * Does not compress the result.
     */
    public byte[] encode() {
        VectorTileProto.Tile.Builder tile = VectorTileProto.Tile.newBuilder();
        for (Map.Entry<String, Layer> e : layers.entrySet()) {
            String layerName = e.getKey();
            Layer layer = e.getValue();

            VectorTileProto.Tile.Layer.Builder tileLayer = VectorTileProto.Tile.Layer.newBuilder()
                    .setVersion(2)
                    .setName(layerName)
                    .setExtent(EXTENT)
                    .addAllKeys(layer.keys());

            for (Object value : layer.values()) {
                VectorTileProto.Tile.Value.Builder tileValue = VectorTileProto.Tile.Value.newBuilder();
                if (value instanceof String) {
                    tileValue.setStringValue((String) value);
                } else if (value instanceof Integer) {
                    tileValue.setSintValue((Integer) value);
                } else if (value instanceof Long) {
                    tileValue.setSintValue((Long) value);
                } else if (value instanceof Float) {
                    tileValue.setFloatValue((Float) value);
                } else if (value instanceof Double) {
                    tileValue.setDoubleValue((Double) value);
                } else if (value instanceof Boolean) {
                    tileValue.setBoolValue((Boolean) value);
                } else {
                    tileValue.setStringValue(value.toString());
                }
                tileLayer.addValues(tileValue.build());
            }

            for (EncodedFeature feature : layer.encodedFeatures) {
                VectorTileProto.Tile.Feature.Builder featureBuilder = VectorTileProto.Tile.Feature.newBuilder()
                        .addAllTags(Arrays.stream(feature.tags.toArray()).boxed().collect(Collectors.toList()))
                        .setType(feature.geometry.geomType.asProtobufType())
                        .addAllGeometry(Arrays.stream(feature.geometry.commands).boxed().collect(Collectors.toList()));

                if (feature.id >= 0) {
                    featureBuilder.setId(feature.id);
                }

                tileLayer.addFeatures(featureBuilder.build());
            }

            tile.addLayers(tileLayer.build());
        }
        return tile.build().toByteArray();
    }

    /**
     * Returns true if this tile contains only polygon fills.
     */
    public boolean containsOnlyFills() {
        return containsOnlyFillsOrEdges(false);
    }

    /**
     * Returns true if this tile contains only polygon fills or horizontal/vertical edges that are likely to be repeated
     * across tiles.
     */
    public boolean containsOnlyFillsOrEdges() {
        return containsOnlyFillsOrEdges(true);
    }

    private boolean containsOnlyFillsOrEdges(boolean allowEdges) {
        boolean empty = true;
        for (Layer layer : layers.values()) {
            for (EncodedFeature feature : layer.encodedFeatures) {
                empty = false;
                if (!feature.geometry.isFillOrEdge(allowEdges)) {
                    return false;
                }
            }
        }
        return !empty;
    }

    private enum Command {
        MOVE_TO(1),
        LINE_TO(2),
        CLOSE_PATH(7);

        final int value;

        Command(int value) {
            this.value = value;
        }
    }

    /**
     * A vector tile encoded as a list of commands according to the
     * <a href="https://github.com/mapbox/vector-tile-spec/tree/master/2.1#43-geometry-encoding">vector tile
     * specification</a>.
     * <p>
     * To encode extra precision in intermediate feature geometries, the geometry contained in {@code commands} is scaled
     * to a tile extent of {@code EXTENT * 2^scale}, so when the {@code scale == 0} the extent is {@link #EXTENT} and when
     * {@code scale == 2} the extent is 4x{@link #EXTENT}. Geometries must be scaled back to 0 using {@link #unscale()}
     * before outputting to mbtiles.
     */
    public static class VectorGeometry {
        private final int[] commands;
        private final GeometryType geomType;
        private final int scale;

        public VectorGeometry(int[] commands, GeometryType geomType, int scale) {
            if (scale < 0) {
                throw new IllegalArgumentException("scale can not be less than 0, got: " + scale);
            }
            this.commands = commands;
            this.geomType = geomType;
            this.scale = scale;
        }

        private static final int LEFT = 1;
        private static final int RIGHT = 1 << 1;
        private static final int TOP = 1 << 2;
        private static final int BOTTOM = 1 << 3;
        private static final int INSIDE = 0;
        private static final int ALL = TOP | LEFT | RIGHT | BOTTOM;

        private int getSide(int x, int y, int extent) {
            int result = INSIDE;
            if (x < 0) {
                result |= LEFT;
            } else if (x > extent) {
                result |= RIGHT;
            }
            if (y < 0) {
                result |= TOP;
            } else if (y > extent) {
                result |= BOTTOM;
            }
            return result;
        }

        private static boolean slanted(int x1, int y1, int x2, int y2) {
            return x1 != x2 && y1 != y2;
        }

        private static boolean segmentCrossesTile(int x1, int y1, int x2, int y2, int extent) {
            return (y1 >= 0 || y2 >= 0) &&
                    (y1 <= extent || y2 <= extent) &&
                    (x1 >= 0 || x2 >= 0) &&
                    (x1 <= extent || x2 <= extent);
        }

        private static boolean isSegmentInvalid(boolean allowEdges, int x1, int y1, int x2, int y2, int extent) {
            boolean crossesTile = segmentCrossesTile(x1, y1, x2, y2, extent);
            if (allowEdges) {
                return crossesTile && slanted(x1, y1, x2, y2);
            } else {
                return crossesTile;
            }
        }


        private static boolean visitedEnoughSides(boolean allowEdges, int sides) {
            if (allowEdges) {
                return ((sides & LEFT) > 0 && (sides & RIGHT) > 0) || ((sides & TOP) > 0 && (sides & BOTTOM) > 0);
            } else {
                return sides == ALL;
            }
        }

        /**
         * Returns this encoded geometry, scaled back to 0, so it is safe to emit to mbtiles output.
         */
        public VectorGeometry unscale() {
            return scale == 0 ? this : new VectorGeometry(VectorTile.unscale(commands, scale, geomType), geomType, 0);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            VectorGeometry that = (VectorGeometry) o;

            if (geomType != that.geomType) {
                return false;
            }
            return Arrays.equals(commands, that.commands);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(commands);
            result = 31 * result + geomType.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "VectorGeometry[" +
                    "commands=int[" + commands.length +
                    "], geomType=" + geomType +
                    " (" + geomType.asByte() + ")]";
        }

        /**
         * Returns true if the encoded geometry is a polygon fill.
         */
        public boolean isFill() {
            return isFillOrEdge(false);
        }

        /**
         * Returns true if the encoded geometry is a polygon fill, rectangle edge, or part of a horizontal/vertical line
         * that is likely to be repeated across tiles.
         */
        public boolean isFillOrEdge() {
            return isFillOrEdge(true);
        }

        /**
         * Returns true if the encoded geometry is a polygon fill, or if {@code allowEdges == true} then also a rectangle
         * edge, or part of a horizontal/vertical line that is likely to be repeated across tiles.
         */
        public boolean isFillOrEdge(boolean allowEdges) {
            if (geomType != GeometryType.POLYGON && (!allowEdges || geomType != GeometryType.LINE)) {
                return false;
            }

            boolean isLine = geomType == GeometryType.LINE;

            int extent = EXTENT << scale;
            int visited = INSIDE;
            int firstX = 0;
            int firstY = 0;
            int x = 0;
            int y = 0;

            int geometryCount = commands.length;
            int length = 0;
            int command = 0;
            int i = 0;
            while (i < geometryCount) {

                if (length <= 0) {
                    length = commands[i++];
                    command = length & ((1 << 3) - 1);
                    length = length >> 3;
                    if (isLine && length > 2) {
                        return false;
                    }
                }

                if (length > 0) {
                    if (command == Command.CLOSE_PATH.value) {
                        if (isSegmentInvalid(allowEdges, x, y, firstX, firstY, extent) ||
                                !visitedEnoughSides(allowEdges, visited)) {
                            return false;
                        }
                        length--;
                        continue;
                    }

                    int dx = commands[i++];
                    int dy = commands[i++];

                    length--;

                    dx = zigZagDecode(dx);
                    dy = zigZagDecode(dy);

                    int nextX = x + dx;
                    int nextY = y + dy;

                    if (command == Command.MOVE_TO.value) {
                        firstX = nextX;
                        firstY = nextY;
                        if ((visited = getSide(firstX, firstY, extent)) == INSIDE) {
                            return false;
                        }
                    } else {
                        if (isSegmentInvalid(allowEdges, x, y, nextX, nextY, extent)) {
                            return false;
                        }
                        visited |= getSide(nextX, nextY, extent);
                    }
                    y = nextY;
                    x = nextX;
                }

            }

            return visitedEnoughSides(allowEdges, visited);
        }

    }

    public static class Feature {
        private final String layer;
        private final long id;
        private final VectorGeometry geometry;
        private final Map<String, Object> attrs;
        private final long group;

        /**
         * A feature in a vector tile.
         *
         * @param layer    the layer the feature was in
         * @param id       the feature ID
         * @param geometry the encoded feature geometry
         * @param attrs    tags for the feature to output
         * @param group    grouping key used to limit point density or {@link #NO_GROUP} if not in a group.
         */
        public Feature(
                String layer,
                long id,
                VectorGeometry geometry,
                Map<String, Object> attrs,
                long group
        ) {
            this.layer = layer;
            this.id = id;
            this.geometry = geometry;
            this.attrs = attrs;
            this.group = group;
        }

        public static final long NO_GROUP = Long.MIN_VALUE;

        public Feature(
                String layer,
                long id,
                VectorGeometry geometry,
                Map<String, Object> attrs
        ) {
            this(layer, id, geometry, attrs, NO_GROUP);
        }

        public boolean hasGroup() {
            return group != NO_GROUP;
        }

        /**
         * Encodes {@code newGeometry} and returns a copy of this feature with {@code geometry} replaced with the encoded
         * new geometry.
         */
        public Feature copyWithNewGeometry(Geometry newGeometry) {
            return copyWithNewGeometry(encodeGeometry(newGeometry));
        }

        /**
         * Returns a copy of this feature with {@code geometry} replaced with {@code newGeometry}.
         */
        public Feature copyWithNewGeometry(VectorGeometry newGeometry) {
            return new Feature(
                    layer,
                    id,
                    newGeometry,
                    attrs,
                    group
            );
        }

        /**
         * Returns a copy of this feature with {@code extraAttrs} added to {@code attrs}.
         */
        public Feature copyWithExtraAttrs(Map<String, Object> extraAttrs) {
            return new Feature(
                    layer,
                    id,
                    geometry,
                    Stream.concat(attrs.entrySet().stream(), extraAttrs.entrySet().stream())
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
                    group
            );
        }
    }

    /**
     * Encodes a geometry as a sequence of integers according to the
     * <a href="https://github.com/mapbox/vector-tile-spec/tree/master/2.1#43-geometry-encoding">Geometry * Encoding
     * Specification</a>.
     */
    private static class CommandEncoder {

        final IntArrayList result = new IntArrayList();
        private final double SCALE;
        // Initial points use absolute locations, then subsequent points in a geometry use offsets so
        // need to keep track of previous x/y location during the encoding.
        int x = 0, y = 0;

        CommandEncoder(int scale) {
            this.SCALE = (EXTENT << scale) / SIZE;
        }

        static boolean shouldClosePath(Geometry geometry) {
            return (geometry instanceof Polygon) || (geometry instanceof LinearRing);
        }

        static int commandAndLength(Command command, int repeat) {
            return repeat << 3 | command.value;
        }

        void accept(Geometry geometry) {
            if (geometry instanceof MultiLineString) {
                for (int i = 0; i < geometry.getNumGeometries(); i++) {
                    encode(((LineString) geometry.getGeometryN(i)).getCoordinateSequence(), false, GeometryType.LINE);
                }
            } else if (geometry instanceof Polygon) {
                LineString exteriorRing = ((Polygon) geometry).getExteriorRing();
                encode(exteriorRing.getCoordinateSequence(), true, GeometryType.POLYGON);

                for (int i = 0; i < ((Polygon) geometry).getNumInteriorRing(); i++) {
                    LineString interiorRing = ((Polygon) geometry).getInteriorRingN(i);
                    encode(interiorRing.getCoordinateSequence(), true, GeometryType.LINE);
                }
            } else if (geometry instanceof MultiPolygon) {
                for (int i = 0; i < geometry.getNumGeometries(); i++) {
                    accept(geometry.getGeometryN(i));
                }
            } else if (geometry instanceof LineString) {
                encode(((LineString) geometry).getCoordinateSequence(), shouldClosePath(geometry), GeometryType.LINE);
            } else if (geometry instanceof Point) {
                encode(((Point) geometry).getCoordinateSequence(), false, GeometryType.POINT);
            } else if (geometry instanceof Puntal) {
                encode(new CoordinateArraySequence(geometry.getCoordinates()), shouldClosePath(geometry),
                        geometry instanceof MultiPoint, GeometryType.POINT);
            } else {
                LOGGER.warn("Unrecognized geometry type: " + geometry.getGeometryType());
            }
        }

        void encode(CoordinateSequence cs, boolean closePathAtEnd, GeometryType geomType) {
            encode(cs, closePathAtEnd, false, geomType);
        }

        void encode(CoordinateSequence cs, boolean closePathAtEnd, boolean multiPoint, GeometryType geomType) {
            if (cs.size() == 0) {
                throw new IllegalArgumentException("empty geometry");
            }

            int startIdx = result.size();
            int numPoints = 0;
            int lineToIndex = 0;
            int lineToLength = 0;
            int startX = x;
            int startY = y;

            for (int i = 0; i < cs.size(); i++) {

                double cx = cs.getX(i);
                double cy = cs.getY(i);

                if (i == 0) {
                    result.add(commandAndLength(Command.MOVE_TO, multiPoint ? cs.size() : 1));
                }

                int _x = (int) Math.round(cx * SCALE);
                int _y = (int) Math.round(cy * SCALE);

                // prevent point equal to the previous
                if (i > 0 && _x == x && _y == y && !multiPoint) {
                    lineToLength--;
                    continue;
                }

                // prevent double closing
                if (closePathAtEnd && cs.size() > 1 && i == (cs.size() - 1) && cs.getX(0) == cx && cs.getY(0) == cy) {
                    lineToLength--;
                    continue;
                }

                // delta, then zigzag
                result.add(zigZagEncode(_x - x));
                result.add(zigZagEncode(_y - y));
                numPoints++;

                x = _x;
                y = _y;

                if (i == 0 && cs.size() > 1 && !multiPoint) {
                    // can length be too long?
                    lineToIndex = result.size();
                    lineToLength = cs.size() - 1;
                    result.add(commandAndLength(Command.LINE_TO, lineToLength));
                }

            }

            // update LineTo length
            if (lineToIndex > 0) {
                if (lineToLength == 0) {
                    // remove empty LineTo
                    result.remove(lineToIndex);
                } else {
                    // update LineTo with new length
                    result.set(lineToIndex, commandAndLength(Command.LINE_TO, lineToLength));
                }
            }

            if (closePathAtEnd) {
                result.add(commandAndLength(Command.CLOSE_PATH, 1));
                numPoints++;
            }

            // degenerate geometry, skip emitting
            if (numPoints < geomType.minPoints()) {
                result.elementsCount = startIdx;
                // reset deltas
                x = startX;
                y = startY;
            }
        }
    }

    private class EncodedFeature {
        private final IntArrayList tags;
        private final long id;
        private final VectorGeometry geometry;

        public EncodedFeature(IntArrayList tags, long id, VectorGeometry geometry) {
            this.tags = tags;
            this.id = id;
            this.geometry = geometry;
        }

        public EncodedFeature(Feature in) {
            this(new IntArrayList(), in.id, in.geometry);
        }
    }

    /**
     * Holds all features in an output layer of this tile, along with the index of each tag key/value so that features can
     * store each key/value as a pair of integers.
     */
    private static final class Layer {

        final List<EncodedFeature> encodedFeatures = new ArrayList<>();
        final Map<String, Integer> keys = new LinkedHashMap<>();
        final Map<Object, Integer> values = new LinkedHashMap<>();

        List<String> keys() {
            return new ArrayList<>(keys.keySet());
        }

        List<Object> values() {
            return new ArrayList<>(values.keySet());
        }

        /**
         * Returns the ID associated with {@code key} or adds a new one if not present.
         */
        Integer key(String key) {
            Integer i = keys.get(key);
            if (i == null) {
                i = keys.size();
                keys.put(key, i);
            }
            return i;
        }

        /**
         * Returns the ID associated with {@code value} or adds a new one if not present.
         */
        Integer value(Object value) {
            Integer i = values.get(value);
            if (i == null) {
                i = values.size();
                values.put(value, i);
            }
            return i;
        }

        @Override
        public String toString() {
            return "Layer{" + encodedFeatures.size() + "}";
        }
    }
}
