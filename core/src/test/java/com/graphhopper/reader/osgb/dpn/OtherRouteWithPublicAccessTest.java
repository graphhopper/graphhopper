package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.rightOfWay.OtherRouteWithPublicAccess;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

public class OtherRouteWithPublicAccessTest {
    static OsDpnOsmAttributeMappingVisitor visitor;
    @Mock
    Way way;

    @BeforeClass
    public static void createVisitor() {
        visitor = new OtherRouteWithPublicAccess();
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVisitWayAttribute() {
        visitor.visitWayAttribute("otherroutewithpublicaccess", way);
        verify(way).setTag("foot", "yes");
    }

}