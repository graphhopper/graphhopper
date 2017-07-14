package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager08;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

import java.util.*;

/**
 * This class approaches the storage of edge parserToValueMap via orchestrating multiple EncodedValue-objects like maxspeed and
 * highway type that can be accessed in a Weighting and will be feeded in the Import (via TagsParser).
 */
public class EncodingManager extends EncodingManager08 {

    /**
     * for backward compatibility we have to specify an encoder name ("vehicle")
     */
    public static final String ENCODER_NAME = "weighting";
    private Map<String, EncodedValue> encodedValues = new HashMap<>();
    private Map<TagParser, EncodedValue> parsers = new HashMap<>();
    private final TagsParser parser;
    private final int extendedDataSize;
    private boolean initialized = false;
    private final FlagEncoder mockEncoder;

    public EncodingManager(TagsParser parser, int extendedDataSize) {
        // we have to add a fake encoder that uses 0 bits for backward compatibility with EncodingManager08
        super(new CarFlagEncoder(0, 1, 0) {
            @Override
            public int defineWayBits(int index, int shift) {
                return shift;
            }
        });
        this.mockEncoder = fetchEdgeEncoders().get(0);

        this.parser = parser;
        // Everything is int-based: the dataIndex, the EncodedValue hierarchy with the 'int'-value and the offset
        // TODO should we use a long or byte-based approach instead?
        this.extendedDataSize = Math.min(1, extendedDataSize / 4) * 4;
    }

    public EncodingManager add(TagParser parser, EncodedValue ev) {
        if (!parser.getName().equals(ev.getName()))
            throw new IllegalArgumentException("TagParser and EncodedValue must have the same name but was " + parser.getName() + " vs. " + ev.getName());

        this.encodedValues.put(ev.getName(), ev);
        this.parsers.put(parser, ev);
        return this;
    }

    /**
     * This method freezes the parserToValueMap and defines their shift, dataIndex etc
     * <p>
     * Note, that the order of the collection is not guaranteed being used as storage order and can change to optimize bit usage.
     */
    public EncodingManager init() {
        if (initialized)
            throw new IllegalStateException("Cannot call init multiple times");

        initialized = true;
        EncodedValue.InitializerConfig initializer = new EncodedValue.InitializerConfig();
        for (EncodedValue ev : parsers.values()) {
            ev.init(initializer);
        }

        return this;
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

    @Override
    public long handleWayTags(ReaderWay way, long includeWay, long relationFlags) {
        // for backward compatibility return flags=1111...
        // applyWayTags does the property storage
        return ~0L;
    }

    @Override
    public void applyWayTags(ReaderWay way, EdgeIteratorState edge) {
        parser.parse(way, edge, parsers);
    }

    /**
     * Size of arbitrary storage per edge and in bytes
     */
    public int getExtendedDataSize() {
        return extendedDataSize;
    }

    // TODO should we add convenient getters like getStringProperty etc?
    public <T extends EncodedValue> T getEncodedValue(String key, Class<T> clazz) {
        EncodedValue prop = encodedValues.get(key);
        if (prop == null)
            throw new IllegalArgumentException("Cannot find encoded value " + key + " in existing: " + encodedValues);
        return (T) prop;
    }

    @Override
    public boolean supports(String encoder) {
        return encoder.equals(ENCODER_NAME);
    }

    @Override
    public FlagEncoder getEncoder(String name) {
        return mockEncoder;
    }

    @Override
    public long handleRelationTags(ReaderRelation relation, long oldRelationFlags) {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public String toString() {
        String str = "";
        for (EncodedValue p : encodedValues.values()) {
            if (str.length() > 0)
                str += ", ";
            str += p.getName();
        }

        return str.toString();
    }

    @Override
    public String toDetailsString() {
        String str = "";
        for (EncodedValue p : encodedValues.values()) {
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
        return flags;
    }

    @Override
    public int hashCode() {
        return encodedValues.hashCode();
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
    public EncodingManager08 setEnableInstructions(boolean enableInstructions) {
        // TODO not implemented
        return this;
    }

    @Override
    public EncodingManager08 setPreferredLanguage(String preferredLanguage) {
        // TODO not implemented
        return this;
    }

    @Override
    public List<FlagEncoder> fetchEdgeEncoders() {
        return super.fetchEdgeEncoders();
    }

    @Override
    public boolean needsTurnCostsSupport() {
        // TODO parserToValueMap per node
        return false;
    }
}
