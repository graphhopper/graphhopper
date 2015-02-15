package com.graphhopper.reader.osgb.dpn;

import com.graphhopper.reader.Way;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;

public class PermissivePathTest {

    static OsDpnOsmAttributeMappingVisitor visitor;
    @Mock
    Way way;

    @BeforeClass
    public static void createVisitor() {
        visitor = new PermissivePath();
    }

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testVisitWayAttribute() throws Exception {
        visitor.visitWayAttribute("permissivepath", way);
        verify(way).setTag("foot", "permissive");
    }
}