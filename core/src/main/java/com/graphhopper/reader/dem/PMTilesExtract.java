package com.graphhopper.reader.dem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * Diagnostic tool: extracts tiles from a PMTiles v3 file and saves each
 * as a grayscale elevation PNG (black=low, white=high).
 *
 * <p>Usage: {@code java PMTilesExtract de.pmtiles ./tiles_out [zoom]}</p>
 */
class PMTilesExtract {

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

        PMTilesReader reader = new PMTilesReader();
        reader.open(pmtilesPath);
        reader.checkWebPSupport();
        PMTilesReader.Header h = reader.header;

        String[] typeNames = {"unknown", "mvt", "png", "jpeg", "webp", "avif"};
        System.out.println("Tile type: " + typeNames[Math.min(h.tileType, typeNames.length - 1)]);
        System.out.println("Zoom: " + h.minZoom + " - " + h.maxZoom);
        System.out.printf("Bounds: lon=[%.4f, %.4f] lat=[%.4f, %.4f]%n",
                h.minLonE7 / 1e7, h.maxLonE7 / 1e7, h.minLatE7 / 1e7, h.maxLatE7 / 1e7);
        System.out.println("Tiles: " + h.numAddressedTiles + " addressed, " + h.numTileEntries + " entries");
        System.out.println("Root directory: " + reader.rootDir.size() + " entries");

        if (requestedZoom >= 0) {
            System.out.println("\nExtracting all tiles at zoom " + requestedZoom + "...");
            int count = extractAllAtZoom(reader, requestedZoom, outDir);
            System.out.println("Extracted " + count + " tiles to " + outDir);
        } else {
            System.out.println("\nExtracting center tile at each zoom level...");
            double centerLon = (h.minLonE7 + h.maxLonE7) / 2.0 / 1e7;
            double centerLat = (h.minLatE7 + h.maxLatE7) / 2.0 / 1e7;
            extractCenterTiles(reader, centerLat, centerLon, h.minZoom, h.maxZoom, outDir);
        }

        reader.close();
    }

    private static void extractCenterTiles(PMTilesReader reader, double centerLat, double centerLon,
                                           int minZoom, int maxZoom, File outDir) throws IOException {
        for (int z = minZoom; z <= maxZoom; z++) {
            int n = 1 << z;
            int tx = (int) ((centerLon + 180.0) / 360.0 * n);
            double latRad = Math.toRadians(centerLat);
            int ty = (int) ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n);
            tx = Math.max(0, Math.min(n - 1, tx));
            ty = Math.max(0, Math.min(n - 1, ty));

            long tileId = PMTilesReader.zxyToTileId(z, tx, ty);
            byte[] data = reader.getTileBytes(z, tx, ty);

            if (data == null) {
                System.out.printf("  z=%2d x=%5d y=%5d tileId=%10d -> NOT FOUND%n", z, tx, ty, tileId);
                continue;
            }

            BufferedImage img = decodeImage(data);
            BufferedImage gray = terrainToGrayscale(img);
            File outFile = new File(outDir, String.format("z%d_x%d_y%d.png", z, tx, ty));
            ImageIO.write(gray, "png", outFile);
            System.out.printf("  z=%2d x=%5d y=%5d tileId=%10d -> %s (%dx%d, %d bytes raw)%n",
                    z, tx, ty, tileId, outFile.getName(), img.getWidth(), img.getHeight(), data.length);
        }
    }

    private static int extractAllAtZoom(PMTilesReader reader, int zoom, File outDir) throws IOException {
        long base = 0;
        for (int i = 0; i < zoom; i++) base += (1L << (2 * i));
        long count = 1L << (2 * zoom);
        long endId = base + count;
        System.out.printf("  TileId range for z=%d: [%d, %d) (%d tiles)%n", zoom, base, endId, count);

        int extracted = 0;
        for (long tileId = base; tileId < endId; tileId++) {
            int[] zxy = PMTilesReader.tileIdToZxy(tileId);
            byte[] data = reader.getTileBytes(zxy[0], zxy[1], zxy[2]);
            if (data == null) continue;

            BufferedImage img = decodeImage(data);
            BufferedImage gray = terrainToGrayscale(img);
            File outFile = new File(outDir, String.format("z%d_x%d_y%d.png", zxy[0], zxy[1], zxy[2]));
            ImageIO.write(gray, "png", outFile);
            extracted++;
            if (extracted % 100 == 0) System.out.println("  ... " + extracted + " tiles extracted");
        }
        return extracted;
    }

    // =========================================================================
    // Image decode + terrain-RGB to grayscale
    // =========================================================================

    private static BufferedImage decodeImage(byte[] data) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img != null) return img;

        String fmt = "unknown";
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') fmt = "WebP";
        else if (data.length > 4 && data[0] == (byte) 0x89 && data[1] == 'P') fmt = "PNG";

        throw new IOException(fmt + " tile but ImageIO can't decode it (" + data.length + " bytes). "
                + "Add to pom.xml: com.github.usefulness:webp-imageio:0.10.2");
    }

    /** Decode Terrarium-encoded terrain-RGB to 16-bit grayscale. Black=low, white=high. */
    private static BufferedImage terrainToGrayscale(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();

        // Pass 1: decode elevations, find min/max
        float[][] elev = new float[h][w];
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                float e = (float) ((r * 256.0 + g + b / 256.0) - 32768.0);
                elev[y][x] = e;
                if (e < min) min = e;
                if (e > max) max = e;
            }
        }
        System.out.printf("         elevation: min=%.1fm  max=%.1fm%n", min, max);

        // Pass 2: map to 16-bit grayscale
        float range = max - min;
        if (range < 1) range = 1;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_USHORT_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = Math.max(0, Math.min(65535, (int) ((elev[y][x] - min) / range * 65535)));
                out.getRaster().setSample(x, y, 0, v);
            }
        }
        return out;
    }
}
