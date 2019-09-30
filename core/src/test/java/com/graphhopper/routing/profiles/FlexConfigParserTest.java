package com.graphhopper.routing.profiles;

import com.graphhopper.routing.ev.DefaultEncodedValueFactory;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.ev.RoadEnvironment;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.parsers.OSMMaxLengthParser;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.GHUtility;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class FlexConfigParserTest {

    private FlexYConfigParser parser = new FlexYConfigParser();
    private CarFlagEncoder encoder = new CarFlagEncoder();
    private EncodingManager em = GHUtility.addDefaultEncodedValues(new EncodingManager.Builder(8)).add(encoder).build();
    private EnumEncodedValue<RoadClass> rcEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
    private IntsRef intsRef;

    @Before
    public void setUp() {
        intsRef = new IntsRef(2);
    }

    @Test
    public void parse() {
        String yaml = "base: car\n"
                + "max_speed: 110\n"
                + "speed_factor: { motorway: 1.1}";
        FlexConfig config = parser.parse(yaml);
        assertEquals("car", config.getBase());
        assertEquals(110, config.getMaxSpeed(), .1);
        assertEquals(1.1, (Double) config.getSpeedFactor().get("motorway"), 0.01);
    }

    @Test
    public void eval() {
        String yaml = "base: car\n"
                + "max_speed: 110\n"
                + "average_speed:\n"
                + "  road_class: { motorway: 80}";
        FlexConfig config = parser.parse(yaml);

        FlexConfig.AverageSpeedConfig speedConfig = config.createAverageSpeedConfig(em, new DefaultEncodedValueFactory());
        EnumEncodedValue<RoadEnvironment> reEnc = em.getEnumEncodedValue(RoadEnvironment.KEY, RoadEnvironment.class);
        rcEnc.setEnum(false, intsRef, RoadClass.PRIMARY);
        encoder.getAverageSpeedEnc().setDecimal(false, intsRef, 40);
        assertEquals(40.0, speedConfig.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.1);

        rcEnc.setEnum(false, intsRef, RoadClass.MOTORWAY);
        assertEquals(80.0, speedConfig.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.01);

        yaml = "base: car\n"
                + "max_speed: 110\n"
                + "average_speed:\n"
                + "  road_class: { motorway: 80}\n"
                + "speed_factor:\n"
                + "  road_environment: { bridge: 0.9 }";
        config = parser.parse(yaml);
        reEnc.setEnum(false, intsRef, RoadEnvironment.BRIDGE);
        speedConfig = config.createAverageSpeedConfig(em, new DefaultEncodedValueFactory());
        assertEquals(80 * 0.9, speedConfig.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.01);

        reEnc.setEnum(false, intsRef, RoadEnvironment.ROAD);
        assertEquals(80.0, speedConfig.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.01);
    }

    @Test
    public void accessFromSpeed() {
        String yaml = "base: car\n"
                + "max_speed: 110\n"
                + "average_speed:\n"
                + "  road_class: { motorway: 0}";
        FlexConfig config = parser.parse(yaml);
        FlexConfig.AverageSpeedConfig speedConfig = config.createAverageSpeedConfig(em, new DefaultEncodedValueFactory());
        encoder.getAverageSpeedEnc().setDecimal(false, intsRef, 20);
        rcEnc.setEnum(false, intsRef, RoadClass.PRIMARY);
        assertEquals(20, speedConfig.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.01);

        // block via config (average_speed: 0)
        rcEnc.setEnum(false, intsRef, RoadClass.MOTORWAY);
        assertEquals(0, speedConfig.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.01);
    }

    @Test
    public void widthPriority() {
        CarFlagEncoder tmpCarEncoder = new CarFlagEncoder();
        EncodingManager tmpEM = new EncodingManager.Builder(8).add(new OSMMaxLengthParser()).add(tmpCarEncoder).build();

        String yaml = "base: car\n"
                + "max_speed: 110\n"
                + "length: 10";
        FlexConfig config = parser.parse(yaml);
        FlexConfig.PriorityFlexConfig prio = config.createPriorityConfig(tmpEM, new DefaultEncodedValueFactory());
        assertEquals(1, prio.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.01);

        tmpCarEncoder.getDecimalEncodedValue("max_length").setDecimal(false, intsRef, 9.5);
        assertEquals(0, prio.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.01);
    }

    @Ignore
    @Test
    public void testUnblockWithConfig() {
        // allow a previously not allowed road via config (reverse_one_way: 0.3)
        // TODO this won't work even if implemented here, because the access of an edge does not depend on the Weighting yet, related: #729
        // so we cannot revert a previously blocked edge
        String yaml = "base: car\n"
                + "max_speed: 110\n"
                + "average_speed:\n"
                + "  road_class: { motorway: 80}\n"
                + "  reverse_one_way: { tertiary: 0.3 }";
        FlexConfig config = parser.parse(yaml);
        FlexConfig.AverageSpeedConfig speedConfig = config.createAverageSpeedConfig(em, new DefaultEncodedValueFactory());
        assertEquals(3600 / 40.0, speedConfig.eval(GHUtility.createMockedEdgeIteratorState(1000, intsRef), false, -1), 0.1);
    }
}