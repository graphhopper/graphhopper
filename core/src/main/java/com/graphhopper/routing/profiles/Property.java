package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;

/**
 * This class defines how to store and read values from an edge.
 * <p>
 * TODO better move parsing code fully into PropertyParserOSM?
 * <p>
 * TODO how to handle properties like 'distance' that needs to be splitted for VirtualEdgeIteratorStates in QueryGraph?
 */
public interface Property {

    /**
     * This method sets the dataIndex and shift of this Property object and potentially changes the submitted init
     * object afterwards via calling next
     *
     * @see InitializerConfig#next(int)
     */
    void init(InitializerConfig init);

    String getName();

    /**
     * This method picks and transform its necessary values from specified way to create a result that
     * can be stored in the associated edge.
     */
    Object parse(ReaderWay way);

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
