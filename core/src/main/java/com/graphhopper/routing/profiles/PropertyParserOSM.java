package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Collection;

public class PropertyParserOSM implements PropertyParser {

    @Override
    public void parse(ReaderWay way, EdgeIteratorState edgeState, Collection<EncodedValue> properties) {
        // TODO Should we better decouple OSM from EncodedValue via a separate class like HighwayProperty that uses a StringEncodedValue?
        // especially ugly is that the order is important as e.g. DoubleEncodedValue extends IntEncodedValue
        // TODO how can we avoid parsing for all properties under certain circumstances like highway=rail -> build a pipe or a filtering system somehow?
        for (EncodedValue encodedValue : properties) {
            // TODO how can we avoid the if-instanceof stuff?
            Object value = encodedValue.parse(way);
            if (value == null)
                continue;

            if (encodedValue instanceof StringEncodedValue) {
                edgeState.set((StringEncodedValue) encodedValue, (String) value);
            } else if (encodedValue instanceof DoubleEncodedValue) {
                edgeState.set((DoubleEncodedValue) encodedValue, ((Number) value).doubleValue());
            } else if (encodedValue instanceof IntEncodedValue) {
                edgeState.set((IntEncodedValue) encodedValue, ((Number) value).intValue());
            } else if (encodedValue instanceof BitEncodedValue) {
                edgeState.set((BitEncodedValue) encodedValue, (Boolean) value);
            } else {
                throw new IllegalArgumentException("encodedValue " + encodedValue.getClass() + " not supported: " + encodedValue);
            }
        }
    }
}
