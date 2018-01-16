package com.graphhopper.routing.profiles;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test the internal functionality of the EncodedValue
 * <p></p>
 * Currently we have two 32 bit position [0, 0]
 * Every init call will try to fit the bits in the current position,
 * if it does not fit it will try the next position.
 * <pre>
 * shift = current bit position within one 32 number
 * nextShift = the next position within one 32 number
 * dataIndex = the index of the 32 numbers in the array
 * </pre>
 */
public class EncodedValueTest {

    public int bits;

    private EncodedValue encodedValue = new EncodedValue() {

        @Override
        public int init(EncodedValue.InitializerConfig init) {
            init.next(bits);
            return bits;
        }

        @Override
        public String getName() {
            return null;
        }
    };

    /**
     * This test will result in [10 + 2, 20]
     * Total bits used 33
     * Max number of bits 64
     */
    @Test
    public void testInitializerConfigDataIndex() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        bits = 10;
        encodedValue.init(config);
        assertEquals(0, config.shift);
        assertEquals(10, config.nextShift);
        assertEquals(0, config.dataIndex);

        bits = 3;
        encodedValue.init(config);
        assertEquals(10, config.shift);
        assertEquals(13, config.nextShift);
        assertEquals(0, config.dataIndex);

        bits = 20;
        encodedValue.init(config);
        assertEquals(0, config.shift);
        assertEquals(20, config.nextShift);
        assertEquals(1, config.dataIndex);
    }

    /**
     * This test will result in [10 + 3, 20 + 13]
     * Total bits used 46
     * Max number of bits 64
     * Positions used 3
     */
    @Test
    public void testInitializerConfigDataIndexWithoutOrder() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        bits = 10;
        encodedValue.init(config);
        assertEquals(0, config.shift);
        assertEquals(10, config.nextShift);
        assertEquals(0, config.dataIndex);

        bits = 3;
        encodedValue.init(config);
        assertEquals(10, config.shift);
        assertEquals(13, config.nextShift);
        assertEquals(0, config.dataIndex);

        bits = 20;
        encodedValue.init(config);
        assertEquals(0, config.shift);
        assertEquals(20, config.nextShift);
        assertEquals(1, config.dataIndex);

        bits = 13;
        encodedValue.init(config);
        assertEquals(0, config.shift);
        assertEquals(13, config.nextShift);
        assertEquals(2, config.dataIndex);
    }

    /**
     * This test will result in [3 + 10 + 13, 20]
     * Total bits used 46
     * Max number of bits 64
     * Positions used 2
     */
    @Test
    public void testInitializerConfigDataIndexOrdered() {
        EncodedValue.InitializerConfig config = new EncodedValue.InitializerConfig();
        bits = 3;
        encodedValue.init(config);
        assertEquals(0, config.shift);
        assertEquals(3, config.nextShift);
        assertEquals(0, config.dataIndex);

        bits = 10;
        encodedValue.init(config);
        assertEquals(3, config.shift);
        assertEquals(13, config.nextShift);
        assertEquals(0, config.dataIndex);

        bits = 13;
        encodedValue.init(config);
        assertEquals(13, config.shift);
        assertEquals(26, config.nextShift);
        assertEquals(0, config.dataIndex);

        bits = 20;
        encodedValue.init(config);
        assertEquals(0, config.shift);
        assertEquals(20, config.nextShift);
        assertEquals(1, config.dataIndex);
    }
}
