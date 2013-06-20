// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.pbf;

import java.util.List;

import com.graphhopper.reader.OSMElement;

/**
 * Stores the results for a decoded Blob.
 * <p/>
 * @author Brett Henderson
 */
public class PbfBlobResult
{
    private List<OSMElement> entities;
    private boolean complete;
    private boolean success;

    /**
     * Creates a new instance.
     */
    public PbfBlobResult()
    {
        complete = false;
        success = false;
    }

    /**
     * Stores the results of a successful blob decoding operation.
     * <p/>
     * @param decodedEntities The entities from the blob.
     */
    public void storeSuccessResult( List<OSMElement> decodedEntities )
    {
        entities = decodedEntities;
        complete = true;
        success = true;
    }

    /**
     * Stores a failure result for a blob decoding operation.
     */
    public void storeFailureResult()
    {
        complete = true;
        success = false;
    }

    /**
     * Gets the complete flag.
     * <p/>
     * @return True if complete.
     */
    public boolean isComplete()
    {
        return complete;
    }

    /**
     * Gets the success flag. This is only valid after complete becomes true.
     * <p/>
     * @return True if successful.
     */
    public boolean isSuccess()
    {
        return success;
    }

    /**
     * Gets the entities decoded from the blob. This is only valid after complete becomes true, and
     * if success is true.
     * <p/>
     * @return The list of decoded entities.
     */
    public List<OSMElement> getEntities()
    {
        return entities;
    }
}
