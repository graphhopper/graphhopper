package com.graphhopper.routing.weighting;

import com.graphhopper.util.flex.FlexModel;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.RoadClass;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.OSMRoadClassParser;
import com.graphhopper.routing.util.parsers.OSMRoadEnvironmentParser;
import com.graphhopper.routing.util.parsers.OSMTollParser;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.MiniPerfTest;

import java.util.concurrent.atomic.AtomicLong;

import static com.graphhopper.routing.profiles.RoadClass.*;

public class FlexWeightingTest {
    public static void main(String[] args) {
        new FlexWeightingTest().measure();
    }

    private void measure() {
        EncodingManager encodingManager = new EncodingManager.Builder(4).
                add(new OSMRoadClassParser()).add(new OSMRoadEnvironmentParser()).add(new OSMTollParser()).
                add(new CarFlagEncoder()).build();
        final GraphHopperStorage graphHopperStorage = new GraphHopperStorage(new RAMDirectory(), encodingManager, false, new GraphExtension.NoOpExtension()).create(100);
        EnumEncodedValue<RoadClass> roadClassEnc = encodingManager.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        DecimalEncodedValue avSpeedEnc = encodingManager.getDecimalEncodedValue(EncodingManager.getKey("car", "average_speed"));
        BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(EncodingManager.getKey("car", "access"));
        graphHopperStorage.edge(0, 1).setDistance(10).set(roadClassEnc, PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(1, 2).setDistance(10).set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(2, 3).setDistance(10).set(roadClassEnc, SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(3, 4).setDistance(10).set(roadClassEnc, MOTORWAY).set(avSpeedEnc, 100).set(accessEnc, true).setReverse(accessEnc, true);


        FlexModel vehicleModel = new FlexModel();
        vehicleModel.setMaxSpeed(120);
        vehicleModel.setBase("car");
        vehicleModel.getFactor().getRoadClass().put(PRIMARY.toString(), 75.0);
//        final FlexWeighting weighting = new FlexWeighting(vehicleModel).init(encodingManager);
        final ScriptWeighting weighting = new ScriptWeighting("car", 120, new ScriptInterface() {
            @Override
            public HelperVariables getHelperVariables() {
                return new HelperVariables();
            }

            @Override
            public double getMillisFactor(EdgeIteratorState edge, boolean reverse) {
                return edge.getDistance();
            }
        }).init(encodingManager);

        System.out.println(weighting.calcWeight(graphHopperStorage.getEdgeIteratorState(0, 1), false, -1));

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

        System.out.println("mean:" + perf.getMean() / 8 + "ms, max:" + perf.getMax() / 8 + "ms, min:" + perf.getMin() / 8 +
                "ms, took without warming:" + perf.getSum() + "ms");
    }

}