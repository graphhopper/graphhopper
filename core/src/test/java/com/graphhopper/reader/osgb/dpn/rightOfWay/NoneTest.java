package com.graphhopper.reader.osgb.dpn.rightOfWay;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.OsDpnOsmAttributeMappingVisitor;

public class NoneTest {
    static OsDpnOsmAttributeMappingVisitor visitor;
    @Mock
    Way way;

    @BeforeClass
    public static void createVisitor() {
        visitor = new None();
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVisitWayAttribute() {
        visitor.visitWayAttribute("none", way);
        verify(way).setTag("foot", "no");
        verify(way).setTag("bicycle", "no");
        verify(way).setTag("horse", "no");
        verifyNoMoreInteractions(way);
    }

}