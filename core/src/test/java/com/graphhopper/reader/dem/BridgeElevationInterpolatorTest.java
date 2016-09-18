package com.graphhopper.reader.dem;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

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
import com.graphhopper.util.Helper;

import gnu.trove.set.hash.TIntHashSet;

public class BridgeElevationInterpolatorTest {

	private static final double EPSILON= 0.0000000001d;
	private static final ReaderWay BRIDGE_WAY;
	private static final ReaderWay NORMAL_WAY;
	
	static {
		BRIDGE_WAY = new ReaderWay(0);
		BRIDGE_WAY.setTag("highway", "primary");
		BRIDGE_WAY.setTag("bridge", "yes");
		NORMAL_WAY = new ReaderWay(0);
		NORMAL_WAY.setTag("highway", "primary");
	}
	
	private GraphHopperStorage graph;
	private DataFlagEncoder dataFlagEncoder;
	private BridgeElevationInterpolator bridgeElevationInterpolator;
	
	@Before
	public void init(){
		graph = new GraphHopperStorage(new RAMDirectory(), new EncodingManager("generic,foot", 8),
				true, new GraphExtension.NoOpExtension()).create(100);

		dataFlagEncoder = (DataFlagEncoder) graph.getEncodingManager().getEncoder("generic");

		bridgeElevationInterpolator = new BridgeElevationInterpolator(graph, dataFlagEncoder);
		
	}
	
	
	@Test
	public void interpolatesElevationOfTunnelWithFourOuterNodes() {

		/*
		 * Graph structure:
		 * 0-----1-----2-----3-----4
		 *        \    |    /
		 *         \   |   /
		 *          T  T  T
		 *           \ | /
		 *            \|/
		 * 5-----6--T--7--T--8-----9
		 */
		
		NodeAccess na = graph.getNodeAccess();
		na.setNode(0, 0, 0, 0);
		na.setNode(1, 10, 0, 10);
		na.setNode(2, 20, 0, 20);
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
		EdgeIteratorState edge17 = graph.edge(1, 7, 10, true);
		EdgeIteratorState edge27 = graph.edge(2, 7, 10, true);
		EdgeIteratorState edge37 = graph.edge(3, 7, 10, true);
		edge17.setWayGeometry(Helper.createPointList3D(12,2,200,14,4,400,16,6,600,18,8,800,20,10,1000));

		edge01.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge12.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge23.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge34.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));

		edge56.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));
		edge67.setFlags(dataFlagEncoder.handleWayTags(BRIDGE_WAY, 1, 0));
		edge78.setFlags(dataFlagEncoder.handleWayTags(BRIDGE_WAY, 1, 0));
		edge89.setFlags(dataFlagEncoder.handleWayTags(NORMAL_WAY, 1, 0));

		edge17.setFlags(dataFlagEncoder.handleWayTags(BRIDGE_WAY, 1, 0));
		edge27.setFlags(dataFlagEncoder.handleWayTags(BRIDGE_WAY, 1, 0));
		edge37.setFlags(dataFlagEncoder.handleWayTags(BRIDGE_WAY, 1, 0));

		final TIntHashSet outerNodeIds = new TIntHashSet();
		final TIntHashSet innerNodeIds = new TIntHashSet();
		gatherOuterAndInnerNodeIdsOfStructure(edge27, outerNodeIds, innerNodeIds);
		
		assertEquals(new TIntHashSet(Arrays.asList(1, 2, 3, 6, 8)), outerNodeIds);
		assertEquals(new TIntHashSet(Arrays.asList(7)), innerNodeIds);
		
		bridgeElevationInterpolator.execute();
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
		assertEquals(Helper.createPointList3D(10,0,10,12,2,12,14,4,14,16,6,16,18,8,18,20,10,20),edge17.fetchWayGeometry(1));
	}
	

	private void gatherOuterAndInnerNodeIdsOfStructure(EdgeIteratorState edge,
			final TIntHashSet outerNodeIds, final TIntHashSet innerNodeIds) {
		bridgeElevationInterpolator.gatherOuterAndInnerNodeIdsOfStructure(bridgeElevationInterpolator.getStorage().createEdgeExplorer(), edge,
				new GHBitSetImpl(), outerNodeIds, innerNodeIds);
	}
}
