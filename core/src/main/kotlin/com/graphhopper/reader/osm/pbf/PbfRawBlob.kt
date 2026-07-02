// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf

/**
 * Represents a single piece of raw blob data extracted from the PBF stream. It has not yet been
 * decoded into a PBF blob object.
 *
 * @param type The type of data represented by this blob. This corresponds to the type field in
 *             the blob header.
 * @param data The raw contents of the blob in binary undecoded form.
 * @author Brett Henderson
 */
class PbfRawBlob(
    /** The type of data represented by this blob. This corresponds to the type field in the blob header. */
    val type: String,
    /** The raw contents of the blob in binary undecoded form. */
    val data: ByteArray
)
