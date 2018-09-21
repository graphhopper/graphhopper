package com.graphhopper.routing.profiles.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.StringEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Arrays;
import java.util.List;

/**
 * Stores the environment of the road like bridge or tunnel. Previously called "transport_mode" in DataFlagEncoder.
 */
public class RoadEnvironmentParser extends AbstractTagParser {
    private final StringEncodedValue roadEnvEnc;
    private final List<String> roadEnvList;
    private final int transportModeTunnelValue;
    private final int transportModeBridgeValue;
    private final int transportModeFordValue;

    public RoadEnvironmentParser() {
        super(EncodingManager.ROAD_ENV);
        roadEnvList = Arrays.asList("_default", "bridge", "tunnel", "ford", "aerialway");
        roadEnvEnc = new StringEncodedValue(EncodingManager.ROAD_ENV, roadEnvList, "_default");

        transportModeTunnelValue = roadEnvEnc.indexOf("tunnel");
        transportModeBridgeValue = roadEnvEnc.indexOf("bridge");
        transportModeFordValue = roadEnvEnc.indexOf("ford");
    }

    public StringEncodedValue getEnc() {
        return roadEnvEnc;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, long allowed, long relationFlags) {
        String roadEnv = roadEnvList.get(0);
        for (String tm : roadEnvList) {
            if (way.hasTag(tm)) {
                roadEnv = tm;
                break;
            }
        }

        roadEnvEnc.setString(false, edgeFlags, roadEnv);
        return edgeFlags;
    }

    public boolean isTransportModeTunnel(EdgeIteratorState edge) {
        return roadEnvEnc.getInt(false, edge.getFlags()) == transportModeTunnelValue;
    }

    public boolean isTransportModeBridge(EdgeIteratorState edge) {
        return roadEnvEnc.getInt(false, edge.getFlags()) == transportModeBridgeValue;
    }

    public boolean isTransportModeFord(IntsRef edgeFlags) {
        return roadEnvEnc.getInt(false, edgeFlags) == transportModeFordValue;
    }
}
