package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;

import java.util.*;

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

    public EncodingManager2 init(Property... properties) {
        return init(Arrays.asList(properties));
    }

    /**
     * This method freezes the properties and defines their shift, dataIndex etc
     * <p>
     * Note, that the order of the collection is not guaranteed being used as storage order and can change to optimize bit usage.
     */
    public EncodingManager2 init(Collection<Property> properties) {
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
    public long handleWayTags(ReaderWay way, long includeWay, long relationFlags) {
    }

    public EdgeProperties handleWay(ReaderWay way) {
        // TODO store int array per edge -> different method call in OSMReader necessary
        return parser.parse(way, edge, properties.values());
    }

    // TODO parsing and filtering is highly related (e.g. for parsing we detect a tag and if the value is parsable and the same is necessary for filtering)
    // we should create a streamed approach instead:
    // property.filter(way).parse() and skip parse
    private Collection<ReaderWayFilter> filters = new ArrayList<>();

    // TODO per default accept everything with highway
    {
        filters.add(new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return way.getTag("highway") != null;
            }
        });
    }

    public interface ReaderWayFilter {
        boolean accept(ReaderWay way);
    }

    public void addReaderWayFilter(ReaderWayFilter rwf) {
        filters.add(rwf);
    }

    public long acceptWay(ReaderWay way) {
        for (ReaderWayFilter filter : filters) {
            if (!filter.accept(way))
                return 0;
        }
        return 1;
    }

    /**
     * Size of arbitrary storage per edge and in bytes
     */
    public int getExtendedDataSize() {
        return extendedDataSize;
    }

    // TODO should we add convenient getters like getStringProperty etc?
    public <T> T getProperty(String key, Class<T> clazz) {
        Property prop = properties.get(key);
        if (prop == null)
            throw new IllegalArgumentException("Cannot find property " + key + " in existing: " + properties);
        return (T) prop;
    }

    @Override
    public boolean supports(String encoder) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public FlagEncoder getEncoder(String name) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
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

    @Override
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
        // TODO deprecate usage of flags
        return 0;
    }

    public int getBytesForFlags() {
        return 4;
    }

    /**
     * Reverse flags, to do so all encoders are called.
     */
    @Override
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
    @Override
    public long handleNodeTags(ReaderNode node) {
        // TODO not implemented
        return 0;
    }

    @Override
    public EncodingManager setEnableInstructions(boolean enableInstructions) {
        // TODO not implemented
        return this;
    }

    @Override
    public EncodingManager setPreferredLanguage(String preferredLanguage) {
        // TODO not implemented
        return this;
    }

    @Override
    public List<FlagEncoder> fetchEdgeEncoders() {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public boolean needsTurnCostsSupport() {
        // TODO properties per node
        return false;
    }
}
