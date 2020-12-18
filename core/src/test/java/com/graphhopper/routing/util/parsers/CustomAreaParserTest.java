package com.graphhopper.routing.util.parsers;

import com.graphhopper.config.CustomArea;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.area.CustomAreaLookup;
import com.graphhopper.routing.util.area.CustomAreaLookupJTS;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import static junit.framework.TestCase.assertTrue;

import java.util.Arrays;
import java.util.Collections;

public class CustomAreaParserTest {
    
    private static final GeometryFactory FAC = new GeometryFactory();
    
    private EncodingManager em;
    private CustomArea areaLeft;
    private CustomArea areaRight;

    @Before
    public void setUp() {
        Polygon borderLeft = FAC.createPolygon(new Coordinate[] { new Coordinate(1, 1), new Coordinate(2, 1), new Coordinate(2, 2), new Coordinate(1, 2), new Coordinate(1, 1) });
        areaLeft = new CustomArea("left", Collections.singletonList(borderLeft), "area_left", 2);
        Polygon borderRight = FAC.createPolygon(new Coordinate[] { new Coordinate(3, 1), new Coordinate(4, 1), new Coordinate(4, 2), new Coordinate(3, 2), new Coordinate(3, 1) });
        areaRight = new CustomArea("right", Collections.singletonList(borderRight), "area_right", 2);
        CustomAreaLookup customAreaLookup = new CustomAreaLookupJTS(Arrays.asList(areaLeft, areaRight));
        em = new EncodingManager.Builder().setCustomAreaLookup(customAreaLookup).build();
    }

    @Test
    public void testEncodedValueExistence() {
        assertTrue(em.hasEncodedValue(areaLeft.getEncodedValue()));
        assertTrue(em.hasEncodedValue(areaRight.getEncodedValue()));
    }
}