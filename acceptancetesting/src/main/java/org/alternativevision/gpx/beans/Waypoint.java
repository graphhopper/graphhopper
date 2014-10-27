/*
 * Waypoint.java
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import org.alternativevision.gpx.types.FixType;

/**
 * This class holds waypoint information from a &lt;wpt&gt; node. 
 * <br>
 * <p>GPX specification for this tag:</p>
 * <code>
 * &lt;wpt<br>
 * &nbsp;&nbsp;&nbsp;lat="latitudeType [1]"<br>
 * &nbsp;&nbsp;&nbsp;lon="longitudeType [1]"&gt;<br> 
 * &nbsp;&nbsp;&nbsp;&lt;ele&gt; xsd:decimal &lt;/ele&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;time&gt; xsd:dateTime &lt;/time&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;magvar&gt; degreesType &lt;/magvar&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;geoidheight&gt; xsd:decimal &lt;/geoidheight&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;name&gt; xsd:string &lt;/name&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;cmt&gt; xsd:string &lt;/cmt&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;desc&gt; xsd:string &lt;/desc&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;src&gt; xsd:string &lt;/src&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;link&gt; linkType &lt;/link&gt; [0..*]<br>
 * &nbsp;&nbsp;&nbsp;&lt;sym&gt; xsd:string &lt;/sym&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;type&gt; xsd:string &lt;/type&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;fix&gt; fixType &lt;/fix&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;sat&gt; xsd:nonNegativeInteger &lt;/sat&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;hdop&gt; xsd:decimal &lt;/hdop&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;vdop&gt; xsd:decimal &lt;/vdop&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;pdop&gt; xsd:decimal &lt;/pdop&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;ageofdgpsdata&gt; xsd:decimal &lt;/ageofdgpsdata&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;dgpsid&gt; dgpsStationType &lt;/dgpsid&gt; [0..1]<br>
 * &nbsp;&nbsp;&nbsp;&lt;extensions&gt; extensionsType &lt;/extensions&gt; [0..1]<br>
 * &lt;/wpt&gt;<br>
 *</code>
 */
public class Waypoint extends Extension{

	/*
	 lat="latitudeType [1] ?"
lon="longitudeType [1] ?"> 
<ele> xsd:decimal </ele> [0..1] ?
<time> xsd:dateTime </time> [0..1] ?
<magvar> degreesType </magvar> [0..1] ?
<geoidheight> xsd:decimal </geoidheight> [0..1] ?
<name> xsd:string </name> [0..1] ?
<cmt> xsd:string </cmt> [0..1] ?
<desc> xsd:string </desc> [0..1] ?
<src> xsd:string </src> [0..1] ?
<link> linkType </link> [0..*] ?
<sym> xsd:string </sym> [0..1] ?
<type> xsd:string </type> [0..1] ?
<fix> fixType </fix> [0..1] ?
<sat> xsd:nonNegativeInteger </sat> [0..1] ?
<hdop> xsd:decimal </hdop> [0..1] ?
<vdop> xsd:decimal </vdop> [0..1] ?
<pdop> xsd:decimal </pdop> [0..1] ?
<ageofdgpsdata> xsd:decimal </ageofdgpsdata> [0..1] ?
<dgpsid> dgpsStationType </dgpsid> [0..1] ?
<extensions> extensionsType </extensions> [0..1] ?
	 */
	private Double latitude;
	private Double longitude;
	private Double elevation;
	private Date time;
	private Double magneticDeclination;
	private Double geoidHeight;
	private String name;
	private String comment;
	private String description;
	private String src;
	private String sym;
	private String type;
	private FixType fix;
	private Integer sat;
	private Double hdop;
	private Double vdop;
	private Double pdop;
	private Double ageOfGPSData;
	private Integer dgpsid;
	
	
	/**
	 * Returns the latitude of this waypoint.
	 * @return a Double value representing the latitude of this waypoint.
	 */
	public Double getLatitude() {
		return latitude;
	}
	
	/**
	 * Setter for waypoint latitude property. This maps to "lat" attribute value.
	 * @param latitude a Doube value representing the latitude of this waypoint.
	 */
	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}
	
	/**
	 * Returns the longitude of this waypoint.
	 * @return a Double value representing the longitude of this waypoint.
	 */
	public Double getLongitude() {
		return longitude;
	}
	
	/**
	 * Setter for waypoint longitude property. This maps to "long" attribute value.
	 * @param longitude a Doube value representing the longitude of this waypoint.
	 */
	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}
	
	/**
	 * Returns the elevation of this waypoint.
	 * @return A Double representing the elevation of this waypoint.
	 */
	public Double getElevation() {
		return elevation;
	}
	
	/**
	 * Setter for waypoint elevation property. This maps to &lt;ele&gt; tag value.
	 * @param elevation a Double value representing the elevation of this waypoint.
	 */
	public void setElevation(Double elevation) {
		this.elevation = elevation;
	}
	
	/**
	 * Returns the time of this waypoint.
	 * @return a Date representing the name of this waypoint.
	 */
	public Date getTime() {
		return time;
	}
	
	/**
	 * Setter for waypoint time property. This maps to &lt;time&gt; tag value.
	 * @param time a Date representing the time of this waypoint.
	 */
	public void setTime(Date time) {
		this.time = time;
	}
	
	/**
	 * Returns the magnetic declination of this waypoint.
	 * @return A Double representing the magnetic declination of this waypoint.
	 */
	public Double getMagneticDeclination() {
		return magneticDeclination;
	}
	
	/**
	 * Setter for waypoint magnetic declination property. This maps to &lt;magvar&gt; tag value.
	 * @param magneticDeclination A String representing the magnetic declination of this waypoint.
	 */
	public void setMagneticDeclination(Double magneticDeclination) {
		this.magneticDeclination = magneticDeclination;
	}
	
	/**
	 * Returns the geoid height of this waypoint.
	 * @return A String representing the geoid height of this waypoint.
	 */
	public Double getGeoidHeight() {
		return geoidHeight;
	}
	
	/**
	 * Setter for waypoint geoid height property. This maps to &lt;geoidheight&gt; tag value.
	 * @param geoidHeight A String representing the geoid height of this waypoint.
	 */
	public void setGeoidHeight(Double geoidHeight) {
		this.geoidHeight = geoidHeight;
	}
	
	/**
	 * Returns the name of this waypoint.
	 * @return A String representing the name of this waypoint.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Setter for waypoint name property. This maps to &lt;name&gt; tag value.
	 * @param name A String representing the name of this waypoint.
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	/**
	 * Returns the comment of this waypoint.
	 * @return a String representing the comment of this waypoint.
	 */
	public String getComment() {
		return comment;
	}
	
	/**
	 * Setter for waypoint comment property. This maps to &lt;cmt&gt; tag value.
	 * @param comment A String representing the comment of this waypoint.
	 */
	public void setComment(String comment) {
		this.comment = comment;
	}
	
	/**
	 * Returns the description of this waypoint.
	 * @return a String representing the description of this waypoint.
	 */
	public String getDescription() {
		return description;
	}
	
	/**
	 * Setter for waypoint description property. This maps to &lt;desc&gt; tag value.
	 * @param description A String representing the description of this waypoint.
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * Returns the src of this waypoint.
	 * @return A String representing the src of this waypoint.
	 */
	public String getSrc() {
		return src;
	}
	
	/**
	 * Setter for waypoint src property. This maps to &lt;src&gt; tag value.
	 * @param src a String representing the src of this waypoint.
	 */
	public void setSrc(String src) {
		this.src = src;
	}
	
	/**
	 * Returns the sym of this waypoint.
	 * @return A String representing the sym of this waypoint.
	 */
	public String getSym() {
		return sym;
	}
	
	/**
	 * Setter for waypoint sym property. This maps to &lt;sym&gt; tag value.
	 * @param sym a String representing the sym of this waypoint.
	 */
	public void setSym(String sym) {
		this.sym = sym;
	}
	
	/**
	 * Returns the type of this waypoint.
	 * @return A String representing the type of this waypoint.
	 */
	public String getType() {
		return type;
	}
	
	/**
	 * Setter for waypoint type property. This maps to &lt;type&gt; tag value.
	 * @param type a String representing the type of this waypoint.
	 */
	public void setType(String type) {
		this.type = type;
	}
	
	/**
	 * Returns the fix of this waypoint.
	 * @return A {@link FixType} representing the fix of this waypoint.
	 */
	public FixType getFix() {
		return fix;
	}
	
	/**
	 * Setter for waypoint fix property. This maps to &lt;fix&gt; tag value.
	 * @param fix a {@link FixType} representing the fix of this waypoint.
	 */
	public void setFix(FixType fix) {
		this.fix = fix;
	}
	
	/**
	 * Returns the sat of this waypoint.
	 * @return an Integer representing the sat of this waypoint.
	 */
	public Integer getSat() {
		return sat;
	}
	
	/**
	 * Setter for waypoint sat property. This maps to &lt;sat&gt; tag value.
	 * @param sat an Integer representing the sat of this waypoint.
	 */
	public void setSat(Integer sat) {
		this.sat = sat;
	}
	
	/**
	 * Returns the hdop of this waypoint.
	 * @return a Double representing the hdop of this waypoint.
	 */
	public Double getHdop() {
		return hdop;
	}
	
	/**
	 * Setter for waypoint hdop property. This maps to &lt;hdop&gt; tag value.
	 * @param hdop a Double representing the name of this waypoint.
	 */
	public void setHdop(Double hdop) {
		this.hdop = hdop;
	}
	
	/**
	 * Returns the vdop of this waypoint.
	 * @return a Double representing the vdop of this waypoint.
	 */
	public Double getVdop() {
		return vdop;
	}
	
	/**
	 * Setter for waypoint vdop property. This maps to &lt;vdop&gt; tag value.
	 * @param vdop A String representing the vdop of this waypoint.
	 */
	public void setVdop(Double vdop) {
		this.vdop = vdop;
	}
	
	/**
	 * Returns the pdop of this waypoint.
	 * @return a Double representing the pdop of this waypoint.
	 */
	public Double getPdop() {
		return pdop;
	}
	
	/**
	 * Setter for waypoint pdop property. This maps to &lt;pdop&gt; tag value.
	 * @param pdop a Double representing the pdop of this waypoint.
	 */
	public void setPdop(Double pdop) {
		this.pdop = pdop;
	}
	
	/**
	 * Returns the ageOfGPSData of this waypoint.
	 * @return a Double representing the ageOfGPSData of this waypoint.
	 */
	public Double getAgeOfGPSData() {
		return ageOfGPSData;
	}
	
	/**
	 * Setter for waypoint ageOfGPSData property. This maps to &lt;ageOfGPSData&gt; tag value.
	 * @param ageOfGPSData A String representing the ageOfGPSData of this waypoint.
	 */
	public void setAgeOfGPSData(Double ageOfGPSData) {
		this.ageOfGPSData = ageOfGPSData;
	}
	
	/**
	 * Returns the dgpsid of this waypoint.
	 * @return an Integer representing the dgpsid of this waypoint.
	 */
	public Integer getDgpsid() {
		return dgpsid;
	}
	
	/**
	 * Setter for waypoint dgpsid property. This maps to &lt;dgpsid&gt; tag value.
	 * @param dgpsid an Integer representing the dgpsid of this waypoint.
	 */
	public void setDgpsid(Integer dgpsid) {
		this.dgpsid = dgpsid;
	}
	
	/**
	 * Returns a String representation of this waypoint.
	 */
	public String toString() {
		StringBuffer sb = new StringBuffer();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		String date="";
		if(time != null) {
			date = sdf.format(time);
		}
		sb.append("[");
		sb.append("name:'"+name+"' ");
		sb.append("lat:" + latitude + " ");
		sb.append("lon:"+longitude + " ");
		sb.append("elv:"+elevation +" ");
		sb.append("time:"+date+ " ");
		sb.append("fix:"+fix+ " ");
		if(extensionData != null) {
			sb.append("extensions:{");
			Iterator<String> it = extensionData.keySet().iterator();
			while(it.hasNext()) {
				sb.append(it.next());
				if(it.hasNext()) {
					sb.append(",");
				}
			}
			sb.append("}");
		}
		sb.append("]");
		return sb.toString();
	}
}
