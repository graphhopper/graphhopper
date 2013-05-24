package com.graphhopper.routing.util;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

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
	 
	 public List<PathSplitterTestCase> buildCases() {
		 
		 List<PathSplitterTestCase> cases = new ArrayList<PathSplitterTestCase>();
		 PathSplitterTestCase c;
		 
		 // case 01 : simple case idx & point
		 c=new PathSplitterTestCase("Case01");
		 c.withPoints(new double[][]{{1,1}, {3,3}})
		  .withGpsCoord(3d, 1d)
		  .withExpectedCutIndex(1)
		  .withExpectedCutPoint(2d, 2d);
		 cases.add(c);
		 
		 // case 02 : simple case idx and point
		 c=new PathSplitterTestCase("Case02");
		 c.withPoints(new double[][]{{1,1}, {2,4}})
		  .withGpsCoord(2d, 2d)
		  .withExpectedCutIndex(1)
		  .withExpectedCutPoint(7/5d, 11/5d);
		 cases.add(c);
		 
		 // case 03 : idx on path length > 2
		 c = new PathSplitterTestCase("Case03");
		 c.withPoints(new double[][]{{1,0}, {0,1}, {1,2}, {3,6}, {2,7}, {0,8}})
		  .withGpsCoord(4d, 3d)
		  .withExpectedCutIndex(3)
		  .withExpectedCutPoint(2, 4);
		 cases.add(c);
		 
		 // case 04: edge is perfectly horizontal
		 c = new PathSplitterTestCase("Case04");
		 c.withPoints(new double[][]{{0,0}, {1,1}, {1,5}, {1, 6}})
		  .withGpsCoord(2, 3)
		  .withExpectedCutIndex(2)
		  .withExpectedCutPoint(1, 3);
		 cases.add(c);
		 
		 // case 05: edge is perfectly vertical
		 c = new PathSplitterTestCase("Case05");
		 c.withPoints(new double[][]{{0,0}, {1,1}, {5,1}, {6, 2}})
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
			 for(double[] lonlat : pts) {
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
			 return new GHPlace(points.latitude(expIdx-1), points.longitude(expIdx-1));
		 }
		 public GHPlace getPointAfterCut() {
			 return new GHPlace(points.latitude(expIdx), points.longitude(expIdx));
		 }
	 }
}
