package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class approaches the storage of edge properties via orchestrating multiple Property-objects like maxspeed and
 * highway type that can be accessed in a Weighting and will be feeded in the Import (via PropertyParser).
 */
public class EncodingManager2 extends EncodingManager {

    private Map<String, Property> properties = new HashMap<>();

    private final PropertyParser parser;
    private final int extendedDataSize;

    public EncodingManager2(PropertyParser parser, int extendedDataSize) {
        // we have to add a fake encoder that uses 0 bits for backward compatibility with EncodingManager
        super(new CarFlagEncoder(0, 1, 0) {
            @Override
            public int defineWayBits(int index, int shift) {
                return shift;
            }
        });
        this.parser = parser;
        // Everything is int-based: the dataIndex, the Property hierarchy with the 'int'-value and the offset
        // TODO should we use a long or byte-based approach instead?
        this.extendedDataSize = Math.min(1, extendedDataSize / 4) * 4;
    }

    /**
     * This method freezes the properties and defines their shift, dataIndex etc
     */
    public EncodingManager2 init(List<Property> properties) {
        if (!this.properties.isEmpty())
            throw new IllegalStateException("Cannot call init multiple times");

        Property.InitializerConfig initializer = new Property.InitializerConfig();
        for (Property prop : properties) {
            prop.init(initializer);
            this.properties.put(prop.getName(), prop);
        }

        return this;
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        parser.parse(way, edge, properties.values());
    }

    public int getBytesForFlags() {
        return 4;
    }

    /**
     * Size of arbitrary storage per edge and in bytes
     */
    public int getExtendedDataSize() {
        return extendedDataSize;
    }

    public boolean supports(String encoder) {
        throw new IllegalStateException("not implemented");
    }

    public FlagEncoder getEncoder(String name) {
        throw new IllegalStateException("not implemented");
    }

    public long acceptWay(ReaderWay way) {
        throw new IllegalStateException("not implemented");
    }

    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        throw new IllegalStateException("not implemented");
    }

    public long handleWayTags(ReaderWay way, long includeWay, long relationFlags) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public String toString() {
        String str = "";
        for (Property p : properties.values()) {
            if (str.length() > 0)
                str += ", ";
            str += p.getName();
        }

        return str.toString();
    }

    public String toDetailsString() {
        String str = "";
        for (Property p : properties.values()) {
            if (str.length() > 0)
                str += ", ";
            str += p.toString();
        }

        return str.toString();
    }

    public long flagsDefault(boolean forward, boolean backward) {
        // TODO deprecate flags
        return 0;
    }

    /**
     * Reverse flags, to do so all encoders are called.
     */
    public long reverseFlags(long flags) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public int hashCode() {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public boolean equals(Object obj) {
        throw new IllegalStateException("not implemented");
    }

    /**
     * Analyze tags on osm node. Store node tags (barriers etc) for later usage while parsing way.
     */
    public long handleNodeTags(ReaderNode node) {
        throw new IllegalStateException("not implemented");
    }

    public EncodingManager setEnableInstructions(boolean enableInstructions) {
        throw new IllegalStateException("not implemented");
    }

    public EncodingManager setPreferredLanguage(String preferredLanguage) {
        throw new IllegalStateException("not implemented");
    }

    public List<FlagEncoder> fetchEdgeEncoders() {
        throw new IllegalStateException("not implemented");
    }

    public boolean needsTurnCostsSupport() {
        // TODO
        return false;
    }

    public <T> T getProperty(String key, Class<T> clazz) {
        Property prop = properties.get(key);
        if (prop == null)
            throw new IllegalArgumentException("Cannot find property " + key + " in existing: " + properties);
        return (T) prop;
    }
}
