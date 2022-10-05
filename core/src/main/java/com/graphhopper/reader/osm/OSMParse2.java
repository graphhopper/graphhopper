package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderWay;

public class OSMParse2 implements OSMParseInterface {
	private WayPreprocessor wayPreprocessor;
	
	public OSMParse2(WayPreprocessor wayPreprocessor) {
		this.wayPreprocessor = wayPreprocessor;
	}
		
    @Override
    public void handleWay(ReaderWay way) {
    	wayPreprocessor.preprocessWay(way);
    }
}
