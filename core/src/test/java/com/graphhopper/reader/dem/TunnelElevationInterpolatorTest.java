package com.graphhopper.reader.dem;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.coll.GHBitSetImpl;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.DataFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIteratorState;

import gnu.trove.set.hash.TIntHashSet;

public class TunnelElevationInterpolatorTest {

	private static final double EPSILON= 0.0000000001d;
	private static final ReaderWay TUNNEL_WAY;
	private static final ReaderWay NORMAL_WAY;
	
	static {
		TUNNEL_WAY = new ReaderWay(0);
		TUNNEL_WAY.setTag("highway", "primary");
		TUNNEL_WAY.setTag("tunnel", "yes");
		NORMAL_WAY = new ReaderWay(0);
		NORMAL_WAY.setTag("highway", "primary");
	}
	
	private GraphHopperStorage graph;
	private DataFlagEncoder dataFlagEncoder;
	private TunnelElevationInterpolator tunnelElevationInterpolator;
	
	@Before
	public void init(){
		graph = new GraphHopperStorage(new RAMDirectory(), new EncodingManager("generic,foot", 8),
				true, new GraphExtension.NoOpExtension()).create(100);

		dataFlagEncoder = (DataFlagEncoder) graph.getEncodingManager().getEncoder("generic");

		tunnelElevationInterpolator = new TunnelElevationInterpolator(graph, dataFlagEncoder);
		
	}
	
	@Test
	public void doesNotInterpolateElevationOfTunnelWithZeroOuterNodes() {

		/*
		 * Graph structure:
		 * 0--T--1--T--2     3--T--4
		 * Tunnel 0-1-2 has a single outer node 2.
		 */
		
		NodeAccess na = graph.getNodeAccess();
		na.setNode(0, 0, 0, 0);
		na.setNode(1, 10, 0, 0);
		na.setNode(2, 20, 0, 10);
		na.setNode(3, 30, 0, 20);
		na.setNode(4, 40, 0, 0);

		EdgeIteratorState edge01 = graph.edge(0, 1, 10, true);
		EdgeIteratorState edge12 = graph.edge(1, 2, 10, true);
		EdgeIteratorState edge34 = graph.edge(3, 4, 10, true);

		edge01.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge12.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge34.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));

		final TIntHashSet outerNodeIds = new TIntHashSet();
		final TIntHashSet innerNodeIds = new TIntHashSet();
		gatherOuterAndInnerNodeIdsOfStructure(edge01, outerNodeIds, innerNodeIds);
		
		assertEquals(new TIntHashSet(Collections.<Integer>emptyList()), outerNodeIds);
		assertEquals(new TIntHashSet(Arrays.asList(0, 1, 2)), innerNodeIds);
		
		tunnelElevationInterpolator.execute();
		assertEquals(0, na.getElevation(0), EPSILON); 
		assertEquals(0, na.getElevation(1), EPSILON); 
		assertEquals(10, na.getElevation(2), EPSILON); 
		assertEquals(20, na.getElevation(3), EPSILON); 
		assertEquals(0, na.getElevation(4), EPSILON); 
	}
	

	@Test
	public void interpolatesElevationOfTunnelWithSingleOuterNode() {

		/*
		 * Graph structure:
		 * 0--T--1--T--2-----3--T--4
		 */
		
		NodeAccess na = graph.getNodeAccess();
		na.setNode(0, 0, 0, 0);
		na.setNode(1, 10, 0, 00);
		na.setNode(2, 20, 0, 10);
		na.setNode(3, 30, 0, 20);
		na.setNode(4, 40, 0, 00);

		EdgeIteratorState edge01 = graph.edge(0, 1, 10, true);
		EdgeIteratorState edge12 = graph.edge(1, 2, 10, true);
		EdgeIteratorState edge23 = graph.edge(2, 3, 10, true);
		EdgeIteratorState edge34 = graph.edge(3, 4, 10, true);

		edge01.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge12.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge23.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge34.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));

		final TIntHashSet outerNodeIds = new TIntHashSet();
		final TIntHashSet innerNodeIds = new TIntHashSet();
		gatherOuterAndInnerNodeIdsOfStructure(edge01, outerNodeIds, innerNodeIds);
		
		assertEquals(new TIntHashSet(Arrays.asList(2)), outerNodeIds);
		assertEquals(new TIntHashSet(Arrays.asList(0, 1)), innerNodeIds);
		
		tunnelElevationInterpolator.execute();
		assertEquals(10, na.getElevation(0), EPSILON); 
		assertEquals(10, na.getElevation(1), EPSILON); 
		assertEquals(10, na.getElevation(2), EPSILON); 
		assertEquals(20, na.getElevation(3), EPSILON); 
		assertEquals(20, na.getElevation(4), EPSILON); 
	}
	
	
	@Test
	public void interpolatesElevationOfTunnelWithTwoOuterNodes() {

		/*
		 * Graph structure:
		 * 0-----1--T--2--T--3-----4
		 */
		
		NodeAccess na = graph.getNodeAccess();
		na.setNode(0, 0, 0, 0);
		na.setNode(1, 10, 0, 10);
		na.setNode(2, 20, 0, 1000);
		na.setNode(3, 30, 0, 30);
		na.setNode(4, 40, 0, 40);

		EdgeIteratorState edge01 = graph.edge(0, 1, 10, true);
		EdgeIteratorState edge12 = graph.edge(1, 2, 10, true);
		EdgeIteratorState edge23 = graph.edge(2, 3, 10, true);
		EdgeIteratorState edge34 = graph.edge(3, 4, 10, true);

		edge01.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge12.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge23.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge34.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));

		final TIntHashSet outerNodeIds = new TIntHashSet();
		final TIntHashSet innerNodeIds = new TIntHashSet();
		gatherOuterAndInnerNodeIdsOfStructure(edge12, outerNodeIds, innerNodeIds);
		
		assertEquals(new TIntHashSet(Arrays.asList(1, 3)), outerNodeIds);
		assertEquals(new TIntHashSet(Arrays.asList(2)), innerNodeIds);
		
		tunnelElevationInterpolator.execute();
		assertEquals(0, na.getElevation(0), EPSILON); 
		assertEquals(10, na.getElevation(1), EPSILON); 
		assertEquals(20, na.getElevation(2), EPSILON); 
		assertEquals(30, na.getElevation(3), EPSILON); 
		assertEquals(40, na.getElevation(4), EPSILON); 
	}
	
	@Test
	public void interpolatesElevationOfTunnelWithThreeOuterNodes() {

		/*
		 * Graph structure:
		 * 0-----1--T--2--T--3-----4
		 *             |
		 *             |
		 *             T
		 *             |
		 *             |
		 *             5--T--6-----7
		 */
		
		NodeAccess na = graph.getNodeAccess();
		na.setNode(0, 0, 0, 0);
		na.setNode(1, 10, 0, 10);
		na.setNode(2, 20, 0, 1000);
		na.setNode(3, 30, 0, 30);
		na.setNode(4, 40, 0, 40);
		na.setNode(5, 20, 10, 1000);
		na.setNode(6, 30, 10, 30);
		na.setNode(7, 40, 10, 40);

		EdgeIteratorState edge01 = graph.edge(0, 1, 10, true);
		EdgeIteratorState edge12 = graph.edge(1, 2, 10, true);
		EdgeIteratorState edge23 = graph.edge(2, 3, 10, true);
		EdgeIteratorState edge34 = graph.edge(3, 4, 10, true);
		EdgeIteratorState edge25 = graph.edge(2, 5, 10, true);
		EdgeIteratorState edge56 = graph.edge(5, 6, 10, true);
		EdgeIteratorState edge67 = graph.edge(6, 7, 10, true);

		edge01.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge12.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge23.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge34.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge25.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge56.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge67.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));

		final TIntHashSet outerNodeIds = new TIntHashSet();
		final TIntHashSet innerNodeIds = new TIntHashSet();
		gatherOuterAndInnerNodeIdsOfStructure(edge12, outerNodeIds, innerNodeIds);
		
		assertEquals(new TIntHashSet(Arrays.asList(1, 3, 6)), outerNodeIds);
		assertEquals(new TIntHashSet(Arrays.asList(2, 5)), innerNodeIds);
		
		tunnelElevationInterpolator.execute();
		assertEquals(0, na.getElevation(0), EPSILON); 
		assertEquals(10, na.getElevation(1), EPSILON); 
		assertEquals(20, na.getElevation(2), EPSILON); 
		assertEquals(30, na.getElevation(3), EPSILON); 
		assertEquals(40, na.getElevation(4), EPSILON); 
		assertEquals(20, na.getElevation(5), EPSILON); 
		assertEquals(30, na.getElevation(6), EPSILON); 
		assertEquals(40, na.getElevation(7), EPSILON); 
	}
	
	
	@Test
	public void interpolatesElevationOfTunnelWithFourOuterNodes() {

		/*
		 * Graph structure:
		 * 0-----1--T--2--T--3-----4
		 *             |
		 *             |
		 *             T
		 *             |
		 *             |
		 * 5-----6--T--7--T--8-----9
		 */
		
		NodeAccess na = graph.getNodeAccess();
		na.setNode(0, 0, 0, 0);
		na.setNode(1, 10, 0, 10);
		na.setNode(2, 20, 0, 1000);
		na.setNode(3, 30, 0, 30);
		na.setNode(4, 40, 0, 40);
		na.setNode(5, 0, 10, 40);
		na.setNode(6, 10, 10, 30);
		na.setNode(7, 20, 10, 1000);
		na.setNode(8, 30, 10, 10);
		na.setNode(9, 40, 10, 0);

		EdgeIteratorState edge01 = graph.edge(0, 1, 10, true);
		EdgeIteratorState edge12 = graph.edge(1, 2, 10, true);
		EdgeIteratorState edge23 = graph.edge(2, 3, 10, true);
		EdgeIteratorState edge34 = graph.edge(3, 4, 10, true);
		EdgeIteratorState edge56 = graph.edge(5, 6, 10, true);
		EdgeIteratorState edge67 = graph.edge(6, 7, 10, true);
		EdgeIteratorState edge78 = graph.edge(7, 8, 10, true);
		EdgeIteratorState edge89 = graph.edge(8, 9, 10, true);
		EdgeIteratorState edge27 = graph.edge(2, 7, 10, true);

		edge01.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge12.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge23.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge34.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));

		edge56.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge67.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge78.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));
		edge89.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));

		edge27.setFlags(dataFlagEncoder.handleWayTags(TUNNEL_WAY, 1, 0));

		final TIntHashSet outerNodeIds = new TIntHashSet();
		final TIntHashSet innerNodeIds = new TIntHashSet();
		gatherOuterAndInnerNodeIdsOfStructure(edge12, outerNodeIds, innerNodeIds);
		
		assertEquals(new TIntHashSet(Arrays.asList(1, 3, 6, 8)), outerNodeIds);
		assertEquals(new TIntHashSet(Arrays.asList(2, 7)), innerNodeIds);
		
		tunnelElevationInterpolator.execute();
		assertEquals(0, na.getElevation(0), EPSILON); 
		assertEquals(10, na.getElevation(1), EPSILON); 
		assertEquals(20, na.getElevation(2), EPSILON); 
		assertEquals(30, na.getElevation(3), EPSILON); 
		assertEquals(40, na.getElevation(4), EPSILON); 
		assertEquals(40, na.getElevation(5), EPSILON); 
		assertEquals(30, na.getElevation(6), EPSILON); 
		assertEquals(20, na.getElevation(7), EPSILON); 
		assertEquals(10, na.getElevation(8), EPSILON); 
		assertEquals(0, na.getElevation(9), EPSILON); 
	}
	

	private void gatherOuterAndInnerNodeIdsOfStructure(EdgeIteratorState edge,
			final TIntHashSet outerNodeIds, final TIntHashSet innerNodeIds) {
		tunnelElevationInterpolator.gatherOuterAndInnerNodeIdsOfStructure(tunnelElevationInterpolator.getStorage().createEdgeExplorer(), edge,
				new GHBitSetImpl(), outerNodeIds, innerNodeIds);
	}
}
