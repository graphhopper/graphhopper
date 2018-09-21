package com.graphhopper.routing.profiles.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.StringEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;

import java.util.Arrays;
import java.util.List;

public class SurfaceParser extends AbstractTagParser {
    private final StringEncodedValue enc;

    public SurfaceParser() {
        super(EncodingManager.SURFACE);
        List<String> surfaceList = Arrays.asList("_default", "asphalt", "unpaved", "paved", "gravel",
                "ground", "dirt", "grass", "concrete", "paving_stones", "sand", "compacted", "cobblestone", "mud", "ice");
        enc = new StringEncodedValue(EncodingManager.SURFACE, surfaceList, "_default");
    }

    public StringEncodedValue getEnc() {
        return enc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, long allowed, long relationFlags) {
        String surfaceValue = way.getTag("surface");
        int intValue = enc.indexOf(surfaceValue);
        enc.setInt(false, edgeFlags, intValue);
        return edgeFlags;
    }
}