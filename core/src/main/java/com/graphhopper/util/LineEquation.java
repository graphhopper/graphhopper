package com.graphhopper.util;

import com.graphhopper.util.shapes.GHPlace;

/**
 * Utility class to compute projection of a point in a line. Instances of this
 * class are created by giving the necessary data to define the line to work
 * with.
 * 
 * @author philippe.suray, NG
 */
public class LineEquation {
	/** 1st point of the line */
	private double[] p1 = null;
	/** 2nd point of the line */
	private double[] p2 = null;
	/** line' slope/gradient */
	private double a = 0.0d;
	/** line's y-intercept */
	private double b = 0.0d;

	/**
	 * Create the reference line to work with out of 2 of its points.
	 * 
	 * @param p1
	 * @param p2
	 */
	public LineEquation(double[] p1, double[] p2) {
		setP1(p1);
		setP2(p2);
	}

	/**
	 * Create the reference line to work with out of its parameter so that
	 * 
	 * <pre>
	 * y = ax + b
	 * </pre>
	 * 
	 * @param a
	 *            slope/gradient of the line
	 * @param b
	 *            y intercept
	 */
	public LineEquation(double a, double b) {
		setA(a);
		setB(b);
	}

	/**
	 * create the reference line to work with out of 2 of it's points
	 * 
	 * @param p1
	 * @param p2
	 */
	public LineEquation(GHPlace p1, GHPlace p2) {
		this(new double[] { p1.lon, p1.lat }, new double[] { p2.lon, p2.lat });
	}

	/**
	 * computes the two parameters of the line (gradient/slope and y intercept)
	 * 
	 * @return true if the coefficient was computed (p1 != null && p2 != null)
	 */
	private boolean computeCoefficient() {
		if (p1 != null && p2 != null) {
			setA((p2[1] - p1[1]) / (p2[0] - p1[0]));	// a = y2-y1 / x2-x1
			setB(-a * p1[0] + p1[1]); 					// b = -ax + y
			//setB((p1[1] * p2[0] - p1[0] * p2[1]) / (p2[0] - p1[0]));
			return true;
		}
		return false;
	}

	/**
	 * Compute the shortest distance between a point and the line
	 * 
	 * @param point
	 * @return
	 */
	public double distanceFromPoint(GHPlace point) {
		GHPlace inter = getIntersection(point);
		return Math.sqrt(Math.pow(point.lon - inter.lon, 2.0d)
				+ Math.pow(point.lat - inter.lat, 2.0d));
	}

	/**
	 * Compute the shortest distance between a point and the line
	 * 
	 * @param p
	 * @return
	 */
	public double distanceFromPoint(double[] p) {
		return distanceFromPoint(new GHPlace(p[0], p[1]));
	}

	/**
	 * Computes the intercept point at the shortest distance between a point and
	 * a line
	 * 
	 * @param p
	 * @return
	 */
	public GHPlace getIntersection(GHPlace p) {
		return getIntersection(p.lat, p.lon);
	}
	
	/**
	 * Computes the intercept point at the shortest distance between a point and
	 * a line
	 * 
	 * @param p
	 * @return
	 */
	public GHPlace getIntersection(double lat, double lon) {
		// d1 : y=ax+b
		// d2 : y=a2x+b2
		// d1 perpendicular to d2 => a2 = -1/a
		double a2 = -1/a;
		// d2 passes by lat/lon => lat = a2*lon + b2 => b2 = lat -(a2*lon)
		double b2 = lat -(a2*lon);
		
		double x = (b2-b)/(a-a2);
		double y = a*x+b;		
		
		GHPlace p = new GHPlace(y, x);
		if(Double.isNaN(p.lon)) {
			// special rare case handling
			if(a == 0) {
				// case D1 is horizontal
				p = new GHPlace(b, lon);
			} else if(Double.isInfinite(a)) {
				// case D1 is vertical
				p = new GHPlace(b2, p1[0]);
			}
		}
		return p;
	}

	/**
	 * Allows to determine on which side of the line the point is located.
	 * 
	 * <pre>
	 * value > 0 : point is on the left
	 * value < 0 : point is on the right
	 * value = 0 : point is on the line
	 * </pre>
	 * 
	 * @param point
	 * @return double
	 */
	public double valueOf(GHPlace point) {
		return point.lat - a * point.lon - b;
	}

	/**
	 * First point defining the line
	 * 
	 * @return
	 */
	public double[] getP1() {
		return p1;
	}

	/**
	 * Set the first point of the line and recompute a and b
	 * 
	 * @param p1
	 */
	public void setP1(double[] p1) {
		this.p1 = p1;
		this.computeCoefficient();
	}

	/**
	 * Set the first point of the line and recompute a and b
	 * 
	 * @param p1
	 */
	public void setP1(GHPlace p1) {
		this.setP1(new double[] { p1.lon, p1.lat });
	}

	/**
	 * Set the second point of the line and recompute a and b
	 * 
	 * @param p2
	 */
	public void setP2(double[] p2) {
		this.p2 = p2;
		this.computeCoefficient();
	}

	/**
	 * Set the second point of the line and recompute a and b
	 * 
	 * @param p2
	 */
	public void setP2(GHPlace p2) {
		this.setP2(new double[] { p2.lon, p2.lat });
	}

	/**
	 * Second point defining the line
	 * 
	 * @return
	 */
	public double[] getP2() {
		return p2;
	}

	/**
	 * The line' slope/gradient
	 * 
	 * @return
	 */
	public double getA() {
		return a;
	}

	/**
	 * The line's y-intercept
	 * 
	 * @return
	 */
	public double getB() {
		return b;
	}

	/**
	 * Set the line' slope/gradient
	 * 
	 * @param d
	 */
	public void setA(double d) {
		a = d;
	}

	/**
	 * Set the lines' y-intercept
	 * 
	 * @param d
	 */
	public void setB(double d) {
		b = d;
	}
}