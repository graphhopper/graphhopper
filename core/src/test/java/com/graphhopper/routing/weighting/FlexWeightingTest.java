package com.graphhopper.routing.weighting;

import com.graphhopper.routing.flex.FlexModel;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.MiniPerfTest;

import java.util.concurrent.atomic.AtomicLong;

public class FlexWeightingTest {
    public static void main(String[] args) {
        new FlexWeightingTest().measure();
    }

    private void measure() {
        EncodingManager encodingManager = EncodingManager.start(4).
                addRoadClass().addRoadEnvironment().addToll().
                add(new CarFlagEncoder()).build();
        final GraphHopperStorage graphHopperStorage = new GraphHopperStorage(new RAMDirectory(), encodingManager, false, new GraphExtension.NoOpExtension()).create(100);
        EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEncodedValue(EncodingManager.ROAD_CLASS, EnumEncodedValue.class);
        DecimalEncodedValue avSpeedEnc = encodingManager.getEncodedValue("car.average_speed", DecimalEncodedValue.class);
        BooleanEncodedValue accessEnc = encodingManager.getEncodedValue("car.access", BooleanEncodedValue.class);
        graphHopperStorage.edge(0, 1).set(roadClassEnc, RoadClass.PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(1, 2).set(roadClassEnc, RoadClass.SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(2, 3).set(roadClassEnc, RoadClass.SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(3, 4).set(roadClassEnc, RoadClass.MOTORWAY).set(avSpeedEnc, 100).set(accessEnc, true).setReverse(accessEnc, true);


        FlexModel vehicleModel = new FlexModel();
        vehicleModel.setMaxSpeed(120);
        vehicleModel.setBase("car");
        vehicleModel.getFactor().getRoadClass().put(RoadClass.PRIMARY.toString(), 75.0);
        final FlexWeighting weighting = new FlexWeighting(encodingManager, vehicleModel);

        final AtomicLong counter = new AtomicLong(0);
        MiniPerfTest perf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                AllEdgesIterator iter = graphHopperStorage.getAllEdges();
                int sum = 0;
                while (iter.next()) {
                    if (!warmup)
                        counter.addAndGet(2);
                    sum += (int) weighting.calcWeight(iter, false, EdgeIterator.NO_EDGE);
                    sum += (int) weighting.calcWeight(iter, true, EdgeIterator.NO_EDGE);
                }
                return sum;
            }
        }.setIterations(10_000_000).start();

        // +4% observed, so pick best of 3 and 5mio is too few => too big fluctuations +-5% instead of +2%
        // 10 mio mean:1.014564792E-4ms => use 1.05+-0.03

        // remove methods except roadclass: 0.66E-4ms
        // put if in front of all calls   : 0.60E-4ms
        // loop with conditional add      : 0.55E-4ms
        System.out.println("mean:" + perf.getMean() / 8 + "ms, max:" + perf.getMax() / 8 + "ms, min:" + perf.getMin() / 8 +
                "ms, took without warming:" + perf.getSum() + "ms");
    }

}