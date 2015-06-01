package com.graphhopper.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.ByteOrder;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.graphhopper.util.EdgeIteratorState;

public class AvoidanceAttributeExtensionTest {
	DataAccess dataAccess;

	@Mock
	GraphHopperStorage graph;
	@Mock
	Directory directory;
	@Mock
	EdgeIteratorState edgeOne;
	@Mock
	EdgeIteratorState edgeTwo;
	
	@Before
	public void init() {
		dataAccess = new RAMDataAccess("","", false, ByteOrder.LITTLE_ENDIAN);
		MockitoAnnotations.initMocks(this);
		Mockito.when(graph.getDirectory()).thenReturn(directory);
		Mockito.when(directory.find("avoidance_flags")).thenReturn(dataAccess);
	}

	@Test
	public void testAddEdgeInfo() {
		Mockito.when(graph.getEdgeProps(0, 1)).thenReturn(edgeOne);
		Mockito.when(graph.getEdgeProps(1, 5)).thenReturn(edgeTwo);
		Mockito.when(edgeOne.getAdditionalField()).thenReturn(-1).thenReturn(0);
		Mockito.when(edgeTwo.getAdditionalField()).thenReturn(-1).thenReturn(4);
		
		AvoidanceAttributeExtension extension = new AvoidanceAttributeExtension();
		extension.init(graph);
		extension.create(4);
		extension.addEdgeInfo(0, 1, 100);
		extension.addEdgeInfo(1, 5, 200);
		assertEquals("Retrieved value should match stored", 200, extension.getAvoidanceFlags(edgeTwo.getAdditionalField()));
		assertEquals("Retrieved value should match stored", 100, extension.getAvoidanceFlags(edgeOne.getAdditionalField()));
		extension.close();
	}

}
