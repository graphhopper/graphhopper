package com.graphhopper.routing.util;

/**
 * Encapsulates a bit-encoded value.
 * @author Nop
 */
public class EncodedValue
{
    private String name;
    private int shift;
    private int mask;
    private int factor;
    private int maxValue;

    private int defaultValue;
    private int defaultMax;

    /**
     * Define a bit-encoded value
     * @param name Description for debugging
     * @param shift bit index of this value
     * @param bits number of bits reserved
     * @param factor scaling factor for stored values
     * @param defaultValue default value
     * @param defaultMax default maximum value
     */
    public EncodedValue( String name, int shift, int bits, int factor, int defaultValue, int defaultMax ) {
        this.name = name;
        this.shift = shift;
        this.factor = factor;
        this.defaultValue = defaultValue;
        this.defaultMax = defaultMax;

        mask = (1 << (bits)) -1;
        maxValue = mask * factor;

        mask <<= shift;

        // test the default max value just for paranoia
        setValue( 0, defaultMax );
    }

    public int setValue( int flags, int value )
    {
        if( value > maxValue )
            throw new IllegalArgumentException( name + " value too large for encoding: " + value );

        // scale down value
        value /= factor;
        value <<= shift;

        // clear value bits
        flags &= ~mask;

        // set value
        return flags | value;
    }

    public int getValue( int flags )
    {
        // find value
        flags &= mask;

        flags >>= shift;
        return flags * factor;
    }

    public int setDefaultValue( int flags )
    {
        return setValue( flags, defaultValue );
    }

    public int maxValue() {
        return defaultMax;
    }
}
