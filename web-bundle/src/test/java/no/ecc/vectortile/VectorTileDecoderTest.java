package no.ecc.vectortile;

import org.junit.jupiter.api.Test;
import vector_tile.VectorTile;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

public class VectorTileDecoderTest {

    @Test
    public void testDecode() throws IOException {
        byte[] data;
        try (InputStream is = getClass().getResourceAsStream("small.vector.pbf")) {
            if (is == null) {
                fail("Could not find test data");
            }
            data = is.readAllBytes();
        }
        VectorTileDecoder decoder = new VectorTileDecoder();
        VectorTileDecoder.FeatureIterable iter = decoder.decode(data);

        assertEquals(20, iter.getLayerNames().size());
        assertEquals(2759, iter.asList().size());

        VectorTileDecoder.Feature feature = iter.iterator().next();

        assertEquals("landuse", feature.getLayerName());
        assertEquals(3000000224480L, feature.getAttributes().get("osm_id"));
        assertEquals("Polygon", feature.getGeometry().getGeometryType());
        assertEquals(32, feature.getGeometry().getNumPoints());
        String wkt = "POLYGON ((-4 67.3125, -0.5625 62.4375, 13.875 46.5625, 18.625 42.8125, 26.125"
                + " 38.0625, 53.6875 26.5625, 78.375 27.6875, 109.4375 95.6875, 25.75 135.0625, 18.75"
                + " 140.375, -4 118.625, -4 67.3125), (57.125 31.375, 58.5 40.125, 58.125 48.8125,"
                + " 59.875 50.0625, 61.5625 51.25, 64 48.875, 65.0625 47.875, 66.75 46.875, 70 46.25,"
                + " 73.8125 46.5, 75.75 47, 75.8125 46, 77 35.5625, 72.1875 32.875, 66.625 31.5625,"
                + " 64.5 31.375, 61.5625 31.125, 57.125 31.375, 57.125 31.375, 57.125 31.375))";
        assertEquals(wkt, feature.getGeometry().toText());
    }
}
