package com.graphhopper.reader.osgb.dpn;

import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.rightOfWay.Footpath;

public class FootpathTest {
    static OsDpnOsmAttributeMappingVisitor visitor;
    @Mock
    Way way;

    @BeforeClass
    public static void createVisitor()
    {
        visitor = new Footpath();
    }

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVisitWayAttribute()
    {
        visitor.visitWayAttribute("footpath", way);
        verify(way).setTag("designation", "public_footpath");
        verify(way).setTag("highway", "footway");
        verify(way).setTag("foot", "yes");
    }

}