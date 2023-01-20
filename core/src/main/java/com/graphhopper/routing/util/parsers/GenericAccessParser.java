package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.osm.conditional.ConditionalOSMTagInspector;
import com.graphhopper.reader.osm.conditional.ConditionalTagInspector;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.TransportationMode;
import com.graphhopper.storage.IntsRef;

import java.util.*;

public abstract class GenericAccessParser implements TagParser {
    static final Collection<String> FERRIES = Arrays.asList("shuttle_train", "ferry");
    static final Collection<String> ONEWAYS = Arrays.asList("yes", "true", "1", "-1");
    static final Collection<String> INTENDED = Arrays.asList("yes", "designated", "official", "permissive");

    // order is important
    protected final List<String> restrictions = new ArrayList<>(5);
    protected final Set<String> restrictedValues = new HashSet<>(5);

    protected final Set<String> intendedValues = new HashSet<>(INTENDED);
    protected final Set<String> ferries = new HashSet<>(FERRIES);
    protected final Set<String> oneways = new HashSet<>(ONEWAYS);
    // http://wiki.openstreetmap.org/wiki/Mapfeatures#Barrier
    protected final Set<String> barriers = new HashSet<>(5);
    protected final BooleanEncodedValue accessEnc;
    private boolean blockFords = true;
    private ConditionalTagInspector conditionalTagInspector;

    protected GenericAccessParser(BooleanEncodedValue accessEnc, TransportationMode transportationMode) {
        this.accessEnc = accessEnc;

        restrictions.addAll(OSMRoadAccessParser.toOSMRestrictions(transportationMode));
    }

    public GenericAccessParser init(DateRangeParser dateRangeParser) {
        setConditionalTagInspector(new ConditionalOSMTagInspector(Collections.singletonList(dateRangeParser),
                restrictions, restrictedValues, intendedValues, false));
        return this;
    }

    protected void setConditionalTagInspector(ConditionalTagInspector inspector) {
        conditionalTagInspector = inspector;
    }

    public boolean isBlockFords() {
        return blockFords;
    }

    protected void blockFords(boolean blockFords) {
        this.blockFords = blockFords;
    }

    protected void blockPrivate(boolean blockPrivate) {
        if (!blockPrivate) {
            if (!restrictedValues.remove("private"))
                throw new IllegalStateException("no 'private' found in restrictedValues");
            intendedValues.add("private");
        }
    }

    public ConditionalTagInspector getConditionalTagInspector() {
        return conditionalTagInspector;
    }

    /**
     * Updates the given edge flags based on node tags
     */
    protected void handleNodeTags(IntsRef edgeFlags, Map<String, Object> nodeTags) {
        if (!nodeTags.isEmpty()) {
            // for now we just create a dummy reader node, because our encoders do not make use of the coordinates anyway
            ReaderNode readerNode = new ReaderNode(0, 0, 0, nodeTags);
            // block access for barriers
            if (isBarrier(readerNode)) {
                BooleanEncodedValue accessEnc = getAccessEnc();
                accessEnc.setBool(false, edgeFlags, false);
                accessEnc.setBool(true, edgeFlags, false);
            }
        }
    }

    /**
     * @return true if the given OSM node blocks access for this vehicle, false otherwise
     */
    public boolean isBarrier(ReaderNode node) {
        // note that this method will be only called for certain nodes as defined by OSMReader!
        String firstValue = node.getFirstPriorityTag(restrictions);
        if (restrictedValues.contains(firstValue) || node.hasTag("locked", "yes"))
            return true;
        else if (intendedValues.contains(firstValue))
            return false;
        else if (node.hasTag("barrier", barriers))
            return true;
        else
            return blockFords && node.hasTag("ford", "yes");
    }

    public final BooleanEncodedValue getAccessEnc() {
        return accessEnc;
    }

    public final List<String> getRestrictions() {
        return restrictions;
    }

    public final String getName() {
        String name = accessEnc.getName().replaceAll("_access", "");
        // safety check for the time being
        String expectedKey = VehicleAccess.key(name);
        if (!accessEnc.getName().equals(expectedKey))
            throw new IllegalStateException("Expected access key '" + expectedKey + "', but got: " + accessEnc.getName());
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }
}
