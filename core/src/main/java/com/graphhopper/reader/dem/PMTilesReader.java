package com.graphhopper.reader.dem;

import javax.imageio.ImageIO;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Low-level PMTiles v3 archive reader. Handles header parsing, directory
 * deserialization, Hilbert curve tile ID mapping, and raw tile byte retrieval.
 */
class PMTilesReader implements Closeable {

    static final int HEADER_LEN = 127;
    static final int COMPRESS_GZIP = 2;

    private static final int LEAF_CACHE_SIZE = 64;

    private RandomAccessFile raf;
    private FileChannel channel;
    Header header;
    List<DirEntry> rootDir;
    private final Map<Long, List<DirEntry>> leafCache = new LinkedHashMap<>(LEAF_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, List<DirEntry>> eldest) {
            return size() > LEAF_CACHE_SIZE;
        }
    };

    void open(String filePath) throws IOException {
        if (header != null) return;
        ImageIO.scanForPlugins();
        raf = new RandomAccessFile(filePath, "r");
        channel = raf.getChannel();
        header = readHeader();
        rootDir = readDirectory(header.rootDirOffset, header.rootDirLength);
    }

    @Override
    public void close() {
        rootDir = null;
        header = null;
        leafCache.clear();
        try {
            if (channel != null) channel.close();
            if (raf != null) raf.close();
        } catch (IOException ignored) {}
    }

    void checkWebPSupport() throws IOException {
        if (header.tileType == 4) {
            boolean hasWebP = false;
            for (String f : ImageIO.getReaderFormatNames())
                if (f.equalsIgnoreCase("webp")) { hasWebP = true; break; }
            if (!hasWebP) throw new IOException(
                    "PMTiles contains WebP tiles but no WebP ImageIO plugin found. " +
                            "Add com.github.usefulness:webp-imageio:0.10.2 to your classpath.");
        }
    }

    // =========================================================================
    // Tile retrieval
    // =========================================================================

    byte[] getTileBytes(int z, int x, int y) throws IOException {
        return findTile(zxyToTileId(z, x, y), rootDir, 0);
    }

    private byte[] findTile(long tileId, List<DirEntry> dir, int depth) throws IOException {
        if (dir == null || dir.isEmpty() || depth > 5) return null;

        int lo = 0, hi = dir.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            DirEntry e = dir.get(mid);
            if (tileId < e.tileId) {
                hi = mid - 1;
            } else if (e.runLength > 0 && tileId >= e.tileId + e.runLength) {
                lo = mid + 1;
            } else {
                if (e.runLength > 0) {
                    return readBytes(header.tileDataOffset + e.offset, (int) e.length);
                } else {
                    List<DirEntry> leafDir = readLeafDirectory(e.offset, e.length);
                    return findTile(tileId, leafDir, depth + 1);
                }
            }
        }

        for (int i = dir.size() - 1; i >= 0; i--) {
            DirEntry e = dir.get(i);
            if (e.tileId <= tileId && e.runLength > 0 && tileId < e.tileId + e.runLength) {
                return readBytes(header.tileDataOffset + e.offset, (int) e.length);
            }
            if (e.tileId < tileId && e.runLength == 0) {
                List<DirEntry> leafDir = readLeafDirectory(e.offset, e.length);
                return findTile(tileId, leafDir, depth + 1);
            }
            if (e.tileId < tileId) break;
        }
        return null;
    }

    // =========================================================================
    // Hilbert curve: Z/X/Y <-> TileID
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
                long[] xy = hilbertDToXY(z, tileId - acc);
                return new int[]{z, (int) xy[0], (int) xy[1]};
            }
            acc += numTiles;
            z++;
        }
    }

    private static long xyToHilbertD(int order, long x, long y) {
        long d = 0;
        for (int s = (1 << (order - 1)); s > 0; s >>= 1) {
            long rx = (x & s) > 0 ? 1 : 0;
            long ry = (y & s) > 0 ? 1 : 0;
            d += s * (long) s * ((3 * rx) ^ ry);
            if (ry == 0) {
                if (rx == 1) { x = s - 1 - x; y = s - 1 - y; }
                long t = x; x = y; y = t;
            }
        }
        return d;
    }

    private static long[] hilbertDToXY(int order, long d) {
        long x = 0, y = 0;
        for (long s = 1; s < (1L << order); s <<= 1) {
            long rx = (d / 2) & 1;
            long ry = (d ^ rx) & 1;
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
    // Header parsing
    // =========================================================================

    private Header readHeader() throws IOException {
        byte[] buf = readBytes(0, HEADER_LEN);
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

        byte[] magic = new byte[7];
        bb.get(magic);
        if (!Arrays.equals(magic, "PMTiles".getBytes()))
            throw new IOException("Not a PMTiles file");

        Header h = new Header();
        h.version = bb.get() & 0xFF;
        if (h.version != 3)
            throw new IOException("Only PMTiles v3 supported, got v" + h.version);

        h.rootDirOffset = bb.getLong();   h.rootDirLength = bb.getLong();
        h.metadataOffset = bb.getLong();  h.metadataLength = bb.getLong();
        h.leafDirsOffset = bb.getLong();  h.leafDirsLength = bb.getLong();
        h.tileDataOffset = bb.getLong();  h.tileDataLength = bb.getLong();
        h.numAddressedTiles = bb.getLong();
        h.numTileEntries = bb.getLong();
        h.numTileContents = bb.getLong();

        h.clustered = (bb.get() & 0xFF) == 1;
        h.internalCompression = bb.get() & 0xFF;
        h.tileCompression = bb.get() & 0xFF;
        h.tileType = bb.get() & 0xFF;
        h.minZoom = bb.get() & 0xFF;
        h.maxZoom = bb.get() & 0xFF;
        h.minLonE7 = bb.getInt();  h.minLatE7 = bb.getInt();
        h.maxLonE7 = bb.getInt();  h.maxLatE7 = bb.getInt();
        h.centerZoom = bb.get() & 0xFF;
        h.centerLonE7 = bb.getInt();
        h.centerLatE7 = bb.getInt();
        return h;
    }

    // =========================================================================
    // Directory parsing
    // =========================================================================

    private List<DirEntry> readLeafDirectory(long offset, long length) throws IOException {
        List<DirEntry> cached = leafCache.get(offset);
        if (cached != null) return cached;
        List<DirEntry> entries = readDirectory(header.leafDirsOffset + offset, length);
        leafCache.put(offset, entries);
        return entries;
    }

    private List<DirEntry> readDirectory(long offset, long length) throws IOException {
        byte[] raw = readBytes(offset, (int) length);
        if (header.internalCompression == COMPRESS_GZIP) {
            try { raw = gunzip(raw); } catch (IOException e) { /* try as-is */ }
        }
        return deserializeEntries(raw);
    }

    private static List<DirEntry> deserializeEntries(byte[] data) {
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
            if (v == 0 && i > 0) offsets[i] = offsets[i - 1] + lengths[i - 1];
            else offsets[i] = v - 1;
        }

        List<DirEntry> entries = new ArrayList<>(numEntries);
        for (int i = 0; i < numEntries; i++)
            entries.add(new DirEntry(tileIds[i], runLengths[i], offsets[i], lengths[i]));
        return entries;
    }

    // =========================================================================
    // I/O helpers
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

    static byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 4)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gis.read(buf)) >= 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static long readVarint(byte[] data, int[] pos) {
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

    // =========================================================================
    // Internal types
    // =========================================================================

    static class Header {
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

    static class DirEntry {
        final long tileId, runLength, offset, length;
        DirEntry(long tileId, long runLength, long offset, long length) {
            this.tileId = tileId; this.runLength = runLength;
            this.offset = offset; this.length = length;
        }
    }
}
