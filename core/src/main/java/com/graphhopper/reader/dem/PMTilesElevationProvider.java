package com.graphhopper.reader.dem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * GraphHopper ElevationProvider that reads elevation data directly from a
 * PMTiles v3 archive containing terrain-RGB encoded PNG tiles.
 *
 * <h3>Supported terrain-RGB encodings:</h3>
 * <ul>
 *   <li><b>Mapbox</b>: elevation = -10000 + (R×65536 + G×256 + B) × 0.1</li>
 *   <li><b>Terrarium</b>: elevation = (R×256 + G + B/256) - 32768</li>
 * </ul>
 *
 * <h3>How it works:</h3>
 * <ol>
 *   <li>Parses the 127-byte PMTiles v3 binary header</li>
 *   <li>Reads and decompresses varint-encoded tile directories</li>
 *   <li>Converts lat/lon → Web Mercator tile z/x/y → Hilbert tile ID</li>
 *   <li>Binary-searches the directory for the tile entry</li>
 *   <li>Reads and decodes the terrain-RGB PNG tile</li>
 *   <li>Samples the pixel at the sub-tile position to get elevation</li>
 * </ol>
 */
public class PMTilesElevationProvider implements ElevationProvider {

    public enum TerrainEncoding { MAPBOX, TERRARIUM }

    // -- PMTiles v3 constants --
    private static final byte[] MAGIC = "PMTiles".getBytes();
    private static final int HEADER_LEN = 127;
    private static final int COMPRESS_NONE = 1;
    private static final int COMPRESS_GZIP = 2;
    // Tile types: 0=unknown, 1=mvt, 2=png, 3=jpeg, 4=webp, 5=avif

    // -- Configuration --
    private final String filePath;
    private final TerrainEncoding encoding;
    private final boolean interpolate;
    private final int preferredZoom;

    // -- Parsed state --
    private RandomAccessFile raf;
    private FileChannel channel;
    private PMTilesHeader header;
    private List<DirEntry> rootDir;

    // -- Tile cache (concurrent, no eviction needed at reasonable zoom levels) --
    private final ConcurrentHashMap<Long, short[]> tileCache = new ConcurrentHashMap<>();
    private int tileSize; // pixels per tile (detected from first decoded tile)

    /**
     * Create a provider with default settings (Terrarium encoding, bilinear interpolation,
     * zoom auto-detected from file).
     */
    public PMTilesElevationProvider(String filePath) {
        this(filePath, TerrainEncoding.TERRARIUM, true, -1);
    }

    /**
     * Create a provider with full control.
     *
     * @param filePath      path to the .pmtiles file
     * @param encoding      MAPBOX or TERRARIUM
     * @param interpolate   enable bilinear interpolation
     * @param preferredZoom zoom level to use (-1 = auto-select sensible zoom for routing)
     */
    public PMTilesElevationProvider(String filePath, TerrainEncoding encoding,
                                    boolean interpolate, int preferredZoom) {
        this.filePath = filePath;
        this.encoding = encoding;
        this.interpolate = interpolate;
        this.preferredZoom = preferredZoom;
    }

    // =========================================================================
    // ElevationProvider interface
    // =========================================================================

    @Override
    public double getEle(double lat, double lon) {
        try {
            ensureOpen();
            // Auto-select zoom: use preferredZoom if set, otherwise cap at 10.
            // Zoom 10 with 512px tiles ≈ 19m resolution — plenty for routing.
            // Zoom 12 would need 16× more tiles for marginal benefit.
            int zoom = preferredZoom > 0 ? preferredZoom : Math.min(header.maxZoom, 10);
            return sampleElevation(lat, lon, zoom);
        } catch (Exception e) {
            System.err.println("PMTilesElevationProvider.getEle(" + lat + ", " + lon + ") failed: " + e.getMessage());
            return Double.NaN;
        }
    }

    // =========================================================================
    // Diagnostic entry point — run this standalone to debug tile reading
    // =========================================================================

    /**
     * Run: java PMTilesElevationProvider /path/to/file.pmtiles [lat lon]
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: PMTilesElevationProvider <file.pmtiles> [lat lon]");
            return;
        }
        String path = args[0];
        double testLat = args.length >= 3 ? Double.parseDouble(args[1]) : 0;
        double testLon = args.length >= 3 ? Double.parseDouble(args[2]) : 0;

        System.out.println("=== PMTiles Diagnostic ===");
        System.out.println("File: " + path);
        System.out.println("File size: " + new File(path).length() + " bytes");

        // Check available ImageIO readers
        String[] formats = javax.imageio.ImageIO.getReaderFormatNames();
        System.out.println("\nImageIO supported formats: " + String.join(", ", formats));
        boolean hasWebP = false;
        for (String f : formats) if (f.equalsIgnoreCase("webp")) hasWebP = true;
        System.out.println("WebP support: " + (hasWebP ? "YES" : "NO — add com.github.usefulness:webp-imageio:0.8.1 to classpath!"));

        PMTilesElevationProvider provider = new PMTilesElevationProvider(path, TerrainEncoding.TERRARIUM, true, -1);
        provider.ensureOpen();

        PMTilesHeader h = provider.header;
        System.out.println("\n--- Header ---");
        System.out.println("Version:         " + h.version);
        System.out.println("Tile type:       " + h.tileType + " (1=mvt, 2=png, 3=jpeg, 4=webp, 5=avif)");
        System.out.println("Tile compress:   " + h.tileCompression + " (1=none, 2=gzip, 3=brotli, 4=zstd)");
        System.out.println("Internal comp:   " + h.internalCompression);
        System.out.println("Zoom:            " + h.minZoom + " – " + h.maxZoom);
        System.out.println("Bounds:          lon=[" + h.minLonE7/1e7 + ", " + h.maxLonE7/1e7 +
                "], lat=[" + h.minLatE7/1e7 + ", " + h.maxLatE7/1e7 + "]");
        System.out.println("Root dir entries: " + (provider.rootDir != null ? provider.rootDir.size() : "null"));

        if (provider.rootDir != null && !provider.rootDir.isEmpty()) {
            System.out.println("\n--- Root directory (first 10 entries) ---");
            for (int i = 0; i < Math.min(10, provider.rootDir.size()); i++) {
                DirEntry e = provider.rootDir.get(i);
                System.out.printf("  [%d] tileId=%d runLength=%d offset=%d length=%d%n",
                        i, e.tileId, e.runLength, e.offset, e.length);
            }
        }

        // Use center of bounds if no test point given
        if (args.length < 3) {
            testLat = (h.minLatE7 + h.maxLatE7) / 2.0 / 1e7;
            testLon = (h.minLonE7 + h.maxLonE7) / 2.0 / 1e7;
        }
        System.out.println("\n--- Tile lookup test at lat=" + testLat + ", lon=" + testLon + " ---");

        int zoom = h.maxZoom;
        int n = 1 << zoom;
        int tileX = (int) ((testLon + 180.0) / 360.0 * n);
        double latRad = Math.toRadians(testLat);
        int tileY = (int) ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);
        tileX = Math.max(0, Math.min(n - 1, tileX));
        tileY = Math.max(0, Math.min(n - 1, tileY));

        long tileId = zxyToTileId(zoom, tileX, tileY);
        System.out.println("z=" + zoom + " x=" + tileX + " y=" + tileY + " → tileId=" + tileId);

        // Try to get the tile bytes
        byte[] tileBytes = provider.getTileBytes(zoom, tileX, tileY);
        if (tileBytes == null) {
            System.out.println("FAIL: getTileBytes returned null — tile not found in directory");

            // Debug: try a few different zoom levels
            System.out.println("\nTrying all zoom levels...");
            for (int z = h.minZoom; z <= h.maxZoom; z++) {
                int nn = 1 << z;
                int tx = (int) ((testLon + 180.0) / 360.0 * nn);
                int ty = (int) ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * nn);
                tx = Math.max(0, Math.min(nn - 1, tx));
                ty = Math.max(0, Math.min(nn - 1, ty));
                long tid = zxyToTileId(z, tx, ty);
                byte[] tb = provider.getTileBytes(z, tx, ty);
                System.out.printf("  z=%2d x=%5d y=%5d tileId=%10d → %s%n",
                        z, tx, ty, tid, tb != null ? tb.length + " bytes" : "null");
            }
            return;
        }

        System.out.println("Tile bytes: " + tileBytes.length + " bytes");
        System.out.print("First 12 bytes: ");
        for (int i = 0; i < Math.min(12, tileBytes.length); i++) {
            System.out.printf("%02X ", tileBytes[i] & 0xFF);
        }
        System.out.println();

        // Try to decode
        try {
            short[] elev = provider.decodeTerrain(tileBytes);
            if (elev == null) {
                System.out.println("FAIL: decodeTerrain returned null — ImageIO could not decode the tile");
            } else {
                int s = provider.tileSize;
                System.out.println("Decoded: " + s + "×" + s + " pixels");
                int cx = s / 2, cy = s / 2;
                System.out.println("Center pixel elevation: " + elev[cy * s + cx] + "m");

                short min = Short.MAX_VALUE, max = Short.MIN_VALUE;
                for (short v : elev) { if (v < min) min = v; if (v > max) max = v; }
                System.out.println("Elevation range: [" + min + ", " + max + "]m");
            }
        } catch (Exception e) {
            System.out.println("FAIL: decodeTerrain threw: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        // Try getEle
        double ele = provider.getEle(testLat, testLon);
        System.out.println("\ngetEle(" + testLat + ", " + testLon + ") = " + ele + "m");

        provider.release();
    }

    @Override
    public boolean canInterpolate() {
        return interpolate;
    }

    @Override
    public void release() {
        tileCache.clear();
        rootDir = null;
        try {
            if (channel != null) channel.close();
            if (raf != null) raf.close();
        } catch (IOException ignored) {}
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
            return bilinear(elev, px, py, w, h);
        } else {
            int ix = Math.max(0, Math.min(w - 1, (int) Math.round(px)));
            int iy = Math.max(0, Math.min(h - 1, (int) Math.round(py)));
            return elev[iy * w + ix];
        }
    }

    private double bilinear(short[] elev, double px, double py, int w, int h) {
        int x0 = Math.max(0, Math.min(w - 2, (int) Math.floor(px)));
        int y0 = Math.max(0, Math.min(h - 2, (int) Math.floor(py)));
        double fx = px - x0;
        double fy = py - y0;
        double v00 = elev[y0 * w + x0];
        double v10 = elev[y0 * w + Math.min(x0 + 1, w - 1)];
        double v01 = elev[Math.min(y0 + 1, h - 1) * w + x0];
        double v11 = elev[Math.min(y0 + 1, h - 1) * w + Math.min(x0 + 1, w - 1)];
        return v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy)
                + v01 * (1 - fx) * fy + v11 * fx * fy;
    }

    // =========================================================================
    // Tile fetch + decode
    // =========================================================================

    private short[] getDecodedTile(int z, int x, int y) throws IOException {
        long key = ((long) z << 50) | ((long) x << 25) | y;
        short[] cached = tileCache.get(key);
        if (cached != null) return cached;

        byte[] tileData = getTileBytes(z, x, y);
        if (tileData == null) return null;

        short[] elev = decodeTerrain(tileData);
        if (elev != null) {
            tileCache.put(key, elev);
        }
        return elev;
    }

    private short[] decodeTerrain(byte[] imageBytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (img == null) {
            // Check if it's a WebP file (RIFF....WEBP magic)
            if (imageBytes.length > 12 && imageBytes[0] == 'R' && imageBytes[1] == 'I'
                    && imageBytes[2] == 'F' && imageBytes[3] == 'F'
                    && imageBytes[8] == 'W' && imageBytes[9] == 'E'
                    && imageBytes[10] == 'B' && imageBytes[11] == 'P') {
                throw new IOException(
                        "Tile is WebP format but no WebP ImageIO plugin found. " +
                                "Add com.github.usefulness:webp-imageio:0.8.1 to your classpath.");
            }
            return null;
        }

        int w = img.getWidth();
        int h = img.getHeight();
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
                // Clamp to short range — covers -32768m to +32767m, plenty for Earth
                elev[y * w + x] = (short) Math.max(-32768, Math.min(32767, Math.round(e)));
            }
        }
        return elev;
    }

    // =========================================================================
    // PMTiles v3 reading
    // =========================================================================

    private void ensureOpen() throws IOException {
        if (header != null) return;
        javax.imageio.ImageIO.scanForPlugins(); // ensure WebP plugin is discovered
        raf = new RandomAccessFile(filePath, "r");
        channel = raf.getChannel();
        header = readHeader();
        rootDir = readDirectory(header.rootDirOffset, header.rootDirLength);

        // Warn early if tiles are WebP but no decoder is available
        if (header.tileType == 4) { // 4 = WebP in PMTiles spec
            boolean hasWebP = false;
            for (String f : javax.imageio.ImageIO.getReaderFormatNames())
                if (f.equalsIgnoreCase("webp")) { hasWebP = true; break; }
            if (!hasWebP) throw new IOException(
                    "PMTiles contains WebP tiles but no WebP ImageIO plugin found. " +
                            "Add com.github.usefulness:webp-imageio:0.10.2 to your classpath.");
        }
    }

    private byte[] getTileBytes(int z, int x, int y) throws IOException {
        long tileId = zxyToTileId(z, x, y);
        return findTile(tileId, rootDir, 0);
    }

    private byte[] findTile(long tileId, List<DirEntry> dir, int depth) throws IOException {
        if (dir == null || dir.isEmpty() || depth > 5) return null;

        // Binary search
        int lo = 0, hi = dir.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            DirEntry e = dir.get(mid);
            if (tileId < e.tileId) {
                hi = mid - 1;
            } else if (tileId >= e.tileId + e.runLength && e.runLength > 0) {
                lo = mid + 1;
            } else {
                // Found — is it a tile or a leaf directory?
                if (e.runLength > 0) {
                    // It's tile data
                    long offset = header.tileDataOffset + e.offset;
                    return readBytes(offset, (int) e.length);
                } else {
                    // It's a leaf directory reference (runLength == 0)
                    long leafOffset = header.leafDirsOffset + e.offset;
                    List<DirEntry> leafDir = readDirectory(leafOffset, e.length);
                    return findTile(tileId, leafDir, depth + 1);
                }
            }
        }

        // Check if tileId falls within the run of an earlier entry
        for (int i = dir.size() - 1; i >= 0; i--) {
            DirEntry e = dir.get(i);
            if (e.tileId <= tileId && e.runLength > 0 && tileId < e.tileId + e.runLength) {
                long offset = header.tileDataOffset + e.offset;
                return readBytes(offset, (int) e.length);
            }
            if (e.tileId < tileId && e.runLength == 0) {
                // Leaf directory
                long leafOffset = header.leafDirsOffset + e.offset;
                List<DirEntry> leafDir = readDirectory(leafOffset, e.length);
                return findTile(tileId, leafDir, depth + 1);
            }
            if (e.tileId < tileId) break;
        }

        return null;
    }

    // =========================================================================
    // PMTiles v3 header parsing (127 bytes, little-endian)
    // =========================================================================

    private PMTilesHeader readHeader() throws IOException {
        byte[] buf = readBytes(0, HEADER_LEN);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

        // Validate magic
        byte[] magic = new byte[7];
        bb.get(magic);
        if (!Arrays.equals(magic, MAGIC))
            throw new IOException("Not a PMTiles file");

        PMTilesHeader h = new PMTilesHeader();
        h.version = bb.get() & 0xFF;
        if (h.version != 3)
            throw new IOException("Only PMTiles v3 supported, got v" + h.version);

        h.rootDirOffset = bb.getLong();
        h.rootDirLength = bb.getLong();
        h.metadataOffset = bb.getLong();
        h.metadataLength = bb.getLong();
        h.leafDirsOffset = bb.getLong();
        h.leafDirsLength = bb.getLong();
        h.tileDataOffset = bb.getLong();
        h.tileDataLength = bb.getLong();
        h.numAddressedTiles = bb.getLong();
        h.numTileEntries = bb.getLong();
        h.numTileContents = bb.getLong();

        h.clustered = (bb.get() & 0xFF) == 1;
        h.internalCompression = bb.get() & 0xFF;
        h.tileCompression = bb.get() & 0xFF;
        h.tileType = bb.get() & 0xFF;
        h.minZoom = bb.get() & 0xFF;
        h.maxZoom = bb.get() & 0xFF;

        // Bounds (stored as E7 integers)
        h.minLonE7 = bb.getInt();
        h.minLatE7 = bb.getInt();
        h.maxLonE7 = bb.getInt();
        h.maxLatE7 = bb.getInt();

        // Center
        h.centerZoom = bb.get() & 0xFF;
        h.centerLonE7 = bb.getInt();
        h.centerLatE7 = bb.getInt();

        return h;
    }

    // =========================================================================
    // Directory parsing (varint-encoded entries, possibly gzip-compressed)
    // =========================================================================

    private List<DirEntry> readDirectory(long offset, long length) throws IOException {
        byte[] raw = readBytes(offset, (int) length);

        // Decompress if needed
        if (header.internalCompression == COMPRESS_GZIP) {
            try {
                raw = gunzip(raw);
            } catch (IOException e) {
                // Might not be compressed, try as-is
            }
        }

        return deserializeEntries(raw);
    }

    /**
     * Deserialize a PMTiles v3 directory.
     *
     * Layout: numEntries as varint, then columns of varints:
     *   - tileId deltas
     *   - runLengths
     *   - lengths
     *   - offsets (with delta encoding for clustered archives)
     */
    private List<DirEntry> deserializeEntries(byte[] data) throws IOException {
        VarintReader r = new VarintReader(data);

        int numEntries = (int) r.readVarint();
        if (numEntries == 0) return Collections.emptyList();

        // Column 1: tile_id (delta-encoded)
        long[] tileIds = new long[numEntries];
        long lastId = 0;
        for (int i = 0; i < numEntries; i++) {
            lastId += r.readVarint();
            tileIds[i] = lastId;
        }

        // Column 2: run_length
        long[] runLengths = new long[numEntries];
        for (int i = 0; i < numEntries; i++) {
            runLengths[i] = r.readVarint();
        }

        // Column 3: length
        long[] lengths = new long[numEntries];
        for (int i = 0; i < numEntries; i++) {
            lengths[i] = r.readVarint();
        }

        // Column 4: offset (delta-encoded for clustered tiles with offset=0 entries)
        long[] offsets = new long[numEntries];
        for (int i = 0; i < numEntries; i++) {
            long v = r.readVarint();
            if (v == 0 && i > 0) {
                // Delta: offset = prev_offset + prev_length
                offsets[i] = offsets[i - 1] + lengths[i - 1];
            } else {
                offsets[i] = v - 1;  // offset is stored as value+1 to distinguish from 0
            }
        }

        List<DirEntry> entries = new ArrayList<>(numEntries);
        for (int i = 0; i < numEntries; i++) {
            entries.add(new DirEntry(tileIds[i], runLengths[i], offsets[i], lengths[i]));
        }
        return entries;
    }

    // =========================================================================
    // Hilbert curve: Z/X/Y → TileID
    // =========================================================================

    /**
     * Convert z/x/y tile coordinates to a PMTiles v3 Hilbert tile ID.
     *
     * The tile ID space is a pyramid: zoom 0 starts at ID 0, zoom 1 at ID 1,
     * zoom 2 at ID 5, etc. Within each zoom level, tiles are ordered along a
     * Hilbert space-filling curve.
     */
    static long zxyToTileId(int z, int x, int y) {
        if (z == 0) return 0;

        // Base offset for this zoom level: sum of 4^i for i = 0..z-1
        // = (4^z - 1) / 3
        long base = 0;
        for (int i = 0; i < z; i++) {
            base += (1L << (2 * i));
        }

        // Hilbert d value for (x, y) in a 2^z × 2^z grid
        long d = xyToHilbertD(z, x, y);
        return base + d;
    }

    /**
     * Convert (x, y) in a 2^order grid to a Hilbert curve distance.
     * Standard algorithm from Wikipedia / Hacker's Delight.
     */
    private static long xyToHilbertD(int order, long x, long y) {
        long d = 0;
        for (int s = (1 << (order - 1)); s > 0; s >>= 1) {
            long rx = (x & s) > 0 ? 1 : 0;
            long ry = (y & s) > 0 ? 1 : 0;
            d += s * (long) s * ((3 * rx) ^ ry);

            // Rotate
            if (ry == 0) {
                if (rx == 1) {
                    x = s - 1 - x;
                    y = s - 1 - y;
                }
                long t = x;
                x = y;
                y = t;
            }
        }
        return d;
    }

    // =========================================================================
    // Low-level I/O helpers
    // =========================================================================

    private byte[] readBytes(long offset, int length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        int read = 0;
        while (read < length) {
            int n = channel.read(buf, offset + read);
            if (n < 0) break;
            read += n;
        }
        return buf.array();
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 4)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gis.read(buf)) >= 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    // =========================================================================
    // Internal types
    // =========================================================================

    private static class PMTilesHeader {
        int version;
        long rootDirOffset, rootDirLength;
        long metadataOffset, metadataLength;
        long leafDirsOffset, leafDirsLength;
        long tileDataOffset, tileDataLength;
        long numAddressedTiles, numTileEntries, numTileContents;
        boolean clustered;
        int internalCompression, tileCompression, tileType;
        int minZoom, maxZoom;
        int minLonE7, minLatE7, maxLonE7, maxLatE7;
        int centerZoom, centerLonE7, centerLatE7;
    }

    private static class DirEntry {
        final long tileId;
        final long runLength;
        final long offset;
        final long length;

        DirEntry(long tileId, long runLength, long offset, long length) {
            this.tileId = tileId;
            this.runLength = runLength;
            this.offset = offset;
            this.length = length;
        }
    }

    /** Reads unsigned LEB128 varints from a byte array. */
    private static class VarintReader {
        private final byte[] data;
        private int pos;

        VarintReader(byte[] data) {
            this.data = data;
            this.pos = 0;
        }

        long readVarint() {
            long result = 0;
            int shift = 0;
            while (pos < data.length) {
                int b = data[pos++] & 0xFF;
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            return result;
        }
    }
}
