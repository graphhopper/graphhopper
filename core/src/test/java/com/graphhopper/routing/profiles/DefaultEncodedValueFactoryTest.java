package com.graphhopper.routing.profiles;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultEncodedValueFactoryTest {

    private final DefaultEncodedValueFactory factory = new DefaultEncodedValueFactory();

    @Test
    public void loadRoadClass() {
        EncodedValue rcEnc = RoadClass.create();
        ObjectEncodedValue loadedRCEnc = (ObjectEncodedValue) factory.create(rcEnc.toString());
        assertEquals(loadedRCEnc, rcEnc);
    }

    @Test
    public void loadCarMaxSpeed() {
        EncodedValue enc = MaxSpeed.create();
        FactorizedDecimalEncodedValue loadedEnc = (FactorizedDecimalEncodedValue) factory.create(enc.toString());
        assertEquals(loadedEnc, enc);
    }

    @Test
    public void loadBoolean() {
        EncodedValue enc = Roundabout.create();
        BooleanEncodedValue loadedEnc = (BooleanEncodedValue) factory.create(enc.toString());
        assertEquals(loadedEnc, enc);
    }
}