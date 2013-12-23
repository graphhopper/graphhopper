/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license
 *  agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the
 *  License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

/**
 * Encapsulates a bit-encoded value.
 * <p/>
 * @author Nop
 */
public class EncodedValue
{
    private String name;
    private long shift;
    private long mask;
    private long factor;
    private long maxValue;
    private long defaultValue;
    private long defaultMax;

    /**
     * Define a bit-encoded value
     * <p/>
     * @param name Description for debugging
     * @param shift bit index of this value
     * @param bits number of bits reserved
     * @param factor scaling factor for stored values
     * @param defaultValue default value
     * @param defaultMax default maximum value
     */
    public EncodedValue( String name, int shift, int bits, int factor, int defaultValue, int defaultMax )
    {
        this.name = name;
        this.shift = shift;
        this.factor = factor;
        this.defaultValue = defaultValue;
        this.defaultMax = defaultMax;

        mask = (1 << (bits)) - 1;
        maxValue = mask * factor;

        mask <<= shift;

        // test the default max value just for paranoia
        setValue(0, defaultMax);
    }

    public long setValue( long flags, long value )
    {
        if (value > maxValue)
            throw new IllegalArgumentException(name + " value too large for encoding: " + value);        

        // scale down value
        value /= factor;
        value <<= shift;

        // clear value bits
        flags &= ~mask;

        // set value
        return flags | value;
    }

    public long getValue( long flags )
    {
        // find value
        flags &= mask;
        flags >>= shift;
        return flags * factor;
    }

    public long setDefaultValue( long flags )
    {
        return setValue(flags, defaultValue);
    }

    public long getDefaultMaxValue()
    {
        return defaultMax;
    }
}
