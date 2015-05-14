package com.graphhopper.reader.osgb.dpn;

import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.graphhopper.reader.Way;
import com.graphhopper.reader.osgb.dpn.potentialHazards.Scree;

public class ScreeTest {
	 static OsDpnOsmAttributeMappingVisitor visitor;
	    @Mock
	    Way way;

	    @BeforeClass
	    public static void createVisitor() {
	        visitor = new Scree();
	    }

	    @Before
	    public void init() {
	        MockitoAnnotations.initMocks(this);
	    }

	    @Test
	    public void testVisitWayAttribute() throws Exception {
	        visitor.visitWayAttribute("scree", way);
	        verify(way).setTag("natural", "scree");
	    }

}
