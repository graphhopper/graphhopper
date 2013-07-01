package com.graphhopper.routing;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.PathSplitterTest;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.index.LocationIDResult;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPlace;

/**
 * Tests for the PathFinisher class
 * 
 * @author NG
 *
 */
public class PathFinisherTest
{

	private FlagEncoder carEncoder = null;
	private Graph graph = null;
	private EdgeIterator ab, bc, cd;
	private DistanceCalc calc = new DistanceCalc();
	private Path pathTest;
	
	@Before
	public void prepareTests()
	{
		EncodingManager encodingManager = new EncodingManager(EncodingManager.CAR);
		graph = new GraphBuilder(encodingManager).create();
		carEncoder = encodingManager.getSingle();
		// add 4 nodes
		graph.setNode(1, 50.4268298, 4.9118004); // A
		graph.setNode(2, 50.4275585, 4.9177794); // B
		graph.setNode(3, 50.428178, 4.917523);	 // C
		graph.setNode(4, 50.4296872, 4.92012);	 // D
		// add 3 edges
		int flag = 43; // 50Km/h bidir 0b101011 with CarFlagEncoder
		assertTrue(encodingManager.getSingle().getSpeed(flag) == 50); // checks that CarFlagEncoder hasn't changed
		ab = graph.edge(1, 2, 432.572, flag);
		ab.setWayGeometry(buildPointList(new double[][]{{50.4268054, 4.912115},
				{50.4268091, 4.9123011},{50.427086, 4.9142088}}));
		bc = graph.edge(2, 3, 71.28, flag);
		cd = graph.edge(3, 4, 250.86, flag);
		cd.setWayGeometry(buildPointList(new double[][]{{50.4282386, 4.9177328},
			{50.4283039, 4.9179075}, {50.4283644, 4.9180379}, {50.4284529, 4.9181707},
			{50.428637, 4.9184618},	{50.4287278, 4.9186063}, {50.4290562, 4.9191349},
			{50.4291773, 4.9193236}, {50.4293217, 4.9195449}, {50.4295429, 4.9198917}}));
		// create a path from 2nd node to 4th (B - C - D)
		pathTest = new SimplePath(graph, encodingManager.getSingle());
		((SimplePath)pathTest).setPoints(new double[][] {{50.4275585, 4.9177794}, {50.428178, 4.917523},
				{50.428178, 4.917523}, {50.4282386, 4.9177328}, {50.4283039, 4.9179075},
				{50.4283644, 4.9180379}, {50.4284529, 4.9181707}, {50.428637, 4.9184618},
				{50.4287278, 4.9186063}, {50.4290562, 4.9191349}, {50.4291773, 4.9193236},
				{50.4293217, 4.9195449}, {50.4295429, 4.9198917}, {50.4296872, 4.92012}}, 20);
	}
	
	/**
	 * Test the {@link SimplePath} class to ensure that other tests based on it are reliable.
	 */
	@Test
	public void testSimplePath() 
	{
		// simple 2 nodes path
		SimplePath path = new SimplePath(graph, carEncoder);
		path.setPoints(new double[][]{{50.4278663, 4.9231626}, {50.4289459, 4.924928}}, 60);
		assertEquals(2, path.calcPoints().getSize());
		assertEquals(173.3478d, path.getDistance(), 0.0001);
		assertEquals(10, path.getTime());
		
		// longer path
		path = new SimplePath(graph, carEncoder);
		path.setPoints(new double[][]{{50.4275611, 4.9226295}, {50.4274719, 4.9224848},
				{50.427324, 4.9223183}, {50.4269526, 4.9219804}, {50.4266264, 4.9217434},
				{50.4264094, 4.9215992}, {50.4261433, 4.9214904}, {50.425974, 4.9214541},
				{50.4258409, 4.9214299}, {50.425599, 4.9214299}, {50.4254055, 4.9214299},
				{50.4251998, 4.9214541}, {50.4250789, 4.9214662}, {50.4248491,4.9215992}}, 20);
		assertEquals(14, path.calcPoints().getSize());
		assertEquals(324.9371d, path.getDistance(), 0.0001);
		assertEquals(51, path.getTime());
	}

	/**
	 * This test will add point at the beginning of the path and check the
	 * result. Assume the path begins at the end of the start edge and the piece
	 * of road between GPS and start node is missing.
	 */
	@Test
	public void testFinishAddPathToStart()
	{				
		LocationIDResult fromLoc = new LocationIDResult();
		fromLoc.setClosestEdge(ab);
		LocationIDResult toLoc = new LocationIDResult();
		toLoc.setClosestEdge(cd);
		GHPlace gpsFrom = new GHPlace(50.42722, 4.91539); // GPS point close to AB
		GHPlace gpsTo = new GHPlace(50.4296872, 4.92012); // D
		PathFinisher finisher = new PathFinisher(fromLoc, toLoc, gpsFrom, gpsTo, pathTest);
		
		PointList fPts = finisher.getFinishedPointList();
//		System.out.println("points:" + fPts.getSize() + " dist:"+finisher.getFinishedDistance() + " time:"+finisher.getFinishedTime());
//		System.out.println(fPts.toWkt());
		assertEquals(15, fPts.getSize());
		// check first point coordinates
		assertEquals(fPts.getLatitude(0), 50.4272419, 0.0000001);
		assertEquals(fPts.getLongitude(0), 4.9153871, 0.0000001);
		// check distance and time
		assertEquals(494.714, finisher.getFinishedDistance(), 0.001);
		assertEquals(65, finisher.getFinishedTime(), 0.001);
	}
	
	/**
	 * This test will remove points at the end of the path and check the result.
	 * Assume the path ends at the end of the end edge and the piece of
	 * road between GPS and end node is exceeding.
	 */
	@Test
	public void testFinishRemovePathToEnd()
	{
		LocationIDResult fromLoc = new LocationIDResult();
		fromLoc.setClosestEdge(ab);
		LocationIDResult toLoc = new LocationIDResult();
		toLoc.setClosestEdge(cd);
		
		GHPlace gpsFrom = new GHPlace(50.4268298, 4.9118004); // A
		GHPlace gpsTo = new GHPlace(50.42945, 4.91932); // GPS point close to CD
		PathFinisher finisher = new PathFinisher(fromLoc, toLoc, gpsFrom, gpsTo, pathTest);
		
		PointList fPts = finisher.getFinishedPointList();
//		System.out.println("points:" + fPts.size() + " dist:"+finisher.getFinishedDistance() + " time:"+finisher.getFinishedTime());
//		System.out.println(fPts.toWkt());
		assertEquals(15, fPts.getSize());
		// check end point coordinates
		assertEquals(fPts.getLatitude(fPts.getSize()-1), 50.4292571, 0.0000001);
		assertEquals(fPts.getLongitude(fPts.getSize()-1), 4.9194458, 0.0000001);
		// check distance and time
		assertEquals(615.396, finisher.getFinishedDistance(), 0.001);
		assertEquals(74, finisher.getFinishedTime(), 0.001);
	}
	
	/**
	 * This test will create a path from two adjacent edges sharing one point
	 * assumed that from node = to node so the path is empty but GPS locations
	 * are actually separated.
	 */
	@Test
	public void testEdge2EdgePath()
	{
		GHPlace gpsFrom = new GHPlace(50.426962, 4.912174); // GPS point close to AB
		GHPlace gpsTo = new GHPlace(50.427902, 4.917701); // GPS point close to BC
		
		EdgeIterator ba = new RevertedEdge(ab);
		EdgeIterator cb = new RevertedEdge(bc);

		// build the 4 possible cases
		Map<String, EdgeIterator[]> testSuite = new HashMap<String, EdgeIterator[]>();
		testSuite.put("case 1", new EdgeIterator[]{ab, bc});
		testSuite.put("case 1", new EdgeIterator[]{ab, cb});
		testSuite.put("case 1", new EdgeIterator[]{ba, bc});
		testSuite.put("case 1", new EdgeIterator[]{ba, cb});
		
		// empty path
		SimplePath path = new SimplePath(graph, carEncoder);
		path.setPoints(new double[][]{}, 50);
		
		// test each case, all 4 cases should give the same result.
		for(Map.Entry<String, EdgeIterator[]> _case : testSuite.entrySet())
		{
			LocationIDResult fromLoc = new LocationIDResult();
			fromLoc.setClosestEdge(_case.getValue()[0]);
			LocationIDResult toLoc = new LocationIDResult();
			toLoc.setClosestEdge(_case.getValue()[1]);
			
			PathFinisher finisher = new PathFinisher(fromLoc, toLoc, gpsFrom, gpsTo, path);
			
			PointList fPts = finisher.getFinishedPointList();
//			System.out.println("points:" + fPts.size() + " dist:"+finisher.getFinishedDistance() + " time:"+finisher.getFinishedTime());
//			System.out.println(fPts.toWkt());
			assertEquals(_case.getKey(), 5, fPts.getSize());
			// check first point coordinates
			assertEquals(_case.getKey(), fPts.getLatitude(0), 50.4268065, 0.0000001);
			assertEquals(_case.getKey(), fPts.getLongitude(0), 4.9121771, 0.0000001);
			// check end point coordinates
			assertEquals(_case.getKey(), fPts.getLatitude(fPts.getSize()-1), 50.4278794, 0.0000001);
			assertEquals(_case.getKey(), fPts.getLongitude(fPts.getSize()-1), 4.9176464, 0.0000001);
			// check distance and time
			assertEquals(_case.getKey(), 184.338, finisher.getFinishedDistance(), 0.001);
			assertEquals(_case.getKey(), 13, finisher.getFinishedTime(), 0.001);
		}
	}
	
	/**
	 * This test will create a path on the very same edge. Two possible cases :
	 * routed nodes are the same and the path is empty or routed nodes are the
	 * two tower node of the same edge.
	 */
	@Test
	public void testSameEdge()
	{
		LocationIDResult fromLoc = new LocationIDResult();
		fromLoc.setClosestEdge(ab);
		LocationIDResult toLoc = new LocationIDResult();
		toLoc.setClosestEdge(ab);

		GHPlace gpsFrom = new GHPlace(50.426962, 4.912174); // GPS point on AB close to A
		GHPlace gpsTo = new GHPlace(50.42725, 4.91752); // GPS point on AB close to B
		
		// -- Case 1 routed nodes are the same and path is empty
		fromLoc.setClosestNode(1);
		toLoc.setClosestNode(1);
		// empty path
		SimplePath path = new SimplePath(graph, carEncoder);
		path.setPoints(new double[][]{}, 50);
		PathFinisher finisher = new PathFinisher(fromLoc, toLoc, gpsFrom, gpsTo, path);
		assertEquals(4, finisher.getFinishedPointList().getSize());
		assertEquals(375.59, finisher.getFinishedDistance(), 0.01);
		assertEquals(27, finisher.getFinishedTime());
		
		// -- Case 2 routed nodes are the tower nodes, path = whole ab edge;
		fromLoc.setClosestNode(1);
		toLoc.setClosestNode(2);
		// path = ab
		path.setPoints(new double[][]{{50.4268298, 4.9118004}, {50.4268054, 4.912115},
			{50.4268091, 4.9123011}, {50.427086, 4.9142088}, {50.4275585, 4.9177794}}, 50);
		finisher = new PathFinisher(fromLoc, toLoc, gpsFrom, gpsTo, path);
		assertEquals(4, finisher.getFinishedPointList().getSize());
		assertEquals(375.59, finisher.getFinishedDistance(), 0.01);
		assertEquals(24, finisher.getFinishedTime());
		// Note: time and distance between the two cases are slightly different due to rounding errors.
	}
	
	/**
	 * Tests that disconnected nodes are handled properly.<br>
	 * case 1 : nodes are disconnected, there is no possible path => nothing to
	 * finish<br>
	 * case 2 : Check that edge2egde fails properly when the 2 edges are not
	 * sharing any tower.
	 */
	@Test
	public void testEdge2EdgePathFail()
	{
		LocationIDResult fromLoc = new LocationIDResult();
		fromLoc.setClosestEdge(ab);
		LocationIDResult toLoc = new LocationIDResult();
		toLoc.setClosestEdge(cd);
		GHPlace gpsFrom = new GHPlace(50.426962, 4.912174); // GPS point close to AB
		GHPlace gpsTo = new GHPlace(50.427902, 4.917701); // GPS point close to CD
		
		// empty path
		SimplePath path = new SimplePath(graph, carEncoder);
		path.setPoints(new double[][]{}, 50);
		
		// case 1 : route not found, no connection between nodes
		fromLoc.setClosestNode(2);
		toLoc.setClosestNode(3);
		PathFinisher finisher = new PathFinisher(fromLoc, toLoc, gpsFrom, gpsTo, path);
		assertEquals(0, finisher.getFinishedPointList().getSize());
		assertEquals(0, finisher.getFinishedDistance(), 0.01);
		assertEquals(0, finisher.getFinishedTime());
		
		// case 2 : incorrect edges and node
		fromLoc.setClosestNode(2);
		toLoc.setClosestNode(2);
		finisher = new PathFinisher(fromLoc, toLoc, gpsFrom, gpsTo, path);
		try
		{
			finisher.getFinishedDistance();
			assertTrue(false);
		} catch(IllegalArgumentException iae)
		{
			assertEquals("From and To edges are not connected by their base/adj nodes", iae.getMessage());
		}
	}
	
	/**
	 * Tests that when passed edge isn't part of the path, the finisher will
	 * fail properly.
	 */
	@Test
	public void testMatchEdgeFail()
	{
		SimplePath path = new SimplePath(graph, carEncoder);
		path.setPoints(new double[][]{{50.4277519, 4.9191771}, {50.4275888, 4.9181358}}, 50);
		
		LocationIDResult fromLoc = new LocationIDResult();
		fromLoc.setClosestEdge(ab);
		LocationIDResult toLoc = new LocationIDResult();
		toLoc.setClosestEdge(cd);
		GHPlace gpsFrom = new GHPlace(50.426962, 4.912174); // GPS point close to AB
		GHPlace gpsTo = new GHPlace(50.427902, 4.917701); // GPS point close to CD
		
		PathFinisher finisher = new PathFinisher(fromLoc, toLoc, gpsFrom, gpsTo, path);
		
		try
		{
			finisher.getFinishedTime();
			assertTrue(false);
		} catch(IllegalArgumentException iae)
		{
			assertEquals("Could not find common point between edge and path", iae.getMessage());
		}
		
	}
	
	/**
	 * A simplified {@link Path} subclass only meant to expose the necessary
	 * data for the {@link PathFinisher} This class has a
	 * {@link #setPoints(double[][], int)} method to initialize the path with a
	 * point sequence and automatically compute the distance and time for the
	 * path
	 * 
	 * @author NGmip
	 * 
	 */
	private class SimplePath extends Path
	{
		private PointList points;
		private long time;
		private double distance;
		
		public SimplePath( Graph graph, FlagEncoder encoder )
		{
			super(graph, encoder);
		}
		
		/**
		 * Initializes the path with a point sequence and a speed (Km/h)
		 * 
		 * @param pts
		 *            array of {lat, lon} couples
		 * @param speed
		 *            path' speed
		 */
		public void setPoints( double[][] pts, int speed )
		{
			this.points = new PointList(pts.length);
			this.distance = 0;
			this.time = 0;
			int i=0;
			double dist;
			for(double[] pt : pts)
			{
				points.add(pt[0], pt[1]);
				if(i > 0)
				{
					dist = calc.calcDist(points.getLatitude(i-1), points.getLongitude(i-1), points.getLatitude(i), points.getLongitude(i));
					distance += dist;
					time += dist * 3.6 / speed;
				}
				i++;
			}
		}
		
		@Override
		public long getTime()
		{
			return this.time;
		}
		
		@Override
		public double getDistance()
		{
			return this.distance;
		}
		
		public PointList calcPoints()
		{
			return this.points;
		}
	}
	
	/**
	 * A proxy class over an edge iterator that revert the edge. Flag directions
	 * and geometries are inverted so that AB becomes BA.
	 * 
	 * @author NG
	 * 
	 */
	private class RevertedEdge implements EdgeIterator
	{
		private final EdgeIterator baseEdge;
		public RevertedEdge( EdgeIterator baseEdge )
		{
			this.baseEdge = baseEdge;
		}
		@Override
		public boolean next()
		{
			return baseEdge.next();
		}
		@Override
		public int getEdge()
		{
			return baseEdge.getEdge();
		}
		@Override
		public int getBaseNode()
		{
			return baseEdge.getAdjNode();
		}
		@Override
		public int getAdjNode()
		{
			return baseEdge.getBaseNode();
		}
		@Override
		public PointList getWayGeometry()
		{
			// revert geometry
			PointList baseList = baseEdge.getWayGeometry();
			int size = baseList.getSize();
			PointList revList = new PointList();
			for( int i=0 ; i < size ; i++ )
			{
				revList.add(baseList.getLatitude(size-1-i), baseList.getLongitude(size-1-i));
			}
			return revList;
		}
		@Override
		public void setWayGeometry( PointList list )
		{
			throw new IllegalStateException("RevertedEdge is read only");
		}
		@Override
		public double getDistance()
		{
			return baseEdge.getDistance();
		}
		@Override
		public void setDistance( double dist )
		{
			throw new IllegalStateException("RevertedEdge is read only");
		}
		@Override
		public int getFlags()
		{
			// revert flags directions
			int flag = ((AbstractFlagEncoder)carEncoder).swapDirection(baseEdge.getFlags());
			return flag;
		}
		@Override
		public void setFlags( int flags )
		{
			throw new IllegalStateException("RevertedEdge is read only");
		}
		@Override
		public boolean isEmpty()
		{
			return baseEdge.isEmpty();
		}
		@Override
		public String getName()
		{
			return baseEdge.getName();
		}
		@Override
		public void setName(String name)
		{
			throw new IllegalStateException("RevertedEdge is read only");
			
		}
	}
	
	private static PointList buildPointList( double[][] pts )
	{
		return PathSplitterTest.buildPointList(pts);
	}
}
