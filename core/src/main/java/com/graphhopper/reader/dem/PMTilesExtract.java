package com.graphhopper.reader.dem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Extracts tiles from a PMTiles v3 file and saves each as a grayscale elevation PNG.
 * Usage: java PMTilesExtract de.pmtiles ./tiles_out [zoom]
 * Creates files like: tiles_out/z6_x33_y21.png (grayscale: black=low, white=high)
 */
public class PMTilesExtract {

    private static final int HEADER_LEN = 127;
    private static final int COMPRESS_GZIP = 2;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: PMTilesExtract <file.pmtiles> <output_dir> [zoom]");
            System.out.println("  If zoom is omitted, extracts one tile per zoom level at the center.");
            System.out.println("  If zoom is given, extracts ALL tiles at that zoom level.");
            return;
        }

        String pmtilesPath = args[0];
        File outDir = new File(args[1]);
        outDir.mkdirs();
        int requestedZoom = args.length >= 3 ? Integer.parseInt(args[2]) : -1;

        // Force ImageIO to discover plugins (TwelveMonkeys registers via SPI)
        ImageIO.scanForPlugins();

        // Check ImageIO support
        String[] formats = ImageIO.getReaderFormatNames();
        System.out.println("ImageIO formats: " + String.join(", ", formats));
        boolean hasWebP = false;
        for (String f : formats) if (f.equalsIgnoreCase("webp")) hasWebP = true;
        if (!hasWebP) {
            System.err.println("\nERROR: No WebP support in ImageIO!");
            System.err.println("Your PMTiles file likely contains WebP tiles. Add to pom.xml:");
            System.err.println("");
            System.err.println("  <dependency>");
            System.err.println("      <groupId>com.github.usefulness</groupId>");
            System.err.println("      <artifactId>webp-imageio</artifactId>");
            System.err.println("      <version>0.8.1</version>");
            System.err.println("  </dependency>");
            System.exit(1);
        }

        try (RandomAccessFile raf = new RandomAccessFile(pmtilesPath, "r");
             FileChannel ch = raf.getChannel()) {

            // --- Parse header ---
            byte[] hdrBuf = readBytes(ch, 0, HEADER_LEN);
            ByteBuffer bb = ByteBuffer.wrap(hdrBuf).order(ByteOrder.LITTLE_ENDIAN);

            byte[] magic = new byte[7];
            bb.get(magic);
            int version = bb.get() & 0xFF;
            System.out.println("Magic: " + new String(magic) + "  Version: " + version);

            long rootDirOffset = bb.getLong();
            long rootDirLength = bb.getLong();
            long metadataOffset = bb.getLong();
            long metadataLength = bb.getLong();
            long leafDirsOffset = bb.getLong();
            long leafDirsLength = bb.getLong();
            long tileDataOffset = bb.getLong();
            long tileDataLength = bb.getLong();
            long numAddressed = bb.getLong();
            long numEntries = bb.getLong();
            long numContents = bb.getLong();

            boolean clustered = (bb.get() & 0xFF) == 1;
            int internalComp = bb.get() & 0xFF;
            int tileComp = bb.get() & 0xFF;
            int tileType = bb.get() & 0xFF;
            int minZoom = bb.get() & 0xFF;
            int maxZoom = bb.get() & 0xFF;
            int minLonE7 = bb.getInt();
            int minLatE7 = bb.getInt();
            int maxLonE7 = bb.getInt();
            int maxLatE7 = bb.getInt();

            String[] typeNames = {"unknown", "mvt", "png", "jpeg", "webp", "avif"};
            String[] compNames = {"unknown", "none", "gzip", "brotli", "zstd"};
            System.out.println("Tile type: " + typeNames[Math.min(tileType, typeNames.length - 1)]);
            System.out.println("Tile compression: " + compNames[Math.min(tileComp, compNames.length - 1)]);
            System.out.println("Internal compression: " + compNames[Math.min(internalComp, compNames.length - 1)]);
            System.out.println("Zoom: " + minZoom + " – " + maxZoom);
            System.out.printf("Bounds: lon=[%.4f, %.4f] lat=[%.4f, %.4f]%n",
                    minLonE7 / 1e7, maxLonE7 / 1e7, minLatE7 / 1e7, maxLatE7 / 1e7);
            System.out.println("Tiles: " + numAddressed + " addressed, " + numEntries + " entries");

            double centerLon = (minLonE7 + maxLonE7) / 2.0 / 1e7;
            double centerLat = (minLatE7 + maxLatE7) / 2.0 / 1e7;

            // --- Read root directory ---
            byte[] rootRaw = readBytes(ch, rootDirOffset, (int) rootDirLength);
            if (internalComp == COMPRESS_GZIP) rootRaw = gunzip(rootRaw);
            List<long[]> rootDir = deserializeDirectory(rootRaw);
            System.out.println("Root directory: " + rootDir.size() + " entries");

            if (requestedZoom >= 0) {
                // Extract ALL tiles at this zoom level
                System.out.println("\nExtracting all tiles at zoom " + requestedZoom + "...");
                int count = extractAllAtZoom(ch, rootDir, requestedZoom, outDir,
                        tileDataOffset, leafDirsOffset, internalComp);
                System.out.println("Extracted " + count + " tiles to " + outDir);
            } else {
                // Extract one tile per zoom at the center
                System.out.println("\nExtracting center tile at each zoom level...");
                for (int z = minZoom; z <= maxZoom; z++) {
                    int n = 1 << z;
                    int tx = (int) ((centerLon + 180.0) / 360.0 * n);
                    double latRad = Math.toRadians(centerLat);
                    int ty = (int) ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);
                    tx = Math.max(0, Math.min(n - 1, tx));
                    ty = Math.max(0, Math.min(n - 1, ty));

                    long tileId = zxyToTileId(z, tx, ty);
                    byte[] data = findTile(ch, tileId, rootDir, tileDataOffset, leafDirsOffset, internalComp, 0);

                    if (data == null) {
                        System.out.printf("  z=%2d x=%5d y=%5d tileId=%10d → NOT FOUND%n", z, tx, ty, tileId);
                        continue;
                    }

                    // Try to decode as image, convert terrain-RGB to grayscale elevation, save as PNG
                    File outFile = new File(outDir, String.format("z%d_x%d_y%d.png", z, tx, ty));
                    BufferedImage img = decodeImage(data);
                    BufferedImage gray = terrainToGrayscale(img);
                    ImageIO.write(gray, "png", outFile);
                    System.out.printf("  z=%2d x=%5d y=%5d tileId=%10d → %s (%dx%d, %d bytes raw)%n",
                            z, tx, ty, tileId, outFile.getName(), img.getWidth(), img.getHeight(), data.length);
                }
            }
        }
    }

    // =========================================================================
    // Extract all tiles at a zoom level
    // =========================================================================

    static int extractAllAtZoom(FileChannel ch, List<long[]> rootDir, int zoom, File outDir,
                                long tileDataOffset, long leafDirsOffset, int internalComp) throws IOException {
        // Tile ID range for this zoom level
        long base = 0;
        for (int i = 0; i < zoom; i++) base += (1L << (2 * i));
        long count = 1L << (2 * zoom);
        long endId = base + count;

        System.out.printf("  TileId range for z=%d: [%d, %d) (%d tiles)%n", zoom, base, endId, count);

        int extracted = 0;
        // Walk through all tile IDs — for sparse datasets, try each
        // For efficiency with large zooms, scan the directory instead
        for (long tileId = base; tileId < endId; tileId++) {
            byte[] data = findTile(ch, tileId, rootDir, tileDataOffset, leafDirsOffset, internalComp, 0);
            if (data == null) continue;

            int[] zxy = tileIdToZxy(tileId);
            File outFile = new File(outDir, String.format("z%d_x%d_y%d.png", zxy[0], zxy[1], zxy[2]));

            BufferedImage img = decodeImage(data);
            BufferedImage gray = terrainToGrayscale(img);
            ImageIO.write(gray, "png", outFile);
            extracted++;
            if (extracted % 100 == 0) System.out.println("  ... " + extracted + " tiles extracted");
        }
        return extracted;
    }

    // =========================================================================
    // Terrain-RGB → Grayscale elevation
    // =========================================================================

    /**
     * Decode image bytes — tries ImageIO first, falls back to sejda WebP native decoder.
     * Supports: PNG, JPEG natively. WebP via TwelveMonkeys or sejda-webp.
     */
    static BufferedImage decodeImage(byte[] data) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img != null) return img;

        // Detect format for a helpful error
        String fmt = "unknown";
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') fmt = "WebP";
        else if (data.length > 4 && data[0] == (byte)0x89 && data[1] == 'P') fmt = "PNG";

        throw new IOException(fmt + " tile but ImageIO can't decode it (" + data.length + " bytes). "
                + "Add to pom.xml: <groupId>com.github.usefulness</groupId><artifactId>webp-imageio</artifactId><version>0.8.1</version>");
    }

    /**
     * Decode Terrarium-encoded terrain-RGB to grayscale elevation image.
     * Two-pass: first finds min/max elevation, then maps linearly to 0–65535.
     * Black = lowest, white = highest. Prints the range to stdout.
     */
    static BufferedImage terrainToGrayscale(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();

        // Pass 1: decode elevations, find min/max
        float[][] elev = new float[h][w];
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                float e = (float) ((r * 256.0 + g + b / 256.0) - 32768.0);
                elev[y][x] = e;
                if (e < min) min = e;
                if (e > max) max = e;
            }
        }

        System.out.printf("         elevation: min=%.1fm  max=%.1fm%n", min, max);

        // Pass 2: map to grayscale
        float range = max - min;
        if (range < 1) range = 1;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = (int) ((elev[y][x] - min) / range * 65535);
                v = Math.max(0, Math.min(65535, v));
                out.getRaster().setSample(x, y, 0, v);
            }
        }
        return out;
    }

    // =========================================================================
    // PMTiles directory parsing
    // =========================================================================

    /** Each entry: [tileId, runLength, offset, length] */
    static List<long[]> deserializeDirectory(byte[] data) {
        int[] pos = {0};
        int numEntries = (int) readVarint(data, pos);
        if (numEntries == 0) return Collections.emptyList();

        long[] tileIds = new long[numEntries];
        long lastId = 0;
        for (int i = 0; i < numEntries; i++) { lastId += readVarint(data, pos); tileIds[i] = lastId; }

        long[] runLengths = new long[numEntries];
        for (int i = 0; i < numEntries; i++) runLengths[i] = readVarint(data, pos);

        long[] lengths = new long[numEntries];
        for (int i = 0; i < numEntries; i++) lengths[i] = readVarint(data, pos);

        long[] offsets = new long[numEntries];
        for (int i = 0; i < numEntries; i++) {
            long v = readVarint(data, pos);
            if (v == 0 && i > 0) {
                offsets[i] = offsets[i - 1] + lengths[i - 1];
            } else {
                offsets[i] = v - 1;
            }
        }

        List<long[]> entries = new ArrayList<>(numEntries);
        for (int i = 0; i < numEntries; i++)
            entries.add(new long[]{tileIds[i], runLengths[i], offsets[i], lengths[i]});
        return entries;
    }

    static byte[] findTile(FileChannel ch, long tileId, List<long[]> dir,
                           long tileDataOffset, long leafDirsOffset, int internalComp, int depth) throws IOException {
        if (dir == null || dir.isEmpty() || depth > 5) return null;

        // Binary search for the entry containing tileId
        int lo = 0, hi = dir.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long[] e = dir.get(mid);
            long eTileId = e[0], eRunLen = e[1];
            if (eRunLen > 0 && tileId >= eTileId && tileId < eTileId + eRunLen) {
                found = mid;
                break;
            } else if (eRunLen == 0 && tileId >= eTileId) {
                // Could be a leaf — check if next entry's tileId is beyond
                if (mid + 1 < dir.size() && tileId >= dir.get(mid + 1)[0]) {
                    lo = mid + 1;
                } else {
                    found = mid;
                    break;
                }
            } else if (tileId < eTileId) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }

        if (found < 0) return null;

        long[] e = dir.get(found);
        long eTileId = e[0], eRunLen = e[1], eOffset = e[2], eLength = e[3];

        if (eRunLen > 0) {
            // Tile data
            return readBytes(ch, tileDataOffset + eOffset, (int) eLength);
        } else {
            // Leaf directory
            byte[] leafRaw = readBytes(ch, leafDirsOffset + eOffset, (int) eLength);
            if (internalComp == COMPRESS_GZIP) leafRaw = gunzip(leafRaw);
            List<long[]> leafDir = deserializeDirectory(leafRaw);
            return findTile(ch, tileId, leafDir, tileDataOffset, leafDirsOffset, internalComp, depth + 1);
        }
    }

    // =========================================================================
    // Hilbert TileID ↔ Z/X/Y
    // =========================================================================

    static long zxyToTileId(int z, int x, int y) {
        if (z == 0) return 0;
        long base = 0;
        for (int i = 0; i < z; i++) base += (1L << (2 * i));
        return base + xyToHilbertD(z, x, y);
    }

    static int[] tileIdToZxy(long tileId) {
        if (tileId == 0) return new int[]{0, 0, 0};
        long acc = 0;
        int z = 0;
        while (true) {
            long numTiles = 1L << (2 * z);
            if (acc + numTiles > tileId) {
                long d = tileId - acc;
                long[] xy = hilbertDToXY(z, d);
                return new int[]{z, (int) xy[0], (int) xy[1]};
            }
            acc += numTiles;
            z++;
        }
    }

    static long xyToHilbertD(int order, long x, long y) {
        long d = 0;
        for (long s = (1L << (order - 1)); s > 0; s >>= 1) {
            long rx = (x & s) > 0 ? 1 : 0;
            long ry = (y & s) > 0 ? 1 : 0;
            d += s * s * ((3 * rx) ^ ry);
            // Rotate
            if (ry == 0) {
                if (rx == 1) { x = s - 1 - x; y = s - 1 - y; }
                long t = x; x = y; y = t;
            }
        }
        return d;
    }

    static long[] hilbertDToXY(int order, long d) {
        long x = 0, y = 0;
        for (long s = 1; s < (1L << order); s <<= 1) {
            long rx = (d / 2) & 1;
            long ry = (d ^ rx) & 1;
            // Rotate
            if (ry == 0) {
                if (rx == 1) { x = s - 1 - x; y = s - 1 - y; }
                long t = x; x = y; y = t;
            }
            x += s * rx;
            y += s * ry;
            d >>= 2;
        }
        return new long[]{x, y};
    }

    // =========================================================================
    // I/O helpers
    // =========================================================================

    static byte[] readBytes(FileChannel ch, long offset, int length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        int read = 0;
        while (read < length) {
            int n = ch.read(buf, offset + read);
            if (n < 0) break;
            read += n;
        }
        return buf.array();
    }

    static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 4)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gis.read(buf)) >= 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    static long readVarint(byte[] data, int[] pos) {
        long result = 0;
        int shift = 0;
        while (pos[0] < data.length) {
            int b = data[pos[0]++] & 0xFF;
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return result;
    }

    static String hexDump(byte[] data, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(len, data.length); i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }
}
