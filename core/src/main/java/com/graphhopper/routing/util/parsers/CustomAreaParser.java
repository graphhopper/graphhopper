package com.graphhopper.routing.util.parsers;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.graphhopper.config.CustomArea;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.routing.ev.StringEncodedValue;
import com.graphhopper.routing.util.area.CustomAreaLookup;
import com.graphhopper.routing.util.area.LookupResult;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.shapes.GHPoint;

public class CustomAreaParser implements TagParser {
    
    private static final String CUSTOM_AREA_EV_PREFIX = CustomArea.key("");
    private final Map<String,StringEncodedValue> evMap = new HashMap<>();
    
    public static void injectCustomAreas(CustomAreaLookup customAreaLookup, ReaderWay way) {
        if (customAreaLookup == CustomAreaLookup.EMPTY) {
            return;
        }
        
        GHPoint estimatedCenter = way.getTag("estimated_center", null);
        if (estimatedCenter == null) {
            return;
        }
        
        LookupResult result = customAreaLookup.lookup(estimatedCenter.lat, estimatedCenter.lon);
        way.setTag("custom_areas", result.getAreas());
        way.setTag("spatial_rule_set", result.getRuleSet());
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue) {
        for (EncodedValue ev : lookup.getEncodedValues()) {
            if (ev instanceof StringEncodedValue && ev.getName().startsWith(CUSTOM_AREA_EV_PREFIX)) {
                evMap.put(ev.getName(), (StringEncodedValue) ev);
            }
        }
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, boolean ferry, IntsRef relationFlags) {
        List<CustomArea> assignedAreas = way.getTag("custom_areas", Collections.emptyList());
        for (CustomArea assignedArea : assignedAreas) {
            String key = assignedArea.getEncodedValue();
            if (key != null && !key.isEmpty()) {
                StringEncodedValue ev = evMap.get(key);
                if (ev != null) {
                    ev.setString(false, edgeFlags, assignedArea.getId());
                }
            }
        }
        return edgeFlags;
    }

}
