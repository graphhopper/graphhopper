package com.graphhopper.reader.dem;

import static com.graphhopper.util.Helper.close;

import com.graphhopper.storage.DataAccess;
import java.awt.image.Raster;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import org.apache.xmlgraphics.image.codec.tiff.TIFFDecodeParam;
import org.apache.xmlgraphics.image.codec.tiff.TIFFImageDecoder;
import org.apache.xmlgraphics.image.codec.util.SeekableStream;

public class USGSProvider extends AbstractTiffElevationProvider {

    public USGSProvider(String cacheDir) {
        this("", cacheDir, "", 8112, 8112, 0.25, 0.25);
    }

    public USGSProvider(String baseUrl, String cacheDir,
            String downloaderName, int width, int height, double latDegree,
            double lonDegree) {
        super(baseUrl, cacheDir, downloaderName, width, height, latDegree,
                lonDegree);
    }

    public static void main(String[] args) {
        USGSProvider elevationProvider = new USGSProvider("/tmp/");

        // Market Street ~-5ft to 260ft in prod.
        System.out.println("Elevation: " + elevationProvider.getEle(37.7903317182555, -122.39999824547087) + "m");
        System.out.println("Elevation: " + elevationProvider.getEle(37.79112431722635, -122.39901032204128) + "m");
    }

    @Override
    boolean isOutsideSupportedArea(double lat, double lon) {
        return lat < 37.5 || lat > 38.25 || lon < -122.75 || lon > -122;
    }

    @Override
    double getMinLatForTile(double lat) {
        return Math.floor(lat * 4) / 4;
    }

    @Override
    double getMinLonForTile(double lon) {
        return Math.floor(lon * 4) / 4;
    }

    @Override
    String getFileNameOfLocalFile(double lat, double lon) {
        return getFileName(lat, lon) + ".tif";
    }

    /**
     * The USGS National Elevation Dataset (NED)'s 1/9th arc-second DEM offering
     * categorizes individual 0.25x0.25 degree tiles using the northwestern
     * corner of each tile. For example, <i>ned19_n37x75_w122x50</i> means that
     * the corners of the tile are (starting from the northwestern corner and
     * moving clockwise):
     * <ul>
     *   <li>37.75, -122.50</li>
     *   <li>37.75, -122.25</li>
     *   <li>37.50, -122.25</li>
     *   <li>37.50, -122.50</li>
     * </ul>
     * @param lat latitude in degrees, ranges from [-90.0, 90.0]
     * @param lon longitude in degrees, ranges from [-180.0, 180.0]
     * @return Filename in format ned19_{n,s}AAxAA_{e,w}BBBxBB;
     * AAxAA being latitude in degrees and BBBxBB being longitude in degrees
     */
    @Override
    String getFileName(double lat, double lon) {
        double latAdjusted = Math.abs(Math.ceil(lat * 4) / 4);
        int latDecimals = (int)(latAdjusted * 100) % 100;

        double lonAdjusted = Math.abs(getMinLonForTile(lon));
        int lonDecimals = (int)(lonAdjusted * 100) % 100;

        return String.format("ned19_n%dx%02d_w%dx%02d", (int) latAdjusted, latDecimals, (int) lonAdjusted, lonDecimals);
    }

    @Override
    String getDownloadURL(double lat, double lon) {
        return "";
    }

    @Override
    public double getEle(double lat, double lon) {
        // Return fast, if there is no data available
        if (isOutsideSupportedArea(lat, lon)) return 0;

        lat = (int) (lat * precision) / precision;
        lon = (int) (lon * precision) / precision;
        String name = getFileName(lat, lon);
        HeightTile demProvider = cacheData.get(name);
        if (demProvider == null) {
            if (!cacheDir.exists()) cacheDir.mkdirs();

            double minLat = getMinLatForTile(lat);
            double minLon = getMinLonForTile(lon);
            // less restrictive against boundary checking
            demProvider = new HeightTile(minLat, minLon, WIDTH, HEIGHT,
                    LON_DEGREE * precision, LON_DEGREE, LAT_DEGREE);
            demProvider.setInterpolate(interpolate);

            cacheData.put(name, demProvider);
            DataAccess heights = getDirectory().create(name + ".gh");
            demProvider.setHeights(heights);
            boolean loadExisting = false;
            try {
                loadExisting = heights.loadExisting();
            } catch (Exception ex) {
                logger.warn("cannot load {}, error: {}", name, ex.getMessage());
            }

            if (!loadExisting) {
                File file = new File(cacheDir,
                        new File(getFileNameOfLocalFile(lat, lon)).getName());

                // short == 2 bytes
                heights.create(2L * WIDTH * HEIGHT);
                Raster raster = generateRasterFromFile(file, name + ".tif");
                super.fillDataAccessWithElevationData(raster, heights, WIDTH);
            } // loadExisting
        }

        if (demProvider.isSeaLevel()) return 0;
        return demProvider.getHeight(lat, lon);
    }

    @Override
    Raster generateRasterFromFile(File file, String tifName) {
        SeekableStream ss = null;
        try {
            InputStream is = new FileInputStream(file);
            ss = SeekableStream.wrapInputStream(is, true);
            TIFFImageDecoder imageDecoder = new TIFFImageDecoder(ss, new TIFFDecodeParam());
            return imageDecoder.decodeAsRaster();
        } catch (Exception e) {
            throw new RuntimeException("Can't decode " + file.getName(), e);
        } finally {
            if (ss != null)
                close(ss);
        }
    }

    public String toString() { return "usgs"; }
}
