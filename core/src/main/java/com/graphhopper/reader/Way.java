package com.graphhopper.reader;

import gnu.trove.list.TLongList;


public interface Way extends RoutingElement {

	TLongList getNodes();


}
