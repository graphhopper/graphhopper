package com.graphhopper.routing.util;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

/**
 * Test cases for the {@link PathSplitter} class. Ensure that split occurs at
 * the right index and the split point has the right coordinates
 * 
 * @author NG
 * 
 */
public class PathSplitterTest {

	@Test
	public void testFindCutIndex() {
		PathSplitter splitter = new PathSplitter();
		
		List<PathSplitterTestCase> cases = this.buildCases();
		
		for(PathSplitterTestCase c : cases) {
			int idx = splitter.findInsertIndex(c.points, c.lat, c.lon);
			Assert.assertTrue(c.caseId + " index don't match", idx == c.expIdx);
			GHPlace cutPt = splitter.getCutPoint(c.getGpsPoint(), c.getPointBeforeCut(), c.getPointAfterCut());
			if(c.expLon != Double.NaN) {
				Assert.assertEquals(c.caseId + " incorrect lon : " + c.expLon, c.expLon, cutPt.lon, 0.000000005d); // 5.00E-09 tolerance for rounding loss
				Assert.assertEquals(c.caseId + " incorrect lat : " + c.expLat, c.expLat, cutPt.lat, 0.000000005d); //
			}
		}
	}
	
	/**
	 * Test extractFullGeom method.
	 */
	@Test
	public void testExtractFullGeom() {
		EncodingManager encodingManager = new EncodingManager(
				EncodingManager.CAR);
		Graph graph = new GraphBuilder(encodingManager).create();
		// add 2 nodes
		graph.setNode(1, 50.4268298, 4.9118004); // A
		graph.setNode(2, 50.4275585, 4.9177794); // B
		// add 1 edge
		int flag = 43; // 50Km/h bidir 0b101011 with CarFlagEncoder
		assertTrue(encodingManager.getSingle().getSpeed(flag) == 50); // checks that CarFlagEncoder hasn't changed
		EdgeIterator ab = graph.edge(1, 2, 432.572, flag);
		ab.wayGeometry(buildPointList(new double[][] {
				{50.4268054, 4.912115}, {50.4268091, 4.9123011},
				{50.427086, 4.9142088}}));
		
		PathSplitter splitter = new PathSplitter();
		PointList fullGeom = splitter.extractFullGeom(ab, graph);
		
		// returned points should be edge's wayGeometry + edges tower nodes 
		int size = fullGeom.size();
		Assert.assertEquals(5, size);
		// check first node = A
		Assert.assertEquals(50.4268298, fullGeom.latitude(0), 0.000001);
		Assert.assertEquals(4.9118004, fullGeom.longitude(0), 0.000001);
		// check last node = B
		Assert.assertEquals(50.4275585, fullGeom.latitude(size-1), 0.000001);
		Assert.assertEquals(4.9177794, fullGeom.longitude(size-1), 0.000001);
	}
	
	public List<PathSplitterTestCase> buildCases() {
		
		List<PathSplitterTestCase> cases = new ArrayList<PathSplitterTestCase>();
		PathSplitterTestCase c;
		
		// case 01 : simple case idx and point
		c = new PathSplitterTestCase("Case01");
		c.withPoints(new double[][] {{1, 1}, {3, 3}})
			.withGpsCoord(3d, 1d)
			.withExpectedCutIndex(1)
			.withExpectedCutPoint(2d, 2d);
		cases.add(c);
		
		// case 02 : simple case idx and point
		c = new PathSplitterTestCase("Case02");
		c.withPoints(new double[][] {{1, 1}, {2, 4}})
			.withGpsCoord(2d, 2d)
			.withExpectedCutIndex(1)
			.withExpectedCutPoint(7 / 5d, 11 / 5d);
		cases.add(c);
		
		// case 03 : idx on path length > 2
		c = new PathSplitterTestCase("Case03");
		c.withPoints(new double[][] {{1, 0}, {0, 1}, {1, 2}, {3, 6}, {2, 7}, {0, 8}})
			.withGpsCoord(4d, 3d)
			.withExpectedCutIndex(3)
			.withExpectedCutPoint(2, 4);
		cases.add(c);
		
		// case 04: edge is perfectly horizontal
		c = new PathSplitterTestCase("Case04");
		c.withPoints(new double[][] { {0, 0}, {1, 1}, {1, 5}, {1, 6}})
			.withGpsCoord(2, 3)
			.withExpectedCutIndex(2)
			.withExpectedCutPoint(1, 3);
		cases.add(c);
		
		// case 05: edge is perfectly vertical
		c = new PathSplitterTestCase("Case05");
		c.withPoints(new double[][] {{0, 0}, {1, 1}, {5, 1}, {6, 2}})
			.withGpsCoord(3, 2)
			.withExpectedCutIndex(2)
			.withExpectedCutPoint(3, 1);
		cases.add(c);
		
		return cases;
	}
	
	private class PathSplitterTestCase {
		protected String caseId;
		protected PointList points;
		/** lon / lat of the gps point around the segment */
		protected double lon, lat;
		/** lon / lat of the expected point where to cut the path */
		protected double expLon, expLat;
		/** expected index where to cut the path */
		protected int expIdx;
		
		public PathSplitterTestCase(String caseId) {
			this.caseId = caseId;
			expLat = expLon = Double.NaN;
			expIdx = -1;
		}
		
		public PathSplitterTestCase withPoints(double[][] pts) {
			this.points = new PointList(pts.length);
			for (double[] lonlat : pts) {
				this.points.add(lonlat[0], lonlat[1]);
			}
			return this;
		}
		
		public PathSplitterTestCase withGpsCoord(double lat, double lon) {
			this.lat = lat;
			this.lon = lon;
			return this;
		}
		
		public PathSplitterTestCase withExpectedCutIndex(int idx) {
			this.expIdx = idx;
			return this;
		}
		
		public PathSplitterTestCase withExpectedCutPoint(double lat, double lon) {
			this.expLat = lat;
			this.expLon = lon;
			return this;
		}
		
		public GHPlace getGpsPoint() {
			return new GHPlace(lat, lon);
		}
		
		public GHPlace getPointBeforeCut() {
			return new GHPlace(points.latitude(expIdx - 1),
					points.longitude(expIdx - 1));
		}
		
		public GHPlace getPointAfterCut() {
			return new GHPlace(points.latitude(expIdx),
					points.longitude(expIdx));
		}
	}
	
	public static PointList buildPointList(double[][] pts) {
		PointList points = new PointList(pts.length);
		for (double[] pt : pts) {
			points.add(pt[0], pt[1]);
		}
		return points;
	}
}
