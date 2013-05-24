package com.graphhopper.routing;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.graphhopper.routing.util.PathSplitter;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

public class TestA {

	private PathSplitter splitter = new PathSplitter();
	
	@Test
	public void test() {
		
		
		
	}
	
	private void insertPoint(PointList edgePoints, GHPlace gps, String name) {
		//edgePoints.toWkt();
		// find split index
		int index = splitter.findInsertIndex(edgePoints, gps.lat, gps.lon);
		PointList newList = new PointList(edgePoints.size()+1);
		GHPlace intersectPoint = null;
		if(index >= 0) {
			for(int i=0 ; i<edgePoints.size() ; i++) {
				if(i == index) {
					// insert
					intersectPoint = splitter.getCutPoint(gps, edgePoints.point(index-1), edgePoints.point(index));
					newList.add(intersectPoint.lat, intersectPoint.lon);
				}
				newList.add(edgePoints.latitude(i), edgePoints.longitude(i));
			}
		}
		//newList.toWkt();
		// check that the slope of the edge before and after the cut is the same 
		double xA = edgePoints.longitude(index-1);
		double yA = edgePoints.latitude(index-1);
		double xB = edgePoints.longitude(index);
		double yB = edgePoints.latitude(index);
		
		double xP = intersectPoint.lon;
		double yP = intersectPoint.lat;
		
		double a_AB = (yB-yA)/(xB-xA);
		double a_AP = (yP-yA)/(xP-xA);
		double a_PB = (yB-yP)/(xB-xP);

		Assert.assertEquals(name,a_AB, a_AP, 0);
		Assert.assertEquals(name,a_AB, a_PB, 0);
	}
	
	private List<TestCase> buildCaseList() {
		List<TestCase> cases = new ArrayList<TestCase>();
		
		cases.add(new TestCase("Case1")
			.withPoints(new double[][]{{0, 0}, {1, 1}, {3, 3}, {4, 4}})
			.withGpsPoint(3, 1)
			.withExpectedCutIndex(2)
			.withExpectedCutPoint(2, 2)
			.withExpectedPoints(new double[][]{{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 4}})
		);
		
		return cases;
	}
	
	private class TestCase {
		private String caseId;
		private PointList points;
		private double gpsLon, gpsLat;
		private int expIdx;
		private double expLon, expLat;
		private PointList expPoints;
		public TestCase(String nameId) {
			this.caseId = nameId;
		}
		public TestCase withPoints(double[][] pts) {
			points = new PointList(pts.length);
			for(double[] pt : pts) {
				points.add(pt[1], pt[0]);
			}
			return this;
		}
		public TestCase withExpectedPoints(double[][] pts) {
			expPoints = new PointList(pts.length);
			for(double[] pt : pts) {
				expPoints.add(pt[1], pt[0]);
			}
			return this;
		}		
		public TestCase withGpsPoint(double lat, double lon) {
			this.gpsLat = lat;
			this.gpsLon = lon;
			return this;
		}
		public TestCase withExpectedCutIndex(int idx) {
			this.expIdx = idx;
			return this;
		}
		public TestCase withExpectedCutPoint(double lat, double lon) {
			this.expLat = lat;
			this.expLon = lon;
			return this;
		}
	}
}
