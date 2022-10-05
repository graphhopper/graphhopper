package com.graphhopper.reader.osm;

import com.graphhopper.coll.GHLongLongHashMap;
import com.graphhopper.storage.IntsRef;

public class RelationFlagsData {
    private final IntsRef tempRelFlags;
    public GHLongLongHashMap osmWayIdToRelationFlagsMap = new GHLongLongHashMap(200, .5f);

    public RelationFlagsData(IntsRef tempRelFlags) {
    	this.tempRelFlags = tempRelFlags;
    }
    
    IntsRef getRelFlagsMap(long osmId) {
        long relFlagsAsLong = osmWayIdToRelationFlagsMap.get(osmId);
        tempRelFlags.ints[0] = (int) relFlagsAsLong;
        tempRelFlags.ints[1] = (int) (relFlagsAsLong >> 32);
        return tempRelFlags;
    }

    void putRelFlagsMap(long osmId, IntsRef relFlags) {
        long relFlagsAsLong = ((long) relFlags.ints[1] << 32) | (relFlags.ints[0] & 0xFFFFFFFFL);
        osmWayIdToRelationFlagsMap.put(osmId, relFlagsAsLong);
    }
}
