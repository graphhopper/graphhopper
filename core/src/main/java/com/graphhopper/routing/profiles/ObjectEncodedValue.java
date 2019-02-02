package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

/**
 * This interface provides access to an EncodedValue of an index based object
 *
 * @see IndexBased
 */
public interface ObjectEncodedValue extends EncodedValue {

    IndexBased[] getObjects();

    /**
     * This method searches for an object with the specified name and returns its ordinal. Please note the special
     * return value if not found to be compatible with the default value of MappedObjectEncodedValue.
     *
     * @return 0 if not found or >0 otherwise
     */
    int indexOf(String name);

    void setObject(boolean reverse, IntsRef ref, IndexBased value);

    IndexBased getObject(boolean reverse, IntsRef ref);
}
