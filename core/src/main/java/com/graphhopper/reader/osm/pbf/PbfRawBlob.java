// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

/**
 * Represents a single piece of raw blob data extracted from the PBF stream. It has not yet been
 * decoded into a PBF blob object.
 * <p>
 *
 * @author Brett Henderson
 */
public class PbfRawBlob {
    private String type;
    private byte[] data;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param type The type of data represented by this blob. This corresponds to the type field in
     *             the blob header.
     * @param data The raw contents of the blob in binary undecoded form.
     */
    public PbfRawBlob(String type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    /**
     * Gets the type of data represented by this blob. This corresponds to the type field in the
     * blob header.
     * <p>
     *
     * @return The blob type.
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the raw contents of the blob in binary undecoded form.
     * <p>
     *
     * @return The raw blob data.
     */
    public byte[] getData() {
        return data;
    }
}
