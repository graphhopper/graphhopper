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
        graphHopperStorage.edge(0, 1).setDistance(10).set(roadClassEnc, RoadClass.PRIMARY).set(avSpeedEnc, 80).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(1, 2).setDistance(10).set(roadClassEnc, RoadClass.SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(2, 3).setDistance(10).set(roadClassEnc, RoadClass.SECONDARY).set(avSpeedEnc, 70).set(accessEnc, true).setReverse(accessEnc, true);
        graphHopperStorage.edge(3, 4).setDistance(10).set(roadClassEnc, RoadClass.MOTORWAY).set(avSpeedEnc, 100).set(accessEnc, true).setReverse(accessEnc, true);


        FlexModel vehicleModel = new FlexModel();
        vehicleModel.setMaxSpeed(120);
        vehicleModel.setBase("car");
        vehicleModel.getFactor().getRoadClass().put(RoadClass.PRIMARY.toString(), 75.0);
        final FlexWeighting weighting = new FlexWeighting(vehicleModel).init(encodingManager);
//        final ScriptWeighting weighting = new ScriptWeighting(new ScriptInterface() {}).init(encodingManager);

        System.out.println(weighting.calcWeight(graphHopperStorage.getEdgeIteratorState(0, 1), false, -1));
//        final ScriptInterface scriptInterface;
//        try {
//            IClassBodyEvaluator cbe = CompilerFactoryFactory.getDefaultCompilerFactory().newClassBodyEvaluator();
//            cbe.setNoPermissions();
//            cbe.setImplementedInterfaces(new Class[]{ScriptInterface.class});
//            cbe.setDefaultImports(new String[]{"com.graphhopper.util.EdgeIteratorState",
//                    "com.graphhopper.routing.profiles.EnumEncodedValue",
//                    "com.graphhopper.routing.profiles.*"});
//            cbe.setClassName("MyRunner");
//            cbe.cook("public EnumEncodedValue road_class;\n"
//                    + "  public double getMaxSpeed() { return 120.0; }\n"
//                    + "  public String getName() { return \"script\"; }\n"
//                    + "  public double get(EdgeIteratorState edge, boolean reverse) {\n"
//                    + "     return edge.get(road_class) == RoadClass.PRIMARY ? 1 : 10;"
//                    + "  }");
//            Class<?> c = cbe.getClazz();
//            scriptInterface = (ScriptInterface) c.newInstance();
//
////            System.out.println(scriptInterface.getMaxSpeed());
//            Field field = scriptInterface.getClass().getDeclaredField("road_class");
//            field.set(scriptInterface, roadClassEnc);
//        } catch (Exception ex) {
//            throw new IllegalArgumentException(ex);
//        }

//        final ScriptEvaluator script = new ScriptEvaluator();
//        final SimpleScriptWeighting ti;
//        try {
//            script.setNoPermissions();
//            ti = (SimpleScriptWeighting) script.createFastEvaluator("return Math.min(speed, 40);", SimpleScriptWeighting.class, new String[]{"speed", "edge", "road_class",
//                    "s1","s2","s3","s4","s5","s6","s7","s8","s9","s10","s11","s12","s13","s14","s15","s16","s17","s18","s19","s20","s21","s22"});
//            script.setParameters(new String[]{"speed", "road_class", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11", "s12", "s13", "s14", "s15", "s16", "s17", "s18", "s19", "s20", "s21", "s22", "s23"},
//                    new Class[]{double.class, String.class, String.class, String.class, String.class,
//                            String.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class,
//                            String.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class});
//            script.setReturnType(double.class);
//            script.cook("return Math.max(speed, 40) * road_class.length() * s1.length() * s3.length() * s4.length() * s5.length() * s6.length() * s10.length();");
//        } catch (Exception ex) {
//            throw new IllegalArgumentException(ex);
//        }

        final AtomicLong counter = new AtomicLong(0);
        MiniPerfTest perf = new MiniPerfTest() {
            @Override
            public int doCalc(boolean warmup, int run) {
                AllEdgesIterator iter = graphHopperStorage.getAllEdges();
                int sum = 0;
                while (iter.next()) {
                    if (!warmup)
                        counter.addAndGet(2);
//                    sum += (int) scriptInterface.get(iter, false);
//                    sum += (int) scriptInterface.get(iter, true);
                    sum += (int) weighting.calcWeight(iter, false, EdgeIterator.NO_EDGE);
                    sum += (int) weighting.calcWeight(iter, true, EdgeIterator.NO_EDGE);
                }
                return sum;
//                try {
//                    return ((Number) script.evaluate(new Object[]{60.0, "test", "a", "ab", "fx", "sdf", "5", "66", "777", "8888", "9999", "10", "11", "12", "1313", "1414", "15", "16", "1716", "18", "1919", "20000", "21", "22", "23232323"})).intValue();
//                    return (int) ti.get(60, null, null, "a", "ab", "fx", "sdf", "5", "66", "777", "8888", "9999", "10", "11", "12", "1313", "1414", "15", "16", "1716", "18", "1919", "20000", "21", "22");
//                } catch (Exception ex) {
//                    throw new IllegalArgumentException(ex);
//                }
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