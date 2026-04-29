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
package com.graphhopper.reader.dem;

import com.graphhopper.storage.MMapDataAccess;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * GraphHopper ElevationProvider that reads elevation data directly from a
 * PMTiles v3 archive containing terrain-RGB encoded tiles.
 * <p>
 * If a directory of pre-decoded .tile files exists (created from previously runs),
 * each file is memory-mapped directly and there is no image decoding, no copying happening.
 * Otherwise tiles are decoded from PMTiles on first access and written as .tile
 * files so subsequent runs skip decoding.
 * <p>
 * Not thread-safe.
 */
public class PMTilesElevationProvider implements ElevationProvider {

    public enum TerrainEncoding {MAPBOX, TERRARIUM}

    private static class PackedTileData {
        private ByteBuffer data;
        private final int blockSize;
        private final int blocksPerAxis;
        private int[] blockOffsets;
        private final int payloadOffset;

        PackedTileData(ByteBuffer data, int blockSize, int blocksPerAxis, int[] blockOffsets, int payloadOffset) {
            this.data = data;
            this.blockSize = blockSize;
            this.blocksPerAxis = blocksPerAxis;
            if (blockOffsets.length != blocksPerAxis * blocksPerAxis + 1)
                throw new IllegalArgumentException("Invalid packed block table length");
            this.blockOffsets = blockOffsets;
            this.payloadOffset = payloadOffset;
        }

        public short get(int x, int y) {
            int blockX = x / blockSize;
            int blockY = y / blockSize;
            int blockIndex = blockY * blocksPerAxis + blockX;
            int blockStart = payloadOffset + blockOffsets[blockIndex];
            int localX = x - blockX * blockSize;
            int localY = y - blockY * blockSize;
            int idx = localY * blockSize + localX;
            int type = data.get(blockStart) & 0xFF;

            if (type == PackedTileCodec.TYPE_SEA) {
                return 0;
            } else if (type == PackedTileCodec.TYPE_CONST) {
                return data.getShort(blockStart + 1);
            } else if (type == PackedTileCodec.TYPE_DELTA8) {
                short base = data.getShort(blockStart + 1);
                int delta = data.get(blockStart + 3 + idx) & 0xFF;
                return (short) (base + delta);
            } else if (type == PackedTileCodec.TYPE_RAW16) {
                return data.getShort(blockStart + 1 + idx * 2);
            }
            throw new IllegalStateException("Unknown packed block type: " + type);
        }

        void release() {
            if (data != null && data.isDirect()) // ensure it is not MISSING or SEA or heap allocated
                MMapDataAccess.cleanMappedByteBuffer(data);
            data = null;
            blockOffsets = null;
        }
    }

    private static final PackedTileData MISSING_TILE = new PackedTileData(null, 1, 1, new int[]{0, 0}, 0) {
        @Override
        public short get(int x, int y) {
            return Short.MIN_VALUE;
        }
    };
    private static final PackedTileData SEA_LEVEL_TILE = new PackedTileData(null, 1, 1, new int[]{0, 0}, 0) {
        @Override
        public short get(int x, int y) {
            return 0;
        }
    };

    private final TerrainEncoding encoding;
    private final boolean interpolate;
    private final int preferredZoom;
    private int zoom;
    private long hilbertBase;
    private int n; // 1 << zoom

    private final PMTilesReader reader = new PMTilesReader();

    // Cache of packed tiles, keyed by Hilbert tile ID. Missing (or all-sea) tiles use marker objects.
    // On-disk .tile files use the packed block format defined in PackedTileCodex.
    private final Map<Long, PackedTileData> tileBuffers = new HashMap<>();

    // Last-tile cache: consecutive getEle() calls typically hit the same tile.
    private long lastTileId = -1;
    private PackedTileData lastTileBuf;

    private int tileSize;

    // Directory for .tile files. If non-null and writable, decoded tiles are persisted
    // there so subsequent runs can mmap them without re-decoding.
    private File tileDir;
    private final String tileDirStr;

    private boolean clearTileFiles = true;

    private final String pmFileStr;

    /**
     * @param preferredZoom 10 means ~76m at equator and ~49m in Germany (default).
     *                      11 means ~38m at equator and ~25m in Germany.
     *                      12 means ~19m at equator and ~12m in Germany.
     * @param tileDir       directory for .tile tile cache files. Pre-populated by pmtiles_to_ele.py
     *                      or built lazily on first access. If null, decoded tiles are kept on heap only.
     */
    public PMTilesElevationProvider(String pmFile, TerrainEncoding encoding,
                                    boolean interpolate, int preferredZoom, String tileDir) {
        this.encoding = encoding;
        this.interpolate = interpolate;
        this.preferredZoom = preferredZoom;
        this.pmFileStr = pmFile;
        this.tileDirStr = tileDir;
    }

    @Override
    public ElevationProvider init() {
        try {
            reader.open(pmFileStr);
            reader.checkWebPSupport();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.zoom = preferredZoom > 0 ? preferredZoom : Math.min(reader.header.maxZoom, 11);
        if (this.zoom < 1)
            throw new IllegalArgumentException("Zoom level must be at least 1, got " + this.zoom);
        this.hilbertBase = PMTilesReader.hilbertBase(zoom);
        this.n = 1 << zoom;

        if (tileDirStr != null && !tileDirStr.isEmpty()) {
            this.tileDir = new File(tileDirStr);
            this.tileDir.mkdirs();
        }
        return this;
    }

    public PMTilesElevationProvider setAutoRemoveTemporaryFiles(boolean clearTileFiles) {
        this.clearTileFiles = clearTileFiles;
        return this;
    }

    @Override
    public double getEle(double lat, double lon) {
        try {
            return sampleElevation(lat, lon);
        } catch (Exception e) {
            System.err.println("PMTilesElevationProvider.getEle(" + lat + ", " + lon + ") failed: " + e.getMessage());
            return Double.NaN;
        }
    }

    @Override
    public boolean canInterpolate() {
        return interpolate;
    }

    @Override
    public void release() {
        for (PackedTileData p : tileBuffers.values()) {
            p.release();
        }
        tileBuffers.clear();
        lastTileId = -1;
        lastTileBuf = null;
        reader.close();
        if (clearTileFiles && tileDir != null) {
            File[] files = tileDir.listFiles((dir, name) -> name.endsWith(".tile"));
            if (files != null)
                for (File f : files) f.delete();
        }
    }

    private long zxyToTileId(int x, int y) {
        return hilbertBase + PMTilesReader.xyToHilbertD(zoom, x, y);
    }

    private double sampleElevation(double lat, double lon) throws IOException {
        double xTileD = (lon + 180.0) / 360.0 * n;
        double latRad = Math.toRadians(lat);
        double yTileD = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;

        int tileX = Math.max(0, Math.min(n - 1, (int) Math.floor(xTileD)));
        int tileY = Math.max(0, Math.min(n - 1, (int) Math.floor(yTileD)));

        PackedTileData tile = getTileBuffer(zxyToTileId(tileX, tileY), tileX, tileY);
        if (tile == MISSING_TILE) return Double.NaN;
        if (tile == SEA_LEVEL_TILE) return 0;

        int w = tileSize, h = tileSize;
        double px = (xTileD - tileX) * (w - 1);
        double py = (yTileD - tileY) * (h - 1);

        if (interpolate) {
            int x0 = Math.max(0, Math.min(w - 2, (int) Math.floor(px)));
            int y0 = Math.max(0, Math.min(h - 2, (int) Math.floor(py)));
            double fx = px - x0, fy = py - y0;
            short v00 = tile.get(x0, y0);
            short v10 = tile.get(x0 + 1, y0);
            short v01 = tile.get(x0, y0 + 1);
            short v11 = tile.get(x0 + 1, y0 + 1);
            if (v00 == Short.MIN_VALUE || v10 == Short.MIN_VALUE || v01 == Short.MIN_VALUE || v11 == Short.MIN_VALUE)
                return Double.NaN;
            return v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy)
                    + v01 * (1 - fx) * fy + v11 * fx * fy;
        } else {
            int ix = Math.max(0, Math.min(w - 1, (int) Math.round(px)));
            int iy = Math.max(0, Math.min(h - 1, (int) Math.round(py)));
            short val = tile.get(ix, iy);
            if (val == Short.MIN_VALUE) return Double.NaN;
            return val;
        }
    }

    private PackedTileData getTileBuffer(long tileId, int tileX, int tileY) throws IOException {
        if (tileId == lastTileId) return lastTileBuf;

        PackedTileData existing = tileBuffers.get(tileId);
        if (existing != null) {
            lastTileId = tileId;
            lastTileBuf = existing;
            return existing;
        }

        // Try pre-decoded .tile file first
        PackedTileData buf = tryMmapTileFile(tileId);
        if (buf == null) {
            // Decode from PMTiles
            byte[] raw = reader.getTileBytes(tileId);
            if (raw == null) {
                buf = MISSING_TILE;
            } else {
                byte[] elevBytes = decodeTerrain(raw);
                if (elevBytes == null) {
                    buf = MISSING_TILE;
                } else if (elevBytes.length == 0) {
                    buf = SEA_LEVEL_TILE;
                } else {
                    fillGaps(elevBytes, tileSize, tileX, tileY, n);
                    buf = persistAndLoad(tileId, elevBytes);
                }
            }
        }

        tileBuffers.put(tileId, buf);
        lastTileId = tileId;
        lastTileBuf = buf;
        return buf;
    }

    /**
     * Try to mmap an existing .tile file. Returns tile data if the file exists,
     * or null if not found (either no tileDir or file not yet decoded).
     */
    private PackedTileData tryMmapTileFile(long tileId) throws IOException {
        if (tileDir == null) return null;
        File f = tileFile(tileId);
        if (!f.exists()) return null;
        return loadTileData(f);
    }

    /**
     * Write decoded bytes to a packed .tile file and load it, or keep packed bytes on heap if no tileDir.
     */
    private PackedTileData persistAndLoad(long tileId, byte[] elevBytes) throws IOException {
        byte[] packed = PackedTileCodec.encodePacked(elevBytes, tileSize, PackedTileCodec.DEFAULT_BLOCK_SIZE);
        if (tileDir != null) {
            File f = tileFile(tileId);
            Files.write(f.toPath(), packed);
            return loadTileData(f);
        }
        // ByteBuffer in heap
        ByteBuffer buf = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
        return toPackedTileData(buf);
    }

    private File tileFile(long tileId) {
        return new File(tileDir, tileId + "_" + zoom + ".tile");
    }

    private PackedTileData loadTileData(File f) throws IOException {
        try (FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            ByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, f.length());
            buf.order(ByteOrder.LITTLE_ENDIAN);
            if (!PackedTileCodec.isPackedTile(buf)) {
                throw new IOException("Unsupported legacy raw .tile format in " + f
                        + ". Remove cached .tile files so they can be regenerated as packed tiles.");
            }
            return toPackedTileData(buf);
        }
    }

    private PackedTileData toPackedTileData(ByteBuffer buf) {
        PackedTileCodec.PackedHeader h = PackedTileCodec.readPackedHeader(buf);
        if (tileSize == 0) tileSize = h.tileSize(); // tileSize is set when tile comes from cache
        else if (tileSize != h.tileSize())
            throw new IllegalStateException("Inconsistent packed tile size: expected " + tileSize + " but got " + h.tileSize());
        if (tileSize < PackedTileCodec.DEFAULT_BLOCK_SIZE)
            throw new IllegalStateException("tileSize must be at least " + PackedTileCodec.DEFAULT_BLOCK_SIZE + ", got " + tileSize);
        return new PackedTileData(buf, h.blockSize(), h.blocksPerAxis(), h.blockOffsets(), h.payloadOffset());
    }

    /**
     * BFS wavefront fill: replaces Short.MIN_VALUE gap pixels with the average of their
     * valid 4-connected neighbors, propagating inward. Only gap pixels reachable from valid
     * data are filled; isolated gaps remain as Short.MIN_VALUE.
     * See <a href="ttps://github.com/mapterhorn/mapterhorn/discussions/217">discussion</a>
     */
    static void fillGaps(byte[] data, int w, int tileX, int tileY, int n) {
        ShortBuffer shorts = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int total = shorts.capacity();
        int h = total / w;
        int[] DX = {-1, 1, 0, 0};
        int[] DY = {0, 0, -1, 1};

        // Log one line per connected gap area with its lat/lon centroid
        boolean[] visited = new boolean[total];
        for (int i = 0; i < total; i++) {
            if (shorts.get(i) != Short.MIN_VALUE || visited[i]) continue;
            ArrayDeque<Integer> comp = new ArrayDeque<>();
            comp.add(i);
            visited[i] = true;
            int count = 0;
            long sumPx = 0, sumPy = 0;
            while (!comp.isEmpty()) {
                int ci = comp.poll();
                count++;
                sumPx += ci % w;
                sumPy += ci / w;
                int cx = ci % w, cy = ci / w;
                for (int d = 0; d < 4; d++) {
                    int nx = cx + DX[d], ny = cy + DY[d];
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                        int ni = ny * w + nx;
                        if (shorts.get(ni) == Short.MIN_VALUE && !visited[ni]) {
                            visited[ni] = true;
                            comp.add(ni);
                        }
                    }
                }
            }
            double cx = (double) sumPx / count;
            double cy = (double) sumPy / count;
            double lon = ((tileX + cx / w) / n) * 360.0 - 180.0;
            double yNorm = (tileY + cy / h) / n;
            double lat = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * yNorm))));
            LoggerFactory.getLogger(PMTilesElevationProvider.class)
                    .warn("fillGaps: {} pixels at lat={}, lon={}", count,
                            String.format("%.5f", lat), String.format("%.5f", lon));
        }

        // Seed: gap pixels bordering valid data
        boolean[] queued = new boolean[total];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < total; i++) {
            if (shorts.get(i) != Short.MIN_VALUE) continue;
            int x = i % w, y = i / w;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h
                        && shorts.get(ny * w + nx) != Short.MIN_VALUE) {
                    queue.add(i);
                    queued[i] = true;
                    break;
                }
            }
        }

        // BFS: fill each gap pixel with average of valid neighbors
        while (!queue.isEmpty()) {
            int i = queue.poll();
            if (shorts.get(i) != Short.MIN_VALUE) continue;
            int x = i % w, y = i / w;
            int sum = 0, cnt = 0;
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    short v = shorts.get(ny * w + nx);
                    if (v != Short.MIN_VALUE) {
                        sum += v;
                        cnt++;
                    }
                }
            }
            if (cnt == 0) continue;
            shorts.put(i, (short) Math.round((double) sum / cnt));
            for (int d = 0; d < 4; d++) {
                int nx = x + DX[d], ny = y + DY[d];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    int ni = ny * w + nx;
                    if (shorts.get(ni) == Short.MIN_VALUE && !queued[ni]) {
                        queue.add(ni);
                        queued[ni] = true;
                    }
                }
            }
        }
    }

    /**
     * Decodes terrain-RGB image bytes into a little-endian byte[] of short elevation values.
     *
     * @return byte[] with LE-encoded shorts, empty byte[] if all elevations are exactly 0 (sea level), or null on decode failure.
     */
    byte[] decodeTerrain(byte[] imageBytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (img == null) {
            // Check if it's a WebP file (RIFF....WEBP magic)
            if (imageBytes.length > 12 && imageBytes[0] == 'R' && imageBytes[1] == 'I'
                    && imageBytes[2] == 'F' && imageBytes[3] == 'F'
                    && imageBytes[8] == 'W' && imageBytes[9] == 'E'
                    && imageBytes[10] == 'B' && imageBytes[11] == 'P') {
                throw new IOException(
                        "Tile is WebP format but no WebP ImageIO plugin found. " +
                                "Add com.github.usefulness:webp-imageio to your classpath.");
            }
            return null;
        }

        int w = img.getWidth(), h = img.getHeight();
        if (w != h)
            throw new IOException("Unsupported non-square elevation tile: " + w + "x" + h + ". Expected square terrain tiles.");
        if (tileSize == 0) tileSize = w; // tileSize set on first decode
        else if (tileSize != w)
            throw new IOException("Inconsistent terrain tile size: expected " + tileSize + " but got " + w);
        if (tileSize % PackedTileCodec.DEFAULT_BLOCK_SIZE != 0)
            throw new IOException("tileSize must be a multiple of blockSize: tileSize=" + tileSize
                    + ", blockSize=" + PackedTileCodec.DEFAULT_BLOCK_SIZE);

        byte[] elev = new byte[h * w * 2];
        boolean allSeaLevel = true;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double e;
                if (encoding == TerrainEncoding.MAPBOX) {
                    e = -10000.0 + (r * 65536 + g * 256 + b) * 0.1;
                } else {
                    e = (r * 256.0 + g + b / 256.0) - 32768.0;
                }
                // Mapbox uses rgb(0,0,0) = -10000 and Terrarium rgb(0,0,0) = -32768 for
                // no-data/ocean. No real place is below -1000m, so treat as no-data marker.
                short s = e < -1000 ? Short.MIN_VALUE
                        : (short) Math.max(-32768, Math.min(32767, Math.round(e)));
                if (s != 0) allSeaLevel = false;

                // little-endian, matching ByteBuffer.LITTLE_ENDIAN order
                int idx = (y * w + x) * 2;
                elev[idx] = (byte) (s & 0xFF);
                elev[idx + 1] = (byte) ((s >> 8) & 0xFF);
            }
        }
        return allSeaLevel ? new byte[0] : elev;
    }
}
