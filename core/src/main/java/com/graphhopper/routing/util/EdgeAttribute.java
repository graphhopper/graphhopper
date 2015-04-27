package com.graphhopper.routing.util;

import com.graphhopper.reader.Way;

public interface EdgeAttribute {
	boolean isValidForWay(Way way);
	long getValue();
	String name();
	boolean representedIn(String[] attributes);
}
