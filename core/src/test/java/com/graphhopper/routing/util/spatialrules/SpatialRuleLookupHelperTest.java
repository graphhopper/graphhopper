package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.util.JsonFeature;
import com.graphhopper.util.JsonFeatureCollection;
import org.junit.Test;

import java.util.*;

import static com.graphhopper.routing.util.spatialrules.SpatialRuleLookupHelper.JSON_ID_FIELD;
import static com.graphhopper.routing.util.spatialrules.SpatialRuleLookupHelper.reorder;
import static org.junit.Assert.assertEquals;

public class SpatialRuleLookupHelperTest {

    static JsonFeatureCollection createJsonFeatureCollection(String key) {
        JsonFeatureCollection coll = new JsonFeatureCollection();
        Map<String, Object> map = new HashMap<>();
        map.put(JSON_ID_FIELD, key);
        coll.getFeatures().add(new JsonFeature(null, null, null, null, map));
        return coll;
    }

    @Test
    public void testReorder() {
        List<JsonFeatureCollection> allFeatureList = new ArrayList<>();
        allFeatureList.add(createJsonFeatureCollection("first_country"));
        allFeatureList.add(createJsonFeatureCollection("sec_country"));
        allFeatureList.add(createJsonFeatureCollection("3rd_country"));

        List<JsonFeatureCollection> result = reorder(allFeatureList, Arrays.asList("3rd_country", "sec_country"));
        assertEquals(1, result.size());
        assertEquals("3rd_country", result.get(0).getFeatures().get(0).getProperties().get(JSON_ID_FIELD));
        assertEquals("sec_country", result.get(0).getFeatures().get(1).getProperties().get(JSON_ID_FIELD));
    }
}