// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import com.graphhopper.reader.ReaderElement;

import java.util.List;

/**
 * Stores the results for a decoded Blob.
 * <p>
 *
 * @author Brett Henderson
 */
public class PbfBlobResult {
    private List<ReaderElement> entities;
    private boolean complete;
    private boolean success;
    private Exception ex;

    /**
     * Creates a new instance.
     */
    public PbfBlobResult() {
        complete = false;
        success = false;
        ex = new RuntimeException("no success result stored");
    }

    /**
     * Stores the results of a successful blob decoding operation.
     * <p>
     *
     * @param decodedEntities The entities from the blob.
     */
    public void storeSuccessResult(List<ReaderElement> decodedEntities) {
        entities = decodedEntities;
        complete = true;
        success = true;
    }

    /**
     * Stores a failure result for a blob decoding operation.
     */
    public void storeFailureResult(Exception ex) {
        complete = true;
        success = false;
        this.ex = ex;
    }

    /**
     * Gets the complete flag.
     * <p>
     *
     * @return True if complete.
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Gets the success flag. This is only valid after complete becomes true.
     * <p>
     *
     * @return True if successful.
     */
    public boolean isSuccess() {
        return success;
    }

    public Exception getException() {
        return ex;
    }

    /**
     * Gets the entities decoded from the blob. This is only valid after complete becomes true, and
     * if success is true.
     * <p>
     *
     * @return The list of decoded entities.
     */
    public List<ReaderElement> getEntities() {
        return entities;
    }
}
