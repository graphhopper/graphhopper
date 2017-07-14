package com.graphhopper.routing.profiles;

/**
 * This class defines where to store an integer. It is important to note that 1. the range of the integer is
 * highly limited (unlike the Java 32bit integer values) so that the storeable part of it fits into the
 * specified number of bits (using the internal shift value) and 2. the default value is always 0.
 * <p>
 * To illustrate why the default is always 0 and how you can still use other defaults imagine the storage engine
 * creates a new entry. Either the engine knows the higher level logic or we assume the default value is 0 and
 * map this value to the real value on every retrieval request.
 * <p>
 * How could you then implement e.g. a 'priority' value going from [-3, 3] that maps to [0,7] but should
 * have a default value of 3 instead of 0? Either you waste space and map this to [1,7], which means that 0 and 3 both
 * refer to the same 0 value (currently the preferred method due to its simplicity) or you could create a
 * MappedIntEncodedValue class that holds an array or a Map with the raw integers similarly to what StringEncodedValue does:
 * {0: 0, 1: -3, 2: -2, 3: -1, 4: 1, 5: 2, 6: 3}
 */
public class IntEncodedValue implements EncodedValue {

    private final String name;
    // we do not have just one or two int values like with flags, we can have much more:
    private int dataIndex;

    final int bits;
    int maxValue;
    int shift;
    int mask;
    int defaultValue;

    public IntEncodedValue(String name, int bits) {
        this(name, bits, 0);
    }

    /**
     * @param defaultValue this parameter defines which value to return if the 'raw' integer value is 0.
     */
    public IntEncodedValue(String name, int bits, int defaultValue) {
        this.name = name;
        if (!name.toLowerCase().equals(name))
            throw new IllegalArgumentException("EncodedValue name must be lower case but was " + name);

        this.bits = bits;
        if (bits <= 0)
            throw new IllegalArgumentException("bits cannot be 0 or negative");
        this.defaultValue = defaultValue;
    }

    @Override
    public final void init(EncodedValue.InitializerConfig init) {
        this.shift = init.shift;
        this.dataIndex = init.dataIndex;
        this.mask = init.next(bits);
        this.maxValue = (1 << bits) - 1;
    }

    private void checkValue(int value) {
        if (mask == 0)
            throw new IllegalStateException("EncodedValue " + getName() + " not initialized");
        if (value > maxValue)
            throw new IllegalArgumentException(name + " value too large for encoding: " + value + ", maxValue:" + maxValue);
        if (value < 0)
            throw new IllegalArgumentException("negative value for " + name + " not allowed! " + value);
    }

    /**
     * This method 'merges' the specified integer value with the specified 'flags' to return a value that can
     * be stored.
     *
     * @return the storable format that can be read via fromStorageFormatToInt
     */
    public final int toStorageFormat(int flags, int value) {
        checkValue(value);
        return uncheckToStorageFormat(flags, value);
    }

    int uncheckToStorageFormat(int flags, int value) {
        value <<= shift;

        // clear value bits
        flags &= ~mask;

        // set value
        return flags | value;
    }

    /**
     * This method restores the integer value from the specified 'flags' taken from the storage.
     */
    public final int fromStorageFormatToInt(int flags) {
        return uncheckFromStorageFormatToInt(flags);
    }

    int uncheckFromStorageFormatToInt(int flags) {
        flags &= mask;
        flags >>>= shift;
        // return the integer value
        if (flags == 0)
            return defaultValue;
        return flags;
    }

    /**
     * There are multiple int values possible per edge. Here we specify the index into this integer array.
     */
    public final int getOffset() {
        assert mask != 0;
        return dataIndex;
    }

    @Override
    public final int hashCode() {
        return mask ^ dataIndex;
    }


    @Override
    public final boolean equals(Object obj) {
        IntEncodedValue other = (IntEncodedValue) obj;
        return other.mask == mask && other.bits == bits && other.dataIndex == dataIndex && other.name.equals(name);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String toString() {
        return getName();
    }
}
