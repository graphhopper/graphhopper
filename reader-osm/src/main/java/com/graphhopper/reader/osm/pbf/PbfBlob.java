// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import org.openstreetmap.osmosis.osmbinary.Fileformat.Blob;

/**
 * Represents a single piece of blob data extracted from the PBF stream.
 * <p>
 *
 * @author Brett Henderson
 */
public class PbfBlob {
    private String type;
    private Blob blob;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param type The type of data represented by this blob. This corresponds to the type field in
     *             the blob header.
     * @param blob The {@link Blob}
     */
    public PbfBlob(String type, Blob blob) {
        this.type = type;
        this.blob = blob;
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
     * Gets the {@link Blob}.
     * <p>
     *
     * @return The blob data.
     */
    public Blob getBlob() {
        return blob;
    }
}
