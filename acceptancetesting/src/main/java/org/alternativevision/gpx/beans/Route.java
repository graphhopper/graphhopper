/*
 * Route.java
 * 
 * Copyright (c) 2012, AlternativeVision. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */

package org.alternativevision.gpx.beans;

import java.util.ArrayList;

/**
 * This class holds route information from a &lt;rte&gt; node. 
 * <br>
 * <p>GPX specification for this tag:</p>
 * <code>
 * &lt;rte&gt;<br>
 * &nbsp;&nbsp;&nbsp;&lt;name&gt; xsd:string &lt;/name&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;cmt&gt; xsd:string &lt;/cmt&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;desc&gt; xsd:string &lt;/desc&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;src&gt; xsd:string &lt;/src&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;link&gt; linkType &lt;/link&gt; [0..*]<br>
 * &nbsp;&nbsp;&nbsp;&lt;number&gt; xsd:nonNegativeInteger &lt;/number&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;type&gt; xsd:string &lt;/type&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;extensions&gt; extensionsType &lt;/extensions&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;rtept&gt; wptType &lt;/rtept&gt; [0..*]<br>
 * &lt;/rte&gt;<br>
 *</code>
 */
public class Route extends Extension {
	private String name;
	private String comment;
	private String description;
	private String src;
	private Integer number;
	private String type;
	private ArrayList<Waypoint> routePoints;
	
	/**
	 * Returns the name of this route.
	 * @return A String representing the name of this route.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Setter for route name property. This maps to &lt;name&gt; tag value.
	 * @param name A String representing the name of this route.
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Returns the comment of this route.
	 * @return A String representing the comment of this route.
	 */
	public String getComment() {
		return comment;
	}
	
	/**
	 * Setter for route comment property. This maps to &lt;comment&gt; tag value.
	 * @param comment A String representing the comment of this route.
	 */
	public void setComment(String comment) {
		this.comment = comment;
	}
	
	/**
	 * Returns the description of this route.
	 * @return A String representing the description of this route.
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * Setter for route description property. This maps to &lt;description&gt; tag value.
	 * @param description A String representing the description of this route.
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * Returns the src of this route.
	 * @return A String representing the src of this route.
	 */
	public String getSrc() {
		return src;
	}
	
	/**
	 * Setter for src type property. This maps to &lt;src&gt; tag value.
	 * @param src A String representing the src of this route.
	 */
	public void setSrc(String src) {
		this.src = src;
	}
	
	/**
	 * Returns the number of this route.
	 * @return A String representing the number of this route.
	 */
	public Integer getNumber() {
		return number;
	}
	
	/**
	 * Setter for route number property. This maps to &lt;number&gt; tag value.
	 * @param number An Integer representing the number of this route.
	 */
	public void setNumber(Integer number) {
		this.number = number;
	}
	
	/**
	 * Returns the type of this route.
	 * @return A String representing the type of this route.
	 */
	public String getType() {
		return type;
	}
	
	/**
	 * Setter for route type property. This maps to &lt;type&gt; tag value.
	 * @param type A String representing the type of this route.
	 */
	public void setType(String type) {
		this.type = type;
	}
	
	/**
	 * Getter for the list of waypoints of this route. 
	 * @return an ArrayList of {@link Waypoint} representing the points of the route.
	 */
	public ArrayList<Waypoint> getRoutePoints() {
		return routePoints;
	}
	
	/**
	 * Setter for the list of waypoints of this route.
	 * @param routePoints an ArrayList of {@link Waypoint} representing the points of the route.
	 */
	public void setRoutePoints(ArrayList<Waypoint> routePoints) {
		this.routePoints = routePoints;
	}
	
	/**
	 * Adds this new waypoint to this route.
	 * @param waypoint a {@link Waypoint}.
	 */
	public void addRoutePoint(Waypoint waypoint) {
		if(routePoints == null) {
			routePoints = new ArrayList<Waypoint>();
		}
		routePoints.add(waypoint);
	}
	
	/**
	 * Returns a String representation of this track.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("rte[");
		sb.append("name:" + name +" ");
		int points = 0;
		if(routePoints!= null) {
			points = routePoints.size();
		}
		sb.append("rtepts:" + points +" ");
		sb.append("]");
		return sb.toString();
	}
}
