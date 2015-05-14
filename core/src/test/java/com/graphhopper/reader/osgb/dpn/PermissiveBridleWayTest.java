package com.graphhopper.reader.osgb.dpn;

import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.rightOfWay.PermissiveBridleWay;

public class PermissiveBridleWayTest {
    static OsDpnOsmAttributeMappingVisitor visitor;
    @Mock
    Way way;

    @BeforeClass
    public static void createVisitor()
    {
        visitor = new PermissiveBridleWay();
    }

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVisitWayAttribute()
    {
        visitor.visitWayAttribute("permissivebridleway", way);
        verify(way).setTag("highway", "bridleway");
        verify(way).setTag("horse", "permissive");
        verify(way).setTag("bicycle", "permissive");
        verify(way).setTag("foot", "permissive");
    }

}