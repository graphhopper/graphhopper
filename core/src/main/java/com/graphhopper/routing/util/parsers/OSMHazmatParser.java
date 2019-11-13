package com.graphhopper.routing.util.parsers;

import java.util.List;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.Hazmat;
import com.graphhopper.routing.util.EncodingManager.Access;
import com.graphhopper.storage.IntsRef;

public class OSMHazmatParser implements TagParser {
    
    private final EnumEncodedValue<Hazmat> hazEnc;
    
    public OSMHazmatParser() {
        this.hazEnc = new EnumEncodedValue<>(Hazmat.KEY, Hazmat.class);
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup,
                    List<EncodedValue> registerNewEncodedValue) {
        registerNewEncodedValue.add(hazEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, Access access,
                    long relationFlags) {
        if (readerWay.hasTag("hazmat", "no")) {
            hazEnc.setEnum(false, edgeFlags, Hazmat.NO);
        }
        return edgeFlags;
    }

}
