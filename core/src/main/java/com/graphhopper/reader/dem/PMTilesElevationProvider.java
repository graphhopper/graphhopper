package com.graphhopper.reader.dem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GraphHopper ElevationProvider that reads elevation data directly from a
 * PMTiles v3 archive containing terrain-RGB encoded tiles.
 * Not thread-safe due to the LinkedHashMap caches.
 */
public class PMTilesElevationProvider implements ElevationProvider {

    public enum TerrainEncoding {MAPBOX, TERRARIUM}

    private final TerrainEncoding encoding;
    private final boolean interpolate;
    private final int preferredZoom;

    private final PMTilesReader reader = new PMTilesReader();

    // Increasing further than this could mean hundreds of MB more are required (for zoom=11).
    // But decreasing means it will be much slower (10 million nodes will take >30s instead of 3).
    private static final int CACHE_SIZE = 4096;
    // LongObjectHashMap and ConcurrentHashMap are slower
    private final Map<Long, short[]> tileCache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, short[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private int tileSize;

    /**
     * @param preferredZoom 10 means ~76m at equator and ~49m in Germany (default).
     *                      11 means ~38m at equator and ~25m in Germany.
     *                      12 means ~19m at equator and ~12m in Germany.
     */
    public PMTilesElevationProvider(String filePath, TerrainEncoding encoding,
                                    boolean interpolate, int preferredZoom) {
        this.encoding = encoding;
        this.interpolate = interpolate;
        this.preferredZoom = preferredZoom;
        try {
            reader.open(filePath);
            reader.checkWebPSupport();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        tileCache.clear();
        reader.close();
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

        short[] elev = getDecodedTile(zoom, tileX, tileY);
        if (elev == null) return Double.NaN;

        int w = tileSize, h = tileSize;
        double px = (xTileD - tileX) * (w - 1);
        double py = (yTileD - tileY) * (h - 1);

        if (interpolate) {
            int x0 = Math.max(0, Math.min(w - 2, (int) Math.floor(px)));
            int y0 = Math.max(0, Math.min(h - 2, (int) Math.floor(py)));
            double fx = px - x0, fy = py - y0;
            double v00 = elev[y0 * w + x0];
            double v10 = elev[y0 * w + Math.min(x0 + 1, w - 1)];
            double v01 = elev[Math.min(y0 + 1, h - 1) * w + x0];
            double v11 = elev[Math.min(y0 + 1, h - 1) * w + Math.min(x0 + 1, w - 1)];
            return v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy)
                    + v01 * (1 - fx) * fy + v11 * fx * fy;
        } else {
            int ix = Math.max(0, Math.min(w - 1, (int) Math.round(px)));
            int iy = Math.max(0, Math.min(h - 1, (int) Math.round(py)));
            return elev[iy * w + ix];
        }
    }

    // =========================================================================
    // Tile decode + cache
    // =========================================================================

    private short[] getDecodedTile(int z, int x, int y) throws IOException {
        // cheap alternative to new TileKey(z, x, y) that avoids object allocation and GC pressure on every cache lookup.
        long key = ((long) z << 50) | ((long) x << 25) | y;
        short[] cached = tileCache.get(key);
        if (cached != null) return cached;

        byte[] tileData = reader.getTileBytes(z, x, y);
        if (tileData == null) return null;

        short[] elev = decodeTerrain(tileData);
        if (elev != null) {
            tileCache.put(key, elev);
        }
        return elev;
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
