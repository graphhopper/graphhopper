package com.graphhopper.routing.profiles;

/**
 * This class defines how to store and read values from an edge.
 */
public interface EncodedValue {

    /**
     * This method sets the dataIndex and shift of this EncodedValue object and potentially changes the submitted init
     * object afterwards via calling next
     *
     * @see InitializerConfig#next(int)
     */
    void init(InitializerConfig init);

    String getName();

    class InitializerConfig {
        int dataIndex = 0;
        int shift = 0;
        int propertyIndex = 0;

        /**
         * Returns the necessary bit mask for the current bit range of the specified usedBits
         */
        int next(int usedBits) {
            propertyIndex++;
            int wayBitMask = (1 << usedBits) - 1;
            wayBitMask <<= shift;
            shift += usedBits;
            if (shift > 32) {
                shift = 0;
                dataIndex++;
            }
            return wayBitMask;
        }
    }
}
