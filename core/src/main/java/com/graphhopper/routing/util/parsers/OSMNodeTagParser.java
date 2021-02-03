package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OSMNodeTagParser implements NodeTagParser {

    private final List<TMInfo> tmInfos;

    private EnumEncodedValue<Barrier> barrierEnc;
    private EnumEncodedValue<Barrier> transferBarrierEnc;

    private BooleanEncodedValue lockedEnc;
    private BooleanEncodedValue transferLockedEnc;

    private EnumEncodedValue<RoadEnvironment> roadEnvironmentEnc;
    private BooleanEncodedValue transferFordEnc;

    public OSMNodeTagParser(Set<TransportationMode> transportationModes) {
        if (transportationModes.isEmpty())
            throw new IllegalArgumentException("At least one TransportationMode required but list was empty.");
        this.tmInfos = new ArrayList<>(transportationModes.size());
        for (TransportationMode tm : transportationModes) {
            TMInfo info = new TMInfo();
            info.restrictions = OSMRoadAccessParser.toOSMRestrictions(tm);
            info.transportationMode = tm;
            info.accessEnc = new EnumEncodedValue<>(tm.getAccessName(), RoadAccess.class);
            info.transferAccessEnc = new EnumEncodedValue<>(tm.getAccessName(), RoadAccess.class);
            tmInfos.add(info);
        }
    }

    @Override
    public void createNodeEncodedValues(EncodedValueLookup lookup, List<EncodedValue> nodeEncodedValues,
                                        List<EncodedValue> edgeEncodedValues) {
        roadEnvironmentEnc = lookup.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        nodeEncodedValues.add(transferFordEnc = new SimpleBooleanEncodedValue("ford"));

        edgeEncodedValues.add(barrierEnc = new EnumEncodedValue<>(Barrier.KEY, Barrier.class));
        nodeEncodedValues.add(transferBarrierEnc = new EnumEncodedValue<>(Barrier.KEY, Barrier.class));

        edgeEncodedValues.add(lockedEnc = new SimpleBooleanEncodedValue("barrier_locked"));
        nodeEncodedValues.add(transferLockedEnc = new SimpleBooleanEncodedValue("barrier_locked"));

        edgeEncodedValues.addAll(tmInfos.stream().map(i -> i.accessEnc).collect(Collectors.toList()));
        nodeEncodedValues.addAll(tmInfos.stream().map(i -> i.transferAccessEnc).collect(Collectors.toList()));
    }

    @Override
    public IntsRef handleNodeTags(IntsRef nodeFlags, ReaderNode node) {
        for (TMInfo i : tmInfos) {
            String firstTag = node.getFirstPriorityTag(i.restrictions);
            i.transferAccessEnc.setEnum(false, nodeFlags, RoadAccess.find(firstTag));
        }

        transferBarrierEnc.setEnum(false, nodeFlags, Barrier.find(node.getTag("barrier")));
        if (node.hasTag("highway", "ford") || node.hasTag("ford", "yes"))
            transferFordEnc.setBool(false, nodeFlags, true);
        if (node.hasTag("locked", "yes"))
            transferLockedEnc.setBool(false, nodeFlags, true);

        return nodeFlags;
    }

    @Override
    public IntsRef copyNodeToEdge(IntsRef nodeFlags, IntsRef edgeFlags) {
        // copy value into different bit range
        Barrier barrier = transferBarrierEnc.getEnum(false, nodeFlags);
        barrierEnc.setEnum(false, edgeFlags, barrier);

        for (TMInfo i : tmInfos) {
            RoadAccess ra = i.transferAccessEnc.getEnum(false, nodeFlags);
            i.accessEnc.setEnum(false, edgeFlags, ra);
        }

        if (transferFordEnc.getBool(false, nodeFlags))
            roadEnvironmentEnc.setEnum(false, edgeFlags, RoadEnvironment.FORD);
        if (transferLockedEnc.getBool(false, nodeFlags))
            lockedEnc.setBool(false, edgeFlags, true);
        return edgeFlags;
    }

    private static class TMInfo {
        TransportationMode transportationMode;
        EnumEncodedValue<RoadAccess> accessEnc;
        EnumEncodedValue<RoadAccess> transferAccessEnc;
        List<String> restrictions;

        @Override
        public String toString() {
            return transportationMode.toString();
        }
    }
}
