/*
 * GPXConstants.java
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

package org.alternativevision.gpx;

public interface GPXConstants {
	/*GPX nodes and attributes*/
	public static final String GPX_NODE = "gpx";
	public static final String WPT_NODE = "wpt";
	public static final String TRK_NODE = "trk";
	public static final String VERSION_ATTR = "version";
	public static final String CREATOR_ATTR = "creator";
	/*End GPX nodes and attributes*/
	
	/*Waypoint nodes and attributes*/
	public static final String LAT_ATTR = "lat";
	public static final String LON_ATTR = "lon";
	public static final String ELE_NODE = "ele";
	public static final String TIME_NODE = "time";
	public static final String NAME_NODE = "name";
	public static final String CMT_NODE = "cmt";
	public static final String DESC_NODE = "desc";
	public static final String SRC_NODE = "src";
	public static final String MAGVAR_NODE = "magvar";
	public static final String GEOIDHEIGHT_NODE = "geoidheight";
	public static final String LINK_NODE = "link";
	public static final String SYM_NODE = "sym";
	public static final String TYPE_NODE = "type";
	public static final String FIX_NODE = "fix";
	public static final String SAT_NODE = "sat";
	public static final String HDOP_NODE = "hdop";
	public static final String VDOP_NODE = "vdop";
	public static final String PDOP_NODE = "pdop";
	public static final String AGEOFGPSDATA_NODE = "ageofdgpsdata";
	public static final String DGPSID_NODE = "dgpsid";
	public static final String EXTENSIONS_NODE = "extensions";
	/*End Waypoint nodes and attributes*/
	
	/*Track nodes and attributes*/
	public static final String NUMBER_NODE = "number";
	public static final String TRKSEG_NODE = "trkseg";
	public static final String TRKPT_NODE = "trkpt";
	/*End Track nodes and attributes*/
	
	/*Route Nodes*/
	public static final String RTE_NODE = "rte";
	public static final String RTEPT_NODE = "rtept";
	/*End route nodes*/

}
