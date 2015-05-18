package com.graphhopper.reader.osgb.dpn.potentialHazards;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.OsDpnOsmAttributeMappingVisitor;

public class TidalWaterTest {
    static OsDpnOsmAttributeMappingVisitor visitor;
    @Mock
    Way way;

    @BeforeClass
    public static void createVisitor() {
        visitor = new TidalWater();
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVisitWayAttribute() throws Exception {
        visitor.visitWayAttribute("tidalwater", way);
        verify(way).getTag("natural");
        verify(way).getTag("tidal");
        verify(way).setTag("natural", "water");
        verify(way).setTag("tidal", "yes");
        verifyNoMoreInteractions(way);
    }

}
