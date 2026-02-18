package com.graphhopper.reader.dem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
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

    private final TerrainEncoding encoding;
    private final boolean interpolate;
    private final int preferredZoom;

    private final PMTilesReader reader = new PMTilesReader();

    // Tile key -> memory-mapped ByteBuffer (one per tile, each a direct mmap of the .tile file).
    // No need for DataAccess as every file is rather small, and we can change to in-memory too via tileDir=null.
    // Sentinels MISSING_BUF / SEA_LEVEL_BUF for tiles without elevation data.
    private final Map<Long, ByteBuffer> tileBuffers = new HashMap<>();
    private static final ByteBuffer MISSING_BUF = ByteBuffer.allocate(0);
    private static final ByteBuffer SEA_LEVEL_BUF = ByteBuffer.allocate(1);

    private int tileSize;

    // Directory for .tile files. If non-null and writable, decoded tiles are persisted
    // there so subsequent runs can mmap them without re-decoding.
    private File tileDir;
    private boolean clearTileFiles = true;

    /**
     * @param preferredZoom 10 means ~76m at equator and ~49m in Germany (default).
     *                      11 means ~38m at equator and ~25m in Germany.
     *                      12 means ~19m at equator and ~12m in Germany.
     * @param tileDir        directory for .tile tile cache files. Pre-populated by pmtiles_to_ele.py
     *                      or built lazily on first access. If null, decoded tiles are kept on heap only.
     */
    public PMTilesElevationProvider(String filePath, TerrainEncoding encoding,
                                    boolean interpolate, int preferredZoom, String tileDir) {
        this.encoding = encoding;
        this.interpolate = interpolate;
        this.preferredZoom = preferredZoom;
        try {
            reader.open(filePath);
            reader.checkWebPSupport();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (tileDir != null && !tileDir.isEmpty()) {
            this.tileDir = new File(tileDir);
            this.tileDir.mkdirs();
        }
    }

    public PMTilesElevationProvider setAutoRemoveTemporaryFiles(boolean clearTileFiles) {
        this.clearTileFiles = clearTileFiles;
        return this;
    }

    @Override
    public double getEle(double lat, double lon) {
        try {
            // Auto-select zoom: use preferredZoom if set, otherwise cap at 11.
            int zoom = preferredZoom > 0 ? preferredZoom : Math.min(reader.header.maxZoom, 11);
            return sampleElevation(lat, lon, zoom);
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
        tileBuffers.clear(); // MappedByteBuffers are unmapped by GC
        reader.close();
        if (clearTileFiles && tileDir != null) {
            File[] files = tileDir.listFiles((dir, name) -> name.endsWith(".tile"));
            if (files != null)
                for (File f : files) f.delete();
        }
    }

    private double sampleElevation(double lat, double lon, int zoom) throws IOException {
        int n = 1 << zoom;
        double xTileD = (lon + 180.0) / 360.0 * n;
        double latRad = Math.toRadians(lat);
        double yTileD = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;

        int tileX = Math.max(0, Math.min(n - 1, (int) Math.floor(xTileD)));
        int tileY = Math.max(0, Math.min(n - 1, (int) Math.floor(yTileD)));

        ByteBuffer tile = getTileBuffer(PMTilesReader.zxyToTileId(zoom, tileX, tileY));
        if (tile == MISSING_BUF) return Double.NaN;
        if (tile == SEA_LEVEL_BUF) return 0;

        int w = tileSize, h = tileSize;
        double px = (xTileD - tileX) * (w - 1);
        double py = (yTileD - tileY) * (h - 1);

        if (interpolate) {
            int x0 = Math.max(0, Math.min(w - 2, (int) Math.floor(px)));
            int y0 = Math.max(0, Math.min(h - 2, (int) Math.floor(py)));
            double fx = px - x0, fy = py - y0;
            short v00 = tile.getShort((y0 * w + x0) * 2);
            short v10 = tile.getShort((y0 * w + Math.min(x0 + 1, w - 1)) * 2);
            short v01 = tile.getShort((Math.min(y0 + 1, h - 1) * w + x0) * 2);
            short v11 = tile.getShort((Math.min(y0 + 1, h - 1) * w + Math.min(x0 + 1, w - 1)) * 2);
            if (v00 == Short.MIN_VALUE || v10 == Short.MIN_VALUE || v01 == Short.MIN_VALUE || v11 == Short.MIN_VALUE)
                return Double.NaN;
            return v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy)
                    + v01 * (1 - fx) * fy + v11 * fx * fy;
        } else {
            int ix = Math.max(0, Math.min(w - 1, (int) Math.round(px)));
            int iy = Math.max(0, Math.min(h - 1, (int) Math.round(py)));
            short val = tile.getShort((iy * w + ix) * 2);
            if (val == Short.MIN_VALUE) return Double.NaN;
            return val;
        }
    }

    private ByteBuffer getTileBuffer(long tileId) throws IOException {
        ByteBuffer existing = tileBuffers.get(tileId);
        if (existing != null) return existing;

        // Try pre-decoded .tile file first
        ByteBuffer buf = tryMmapTileFile(tileId);
        if (buf != null) {
            tileBuffers.put(tileId, buf);
            return buf;
        }

        // Decode from PMTiles
        byte[] raw = reader.getTileBytes(tileId);
        if (raw == null) {
            tileBuffers.put(tileId, MISSING_BUF);
            return MISSING_BUF;
        }

        byte[] elevBytes = decodeTerrain(raw);
        if (elevBytes == null) {
            tileBuffers.put(tileId, MISSING_BUF);
            return MISSING_BUF;
        }
        if (elevBytes.length == 0) {
            tileBuffers.put(tileId, SEA_LEVEL_BUF);
            return SEA_LEVEL_BUF;
        }

        // Persist as .tile file and mmap, or wrap as heap buffer if no tileDir
        buf = persistAndMmap(tileId, elevBytes);
        tileBuffers.put(tileId, buf);
        return buf;
    }

    /**
     * Try to mmap an existing .tile file. Returns the mmap'd ByteBuffer if the file exists,
     * or null if not found (either no tileDir or file not yet decoded).
     */
    private ByteBuffer tryMmapTileFile(long tileId) throws IOException {
        if (tileDir == null) return null;
        File f = tileFile(tileId);
        if (!f.exists()) return null;
        return mmapFile(f);
    }

    /**
     * Write decoded bytes to an .tile file and mmap it, or wrap as heap buffer if no tileDir.
     */
    private ByteBuffer persistAndMmap(long tileId, byte[] elevBytes) throws IOException {
        if (tileDir != null) {
            File f = tileFile(tileId);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(elevBytes);
            }
            return mmapFile(f);
        }
        // No tileDir — wrap as heap buffer (tests, small regions)
        return ByteBuffer.wrap(elevBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private File tileFile(long tileId) {
        return new File(tileDir, tileId + ".tile");
    }

    private ByteBuffer mmapFile(File f) throws IOException {
        if (tileSize == 0) {
            // Derive tile size from file: fileLength = tileSize * tileSize * 2
            tileSize = (int) Math.sqrt(f.length() / 2);
        }
        try (FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)) {
            ByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, f.length());
            buf.order(ByteOrder.LITTLE_ENDIAN);
            return buf;
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
        if (tileSize == 0) tileSize = w; // record on first decode

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
                // no-data/ocean. No real place is below -1000m, so treat as no-data sentinel.
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
