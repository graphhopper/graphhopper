package com.graphhopper.routing.ev;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DefaultEncodedValueFactoryTest {

    private final DefaultEncodedValueFactory factory = new DefaultEncodedValueFactory();

    @Test
    public void loadRoadClass() {
        EncodedValue rcEnc = new EnumEncodedValue<>(RoadClass.KEY, RoadClass.class);
        EncodedValue loadedRCEnc = factory.create(rcEnc.toString());
        assertEquals(loadedRCEnc, rcEnc);
    }

    @Test
    public void loadCarMaxSpeed() {
        EncodedValue enc = MaxSpeed.create();
        DecimalEncodedValueImpl loadedEnc = (DecimalEncodedValueImpl) factory.create(enc.toString());
        assertEquals(loadedEnc, enc);
    }

    @Test
    public void loadBoolean() {
        EncodedValue enc = Roundabout.create();
        BooleanEncodedValue loadedEnc = (BooleanEncodedValue) factory.create(enc.toString());
        assertEquals(loadedEnc, enc);
    }
}