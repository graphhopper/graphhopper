package com.graphhopper.routing.ev;

import java.util.Arrays;
import java.util.List;

import com.graphhopper.storage.IntsRef;

/**
 * This class holds a string array and stores <i>index+1</i> to indicate a string is set or <i>0</i>
 * if no value is assigned
 * 
 * @author Peter Karich
 * @author Thomas Butz
 */
public final class StringEncodedValue extends UnsignedIntEncodedValue {
    private final String[] arr;
    
    public StringEncodedValue(String name, List<String> values) {
        this(name, values, false);
    }

    public StringEncodedValue(String name, List<String> values, boolean storeTwoDirections) {
        super(name, 32 - Integer.numberOfLeadingZeros(values.size()), storeTwoDirections);
        
        arr = values.toArray(new String[]{});
        Arrays.sort(arr);
    }

    public final void setString(boolean reverse, IntsRef ref, String value) {
        int intValue = getIntValue(value);
        super.setInt(reverse, ref, intValue);
    }

    public final String getString(boolean reverse, IntsRef ref) {
        int value = super.getInt(reverse, ref);
        if (value == 0) {
            return null;
        }
        return arr[value-1];
    }

    public String[] getValues() {
        return arr;
    }

    private final int getIntValue(String value) {
        if (value == null)
            return 0;
        int res = Arrays.binarySearch(arr, value);
        if (res < 0)
            return 0;
        return res+1;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StringEncodedValue)) {
            return false;
        }
        StringEncodedValue other = (StringEncodedValue) obj;
        return Arrays.equals(arr, other.arr);
    }

    @Override
    public int getVersion() {
        return 31 * super.getVersion() + staticHashCode(arr);
    }
}
