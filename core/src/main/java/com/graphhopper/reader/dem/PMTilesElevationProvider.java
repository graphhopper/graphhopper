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
 * If a directory of pre-decoded .ele files exists (created by pmtiles_to_ele.py),
 * each file is memory-mapped directly — no image decoding, no copying.
 * Otherwise tiles are decoded from PMTiles on first access and written as .ele
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

    // Tile key -> memory-mapped ByteBuffer (one per tile, each a direct mmap of the .ele file).
    // Sentinels MISSING_BUF / SEA_LEVEL_BUF for tiles without elevation data.
    private final Map<Long, ByteBuffer> tileBuffers = new HashMap<>();
    private static final ByteBuffer MISSING_BUF = ByteBuffer.allocate(0);
    private static final ByteBuffer SEA_LEVEL_BUF = ByteBuffer.allocate(1);

    private int tileSize;

    // Directory for .ele files. If non-null and writable, decoded tiles are persisted
    // there so subsequent runs can mmap them without re-decoding.
    private File eleDir;
    private boolean clearEleFiles = true;

    /**
     * @param preferredZoom 10 means ~76m at equator and ~49m in Germany (default).
     *                      11 means ~38m at equator and ~25m in Germany.
     *                      12 means ~19m at equator and ~12m in Germany.
     * @param eleDir        directory for .ele tile cache files. Pre-populated by pmtiles_to_ele.py
     *                      or built lazily on first access. If null, decoded tiles are kept on heap only.
     */
    public PMTilesElevationProvider(String filePath, TerrainEncoding encoding,
                                    boolean interpolate, int preferredZoom, String eleDir) {
        this.encoding = encoding;
        this.interpolate = interpolate;
        this.preferredZoom = preferredZoom;
        try {
            reader.open(filePath);
            reader.checkWebPSupport();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (eleDir != null && !eleDir.isEmpty()) {
            this.eleDir = new File(eleDir);
            this.eleDir.mkdirs();
        }
    }

    public PMTilesElevationProvider setAutoRemoveTemporaryFiles(boolean clearEleFiles) {
        this.clearEleFiles = clearEleFiles;
        return this;
    }

    // =========================================================================
    // ElevationProvider interface
    // =========================================================================

    @Override
    public double getEle(double lat, double lon) {
        try {
            // Auto-select zoom: use preferredZoom if set, otherwise cap at 10.
            // Zoom 10 with 512px tiles ≈ 19m resolution.
            // Zoom 12 would need 16× more tiles (also increasing cache access by a lot) for marginal benefit.
            int zoom = preferredZoom > 0 ? preferredZoom : Math.min(reader.header.maxZoom, 10);
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
        if (clearEleFiles && eleDir != null) {
            File[] files = eleDir.listFiles((dir, name) -> name.endsWith(".ele"));
            if (files != null)
                for (File f : files) f.delete();
        }
    }

    // =========================================================================
    // Core elevation sampling
    // =========================================================================

    private double sampleElevation(double lat, double lon, int zoom) throws IOException {
        int n = 1 << zoom;
        double xTileD = (lon + 180.0) / 360.0 * n;
        double latRad = Math.toRadians(lat);
        double yTileD = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;

        int tileX = Math.max(0, Math.min(n - 1, (int) Math.floor(xTileD)));
        int tileY = Math.max(0, Math.min(n - 1, (int) Math.floor(yTileD)));

        ByteBuffer tile = getTileBuffer(zoom, tileX, tileY);
        if (tile == MISSING_BUF) return Double.NaN;
        if (tile == SEA_LEVEL_BUF) return 0;

        int w = tileSize, h = tileSize;
        double px = (xTileD - tileX) * (w - 1);
        double py = (yTileD - tileY) * (h - 1);

        if (interpolate) {
            int x0 = Math.max(0, Math.min(w - 2, (int) Math.floor(px)));
            int y0 = Math.max(0, Math.min(h - 2, (int) Math.floor(py)));
            double fx = px - x0, fy = py - y0;
            double v00 = tile.getShort((y0 * w + x0) * 2);
            double v10 = tile.getShort((y0 * w + Math.min(x0 + 1, w - 1)) * 2);
            double v01 = tile.getShort((Math.min(y0 + 1, h - 1) * w + x0) * 2);
            double v11 = tile.getShort((Math.min(y0 + 1, h - 1) * w + Math.min(x0 + 1, w - 1)) * 2);
            return v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy)
                    + v01 * (1 - fx) * fy + v11 * fx * fy;
        } else {
            int ix = Math.max(0, Math.min(w - 1, (int) Math.round(px)));
            int iy = Math.max(0, Math.min(h - 1, (int) Math.round(py)));
            return tile.getShort((iy * w + ix) * 2);
        }
    }

    // =========================================================================
    // Tile loading: mmap .ele files or decode from PMTiles
    // =========================================================================

    private ByteBuffer getTileBuffer(int z, int x, int y) throws IOException {
        long key = ((long) z << 50) | ((long) x << 25) | y;
        ByteBuffer existing = tileBuffers.get(key);
        if (existing != null) return existing;

        // Try pre-decoded .ele file first
        ByteBuffer buf = tryMmapEleFile(z, x, y);
        if (buf != null) {
            tileBuffers.put(key, buf);
            return buf;
        }

        // Decode from PMTiles
        byte[] raw = reader.getTileBytes(z, x, y);
        if (raw == null) {
            tileBuffers.put(key, MISSING_BUF);
            return MISSING_BUF;
        }

        byte[] elevBytes = decodeTerrain(raw);
        if (elevBytes == null) {
            tileBuffers.put(key, MISSING_BUF);
            return MISSING_BUF;
        }
        if (elevBytes.length == 0) {
            tileBuffers.put(key, SEA_LEVEL_BUF);
            return SEA_LEVEL_BUF;
        }

        // Persist as .ele file and mmap, or wrap as heap buffer if no eleDir
        buf = persistAndMmap(z, x, y, elevBytes);
        tileBuffers.put(key, buf);
        return buf;
    }

    /**
     * Try to mmap an existing .ele file. Returns the mmap'd ByteBuffer if the file exists,
     * or null if not found (either no eleDir or file not yet decoded).
     */
    private ByteBuffer tryMmapEleFile(int z, int x, int y) throws IOException {
        if (eleDir == null) return null;
        File f = eleFile(z, x, y);
        if (!f.exists()) return null;
        return mmapFile(f);
    }

    /**
     * Write decoded bytes to an .ele file and mmap it, or wrap as heap buffer if no eleDir.
     */
    private ByteBuffer persistAndMmap(int z, int x, int y, byte[] elevBytes) throws IOException {
        if (eleDir != null) {
            File f = eleFile(z, x, y);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(elevBytes);
            }
            return mmapFile(f);
        }
        // No eleDir — wrap as heap buffer (tests, small regions)
        return ByteBuffer.wrap(elevBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private File eleFile(int z, int x, int y) {
        long tileId = PMTilesReader.zxyToTileId(z, x, y);
        return new File(eleDir, "z" + z + "_" + tileId + ".ele");
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
     * @return byte[] with LE-encoded shorts, empty byte[] if all elevations are <= 0 (sea level), or null on decode failure.
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
                short s = (short) Math.max(-32768, Math.min(32767, Math.round(e)));
                if (s > 0) allSeaLevel = false;

                // little-endian, matching ByteBuffer.LITTLE_ENDIAN order
                int idx = (y * w + x) * 2;
                elev[idx] = (byte) (s & 0xFF);
                elev[idx + 1] = (byte) ((s >> 8) & 0xFF);
            }
        }
        return allSeaLevel ? new byte[0] : elev;
    }
}
