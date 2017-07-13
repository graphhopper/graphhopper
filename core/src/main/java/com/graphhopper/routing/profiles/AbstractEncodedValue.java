package com.graphhopper.routing.profiles;

/**
 * Base class for a property with name and init method
 */
public abstract class AbstractEncodedValue implements EncodedValue {
    protected final String name;
    protected final int bits;
    protected int shift;
    // we do not have just one or two int values like with flags, we can have much more:
    private int dataIndex;
    protected int mask;
    private int maxValue;

    protected AbstractEncodedValue(String name, int bits) {
        this.name = name;
        if (!name.toLowerCase().equals(name))
            throw new IllegalArgumentException("EncodedValue name must be lower case but was " + name);

        this.bits = bits;
        if (bits <= 0)
            throw new IllegalArgumentException("bits cannot be 0 or negative");
    }

    @Override
    public void init(EncodedValue.InitializerConfig init) {
        this.shift = init.shift;
        this.dataIndex = init.dataIndex;
        this.mask = init.next(bits);
        this.maxValue = (1 << bits) - 1;
    }

    protected final void checkValue(int value) {
        if (mask == 0)
            throw new IllegalStateException("EncodedValue " + getName() + " not initialized");
        if (value > maxValue)
            throw new IllegalArgumentException(name + " value too large for encoding: " + value + ", maxValue:" + maxValue);
        if (value < 0)
            throw new IllegalArgumentException("negative " + name + " value not allowed! " + value);
//        if (!allowZero && value == 0)
//            throw new IllegalArgumentException("zero " + name + " value not allowed! " + value);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * There are multiple int values possible per edge. Here we specify the index into this array.
     */
    public int getOffset() {
        assert mask != 0;
        return dataIndex;
    }

    @Override
    public int hashCode() {
        return mask ^ dataIndex;
    }

    @Override
    public boolean equals(Object obj) {
        AbstractEncodedValue other = (AbstractEncodedValue) obj;
        return other.mask == mask && other.bits == bits && other.dataIndex == dataIndex && other.name.equals(name);
    }
}
