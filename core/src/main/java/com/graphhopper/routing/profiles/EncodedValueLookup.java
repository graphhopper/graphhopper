package com.graphhopper.routing.profiles;

public interface EncodedValueLookup {

    <T extends EncodedValue> T getEncodedValue(String key, Class<T> encodedValueType);

    BooleanEncodedValue getBooleanEncodedValue(String key);

    IntEncodedValue getIntEncodedValue(String key);

    DecimalEncodedValue getDecimalEncodedValue(String key);

    StringEncodedValue getStringEncodedValue(String key);
}
