package com.graphhopper.routing.ev;

public interface EdgeBytesAccess {
    void setBytes(int edgeId, int edgeRowBytesOffset, byte[] bytes, int bytesOffset, int len);

    void getBytes(int edgeId, int edgeRowBytesOffset, byte[] bytes, int bytesOffset, int len);

    /**
     * Gets the int value at the given index for the given edgeId
     */
    int getInt(int edgeId, int byteIndex);

    /**
     * Sets the int value at the given index for the given edgeId
     */
    void setInt(int edgeId, int byteIndex, int value);
}
