package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.*;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UrbanDensityCalculatorTest {

    @Test
    public void testUrbanDensity() {
        EnumEncodedValue<UrbanDensity> ud = UrbanDensity.create();
        EnumEncodedValue<RoadClass> roadClassEnc = RoadClass.create();
        BooleanEncodedValue roadClassLinkEnc = RoadClassLink.create();
        EncodingManager em = new EncodingManager.Builder().add(ud).add(roadClassEnc).add(roadClassLinkEnc).build();
        BaseGraph baseGraph = new BaseGraph.Builder(em).build();

        EdgeIteratorState e01 = baseGraph.edge(0, 1).set(roadClassEnc, RoadClass.RESIDENTIAL);
        EdgeIteratorState e12 = baseGraph.edge(1, 2).set(roadClassEnc, RoadClass.RESIDENTIAL).
                setKeyValues(Arrays.asList(new KVStorage.KeyValue("tiger:reviewed", "no")));

        UrbanDensityCalculator.calcUrbanDensity(baseGraph, ud, roadClassEnc, roadClassLinkEnc, 300, 60, 0, 0, 1);

        assertEquals(UrbanDensity.RESIDENTIAL, e01.get(ud));
        assertEquals(UrbanDensity.RURAL, e12.get(ud));
    }

}
