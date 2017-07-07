package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.EdgeIteratorState;

import java.util.Collection;

public class PropertyParserOSM implements PropertyParser {

    @Override
    public void parse(ReaderWay way, EdgeIteratorState edge, Collection<Property> properties) {
        // TODO Should we better decouple OSM from Property via a separate class like HighwayProperty that uses a StringProperty?
        // especially ugly is that the order is important as e.g. DoubleProperty extends IntProperty
        // TODO how can we avoid parsing for all properties under certain circumstances like highway=rail -> build a pipe or a filtering system somehow?
        for (Property property : properties) {
            // TODO how can we avoid the if-instanceof stuff?
            Object value = property.parse(way);
            if (value == null)
                continue;

            if (property instanceof StringProperty) {
                edge.set((StringProperty) property, (String) value);
            } else if (property instanceof DoubleProperty) {
                edge.set((DoubleProperty) property, ((Number) value).doubleValue());
            } else if (property instanceof IntProperty) {
                edge.set((IntProperty) property, ((Number) value).intValue());
            } else {
                throw new IllegalArgumentException("property " + property.getClass() + " not supported: " + property);
            }
        }
    }
}
