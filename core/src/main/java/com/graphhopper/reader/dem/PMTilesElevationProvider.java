package com.graphhopper.reader.dem;

import com.graphhopper.storage.DAType;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.GHDirectory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * GraphHopper ElevationProvider that reads elevation data directly from a
 * PMTiles v3 archive containing terrain-RGB encoded tiles.
 * <p>
 * Decoded tiles are stored in a memory-mapped file (via DataAccess) so the OS
 * page cache manages what stays in RAM. The Java heap only holds a small offset
 * index (~50-80 MB for 1M tiles at planet scale).
 * <p>
 * Not thread-safe.
 */
public class PMTilesElevationProvider implements ElevationProvider {

    public enum TerrainEncoding {MAPBOX, TERRARIUM}

    private final TerrainEncoding encoding;
    private final boolean interpolate;
    private final int preferredZoom;

    private final PMTilesReader reader = new PMTilesReader();

    // Tile key -> byte offset in tileData. -1 sentinel not stored; absence means not yet decoded.
    private final Map<Long, Long> tileOffsets = new HashMap<>();
    private final boolean ownDir;
    private Directory dir;
    private DataAccess tileData;
    private long nextOffset = 0;

    private int tileSize;

    /**
     * @param preferredZoom 10 means ~76m at equator and ~49m in Germany (default).
     *                      11 means ~38m at equator and ~25m in Germany.
     *                      12 means ~19m at equator and ~12m in Germany.
     * @param cacheDir      directory for the memory-mapped tile cache file. If null, derived from filePath's parent.
     */
    public PMTilesElevationProvider(String filePath, TerrainEncoding encoding,
                                    boolean interpolate, int preferredZoom, String cacheDir) {
        this(filePath, encoding, interpolate, preferredZoom, createDir(filePath, cacheDir), true);
    }

    /**
     * Constructor accepting an explicit Directory (e.g. a RAM-backed directory for tests).
     */
    public PMTilesElevationProvider(String filePath, TerrainEncoding encoding,
                                    boolean interpolate, int preferredZoom, Directory dir) {
        this(filePath, encoding, interpolate, preferredZoom, dir, false);
    }

    private PMTilesElevationProvider(String filePath, TerrainEncoding encoding,
                                     boolean interpolate, int preferredZoom, Directory dir, boolean ownDir) {
        this.encoding = encoding;
        this.interpolate = interpolate;
        this.preferredZoom = preferredZoom;
        this.dir = dir;
        this.ownDir = ownDir;
        try {
            reader.open(filePath);
            reader.checkWebPSupport();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        tileData = dir.create("pmtiles_elev_cache");
        tileData.create(1_000_000);
    }

    private static Directory createDir(String filePath, String cacheDir) {
        if (cacheDir == null || cacheDir.isEmpty()) {
            File parent = new File(filePath).getAbsoluteFile().getParentFile();
            cacheDir = parent != null ? parent.getAbsolutePath() : ".";
        }
        return new GHDirectory(cacheDir, DAType.MMAP);
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
        tileOffsets.clear();
        nextOffset = 0;
        reader.close();

        // TODO NOW similar to cgiar provider make this configurable which will make it much faster for next import
        if (tileData != null) {
            tileData.close();
            tileData = null;
        }

        if (dir != null) {
            if (ownDir) dir.clear();
            else dir.close();
            dir = null;
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

        long offset = getDecodedTileOffset(zoom, tileX, tileY);
        if (offset == MISSING) return Double.NaN;
        if (offset == SEA_LEVEL) return 0;

        int w = tileSize, h = tileSize;
        double px = (xTileD - tileX) * (w - 1);
        double py = (yTileD - tileY) * (h - 1);

        if (interpolate) {
            int x0 = Math.max(0, Math.min(w - 2, (int) Math.floor(px)));
            int y0 = Math.max(0, Math.min(h - 2, (int) Math.floor(py)));
            double fx = px - x0, fy = py - y0;
            double v00 = tileData.getShort(offset + (long) (y0 * w + x0) * 2);
            double v10 = tileData.getShort(offset + (long) (y0 * w + Math.min(x0 + 1, w - 1)) * 2);
            double v01 = tileData.getShort(offset + (long) (Math.min(y0 + 1, h - 1) * w + x0) * 2);
            double v11 = tileData.getShort(offset + (long) (Math.min(y0 + 1, h - 1) * w + Math.min(x0 + 1, w - 1)) * 2);
            return v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy)
                    + v01 * (1 - fx) * fy + v11 * fx * fy;
        } else {
            int ix = Math.max(0, Math.min(w - 1, (int) Math.round(px)));
            int iy = Math.max(0, Math.min(h - 1, (int) Math.round(py)));
            return tileData.getShort(offset + (long) (iy * w + ix) * 2);
        }
    }

    // =========================================================================
    // Tile decode + mmap cache
    // =========================================================================

    /**
     * Tile absent from the archive.
     */
    private static final long MISSING = -1;
    /**
     * Tile decoded but all elevations are <= 0 (sea level).
     */
    private static final long SEA_LEVEL = -2;

    private long getDecodedTileOffset(int z, int x, int y) throws IOException {
        long key = ((long) z << 50) | ((long) x << 25) | y;
        Long existing = tileOffsets.get(key);
        if (existing != null) return existing;

        byte[] raw = reader.getTileBytes(z, x, y);
        if (raw == null) {
            tileOffsets.put(key, MISSING);
            return MISSING;
        }

        short[] elev = decodeTerrain(raw);
        if (elev == null) {
            tileOffsets.put(key, MISSING);
            return MISSING;
        }

        // Check if entire tile is at or below sea level — skip storing it
        boolean allSeaLevel = true;
        for (short s : elev) {
            if (s > 0) {
                allSeaLevel = false;
                break;
            }
        }
        if (allSeaLevel) {
            tileOffsets.put(key, SEA_LEVEL);
            return SEA_LEVEL;
        }

        long off = nextOffset;
        long byteLen = (long) elev.length * 2;
        tileData.ensureCapacity(off + byteLen);

        // TODO necessary? convert short[] to byte[]
        byte[] bytes = new byte[(int) byteLen];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(elev);
        tileData.setBytes(off, bytes, bytes.length);

        tileOffsets.put(key, off);
        nextOffset += byteLen;
        return off;
    }

    short[] decodeTerrain(byte[] imageBytes) throws IOException {
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

        short[] elev = new short[h * w];
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
                // Clamp to short range which covers -32768m to +32767m, plenty for Earth
                elev[y * w + x] = (short) Math.max(-32768, Math.min(32767, Math.round(e)));
            }
        }
        return elev;
    }
}
