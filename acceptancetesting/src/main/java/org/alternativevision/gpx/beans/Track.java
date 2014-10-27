/*
 * Track.java
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
 * This class holds track information from a &lt;trk&gt; node. 
 * <br>
 * <p>GPX specification for this tag:</p>
 * <code>
 * &lt;trk&gt;<br>
 * &nbsp;&nbsp;&nbsp;&lt;name&gt; xsd:string &lt;/name&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;cmt&gt; xsd:string &lt;/cmt&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;desc&gt; xsd:string &lt;/desc&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;src&gt; xsd:string &lt;/src&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;link&gt; linkType &lt;/link&gt; [0..*]<br>
 * &nbsp;&nbsp;&nbsp;&lt;number&gt; xsd:nonNegativeInteger &lt;/number&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;type&gt; xsd:string &lt;/type&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;extensions&gt; extensionsType &lt;/extensions&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;trkseg&gt; trksegType &lt;/trkseg&gt; [0..*]<br>
 * &lt;/trk&gt;<br>
 *</code>
 */
public class Track extends Extension{
	/*
<name> xsd:string </name> [0..1] ?
<cmt> xsd:string </cmt> [0..1] ?
<desc> xsd:string </desc> [0..1] ?
<src> xsd:string </src> [0..1] ?
<link> linkType </link> [0..*] ?
<number> xsd:nonNegativeInteger </number> [0..1] ?
<type> xsd:string </type> [0..1] ?
<extensions> extensionsType </extensions> [0..1] ?
<trkseg> trksegType </trkseg> [0..*] ?
	 */
	private String name;
	private String comment;
	private String description;
	private String src;
	private Integer number;
	private String type;
	private ArrayList<Waypoint> trackPoints;
	
	/**
	 * Returns the name of this track.
	 * @return A String representing the name of this track.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Setter for track name property. This maps to &lt;name&gt; tag value.
	 * @param name A String representing the name of this track.
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Returns the comment of this track.
	 * @return A String representing the comment of this track.
	 */
	public String getComment() {
		return comment;
	}
	
	/**
	 * Setter for track comment property. This maps to &lt;comment&gt; tag value.
	 * @param comment A String representing the comment of this track.
	 */
	public void setComment(String comment) {
		this.comment = comment;
	}
	
	/**
	 * Returns the description of this track.
	 * @return A String representing the description of this track.
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * Setter for track description property. This maps to &lt;description&gt; tag value.
	 * @param description A String representing the description of this track.
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * Returns the src of this track.
	 * @return A String representing the src of this track.
	 */
	public String getSrc() {
		return src;
	}
	
	/**
	 * Setter for src type property. This maps to &lt;src&gt; tag value.
	 * @param src A String representing the src of this track.
	 */
	public void setSrc(String src) {
		this.src = src;
	}
	
	/**
	 * Returns the number of this track.
	 * @return A String representing the number of this track.
	 */
	public Integer getNumber() {
		return number;
	}
	
	/**
	 * Setter for track number property. This maps to &lt;number&gt; tag value.
	 * @param number An Integer representing the number of this track.
	 */
	public void setNumber(Integer number) {
		this.number = number;
	}
	
	/**
	 * Returns the type of this track.
	 * @return A String representing the type of this track.
	 */
	public String getType() {
		return type;
	}
	
	/**
	 * Setter for track type property. This maps to &lt;type&gt; tag value.
	 * @param type A String representing the type of this track.
	 */
	public void setType(String type) {
		this.type = type;
	}
	
	/**
	 * Getter for the list of waypoints of a track.
	 * @return an ArrayList of {@link Waypoint} representing the points of the track.
	 */
	public ArrayList<Waypoint> getTrackPoints() {
		return trackPoints;
	}
	
	/**
	 * Setter for the list of waypoints of a track.
	 * @param trackPoints an ArrayList of {@link Waypoint} representing the points of the track.
	 */
	public void setTrackPoints(ArrayList<Waypoint> trackPoints) {
		this.trackPoints = trackPoints;
	}
	
	/**
	 * Returns a String representation of this track.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("trk[");
		sb.append("name:" + name +" ");
		int points = 0;
		if(trackPoints!= null) {
			points = trackPoints.size();
		}
		sb.append("trkseg:" + points +" ");
		sb.append("]");
		return sb.toString();
	}
}
