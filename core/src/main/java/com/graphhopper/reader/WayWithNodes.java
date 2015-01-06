package com.graphhopper.reader;

import gnu.trove.list.TLongList;

public interface WayWithNodes extends Way {

    TLongList getNodes();

}
