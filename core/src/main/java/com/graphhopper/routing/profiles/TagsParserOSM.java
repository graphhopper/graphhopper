package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Map;

/**
 * This class orchestrates the call of multiple TagParser its 'parse' method.
 */
public class TagsParserOSM implements TagsParser {

    @Override
    public void parse(ReaderWay way, EdgeIteratorState edgeState, Map<TagParser, EncodedValue> parsers) {
        for (Map.Entry<TagParser, EncodedValue> entry : parsers.entrySet()) {
            Object value = entry.getKey().parse(way);
            if (value == null) {
                continue;
            }

            EncodedValue encodedValue = entry.getValue();

            // TODO how can we avoid the if-instanceof stuff?
            // for this it is especially ugly that the order is important as e.g. because of
            // DecimalEncodedValue extends IntEncodedValue the decimal has to come before int, same for bit
            if (encodedValue instanceof StringEncodedValue) {
                edgeState.set((StringEncodedValue) encodedValue, (String) value);
            } else if (encodedValue instanceof DecimalEncodedValue) {
                edgeState.set((DecimalEncodedValue) encodedValue, ((Number) value).doubleValue());
            } else if (encodedValue instanceof BitEncodedValue) {
                edgeState.set((BitEncodedValue) encodedValue, (Boolean) value);
            } else if (encodedValue instanceof IntEncodedValue) {
                edgeState.set((IntEncodedValue) encodedValue, ((Number) value).intValue());
            } else {
                throw new IllegalArgumentException("encodedValue " + encodedValue.getClass() + " not supported: " + encodedValue);
            }
        }
    }
}
