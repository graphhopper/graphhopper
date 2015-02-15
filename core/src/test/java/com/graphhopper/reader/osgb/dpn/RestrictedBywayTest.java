package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;

public class RestrictedBywayTest {
    static OsDpnOsmAttributeMappingVisitor visitor;
    @Mock
    Way way;

    @BeforeClass
    public static void createVisitor()
    {
        visitor = new RestrictedByway();
    }

    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVisitWayAttribute() throws Exception
    {
        visitor.visitWayAttribute("restrictedbyway", way);
        verify(way).setTag("designation", "restricted_byway");
        verify(way).setTag("highway", "track");
        verify(way).setTag("motor_vehicle", "no");
    }
}