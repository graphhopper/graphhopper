package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

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
        verify(way).setTag("horse", "permissive");
        verify(way).setTag("bicycle", "permissive");
    }

}