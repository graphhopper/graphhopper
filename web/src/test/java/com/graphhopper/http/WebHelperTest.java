package com.graphhopper.http;

import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class WebHelperTest {

    @Test
    public void testDecode() throws Exception {
        PointList list = WebHelper.decodePolyline("_p~iF~ps|U", 1);
        assertEquals(Helper.createPointList(38.5, -120.2), list);

        list = WebHelper.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 3);
        assertEquals(Helper.createPointList(38.5, -120.2, 40.7, -120.95, 43.252, -126.453), list);
    }

    @Test
    public void testEncode() throws Exception {
        assertEquals("_p~iF~ps|U", WebHelper.encodePolyline(
                Helper.createPointList(38.5, -120.2)));

        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", WebHelper.encodePolyline(
                Helper.createPointList(38.5, -120.2, 40.7, -120.95, 43.252, -126.453)));
    }

    @Test
    public void testBoth() throws Exception {
        PointList list = Helper.createPointList(38.5, -120.2, 43.252, -126.453,
                40.7, -120.95, 50.3139, 10.612793, 50.04303, 9.497681);
        String str = WebHelper.encodePolyline(list);
        assertEquals(list, WebHelper.decodePolyline(str, list.size()));

        list = Helper.createPointList(38.5, -120.2, 43.252, -126.453,
                40.7, -120.95, 40.70001, -120.95001);
        str = WebHelper.encodePolyline(list);
        assertEquals(list, WebHelper.decodePolyline(str, list.size()));
    }
}
