// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import com.graphhopper.reader.ReaderElement;

import java.util.List;

/**
 * Instances of this interface are used to receive results from PBFBlobDecoder.
 * <p>
 *
 * @author Brett Henderson
 */
public interface PbfBlobDecoderListener {
    /**
     * Provides the listener with the list of decoded entities.
     * <p>
     *
     * @param decodedEntities The decoded entities.
     */
    void complete(List<ReaderElement> decodedEntities);

    /**
     * Notifies the listener that an error occurred during processing.
     */
    void error(Exception ex);
}
