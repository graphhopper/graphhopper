package com.graphhopper.reader.osgb.dpn;

import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.rightOfWay.BridleWay;

public class BridleWayTest {
    static OsDpnOsmAttributeMappingVisitor visitor;
    @Mock
    Way way;

    @BeforeClass
    public static void createVisitor() {
        visitor = new BridleWay();
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVisitWayAttribute() {
        visitor.visitWayAttribute("bridleway", way);
        verify(way).setTag("designation", "public_bridleway");
        verify(way).setTag("highway", "bridleway");
        verify(way).setTag("foot", "yes");
    }

}