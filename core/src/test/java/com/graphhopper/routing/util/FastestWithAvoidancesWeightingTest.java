package com.graphhopper.routing.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.OngoingStubbing;

import com.graphhopper.storage.AvoidanceAttributeExtension;
import com.graphhopper.util.EdgeIteratorState;

public class FastestWithAvoidancesWeightingTest {
	@Mock
	FlagEncoder encoder;
	
	@Mock
	EdgeIteratorState edge;
	
	@Mock
	AvoidanceAttributeExtension avoidanceExtension;
	
	@Before
	public void configureMocks() {
		MockitoAnnotations.initMocks(this);
		configureSpeeds();
	}

	@Test
	public void testSingleAvoidWhenMatches() {
		String[] avoidances = {"cliff"};
		when(encoder.getBitMask(avoidances, AbstractAvoidanceDecorator.KEY)).thenReturn(4L);
		expectStoredAvoidance().thenReturn(4L);
		FastestWithAvoidancesWeighting weighting = new FastestWithAvoidancesWeighting(encoder, avoidanceExtension, "cliff");
		int prevOrNextEdgeId=1;
		boolean reverse = false;
		assertTrue("Avoidable Edges should have maximum weight", Double.isInfinite(weighting.calcWeight(edge, reverse , prevOrNextEdgeId)));
	}

	@Test
	public void testSingleAvoidWhenNoMatch() {
		String[] avoidances = {"cliff"};
		when(encoder.getBitMask(avoidances, AbstractAvoidanceDecorator.KEY)).thenReturn(4L);
		expectStoredAvoidance().thenReturn(1L);
		FastestWithAvoidancesWeighting weighting = new FastestWithAvoidancesWeighting(encoder, avoidanceExtension,  "cliff");
		int prevOrNextEdgeId=1;
		boolean reverse = false;
		assertFalse("Routable Edges should not have maximum weight", Double.isInfinite(weighting.calcWeight(edge, reverse , prevOrNextEdgeId)));
	}
	
	@Test
	public void testMultiAvoidWhenRouteIsExactMatch() {
		String[] avoidances = {"cliff","aroad"};
		when(encoder.getBitMask(avoidances, AbstractAvoidanceDecorator.KEY)).thenReturn(5L);
		expectStoredAvoidance().thenReturn(5L);
		FastestWithAvoidancesWeighting weighting = new FastestWithAvoidancesWeighting(encoder, avoidanceExtension,  "cliff", "aroad");
		int prevOrNextEdgeId=1;
		boolean reverse = false;
		assertTrue("Avoidable Edges should have maximum weight", Double.isInfinite(weighting.calcWeight(edge, reverse , prevOrNextEdgeId)));
	}
	
	@Test
	public void testMultiAvoidWhenRouteContainsOneOfTheAvoidances() {
		String[] avoidances = {"cliff","aroad"};
		when(encoder.getBitMask(avoidances, AbstractAvoidanceDecorator.KEY)).thenReturn(5L);
		expectStoredAvoidance().thenReturn(4L);
		FastestWithAvoidancesWeighting weighting = new FastestWithAvoidancesWeighting(encoder, avoidanceExtension,  "cliff", "aroad");
		int prevOrNextEdgeId=1;
		boolean reverse = false;
		assertTrue("Avoidable Edges should have maximum weight", Double.isInfinite(weighting.calcWeight(edge, reverse , prevOrNextEdgeId)));
	}
	
	@Test
	public void testMultiAvoidWhenNoMatch() {
		String[] avoidances = {"cliff","aroad"};
		when(encoder.getBitMask(avoidances, AbstractAvoidanceDecorator.KEY)).thenReturn(5L);
		expectStoredAvoidance().thenReturn(2L);
		FastestWithAvoidancesWeighting weighting = new FastestWithAvoidancesWeighting(encoder, avoidanceExtension,  "cliff", "aroad");
		int prevOrNextEdgeId=1;
		boolean reverse = false;
		assertFalse("Routable Edges should not have maximum weight", Double.isInfinite(weighting.calcWeight(edge, reverse , prevOrNextEdgeId)));
	}

	/**
	 * Sets up :-
	 *  max speed = 100
	 *  edge speed = 50
	 */
	private void configureSpeeds() {
		when(encoder.getMaxSpeed()).thenReturn(100D);
		when(encoder.getSpeed(anyLong())).thenReturn(50D);
	}
	
	private OngoingStubbing<Long> expectStoredAvoidance() {
		return when(avoidanceExtension.getAvoidanceFlags(anyLong()));
	}

}
