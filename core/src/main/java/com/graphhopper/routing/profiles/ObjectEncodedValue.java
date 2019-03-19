package com.graphhopper.routing.profiles;

import com.graphhopper.storage.IntsRef;

/**
 * This interface provides access to an EncodedValue of an index based object
 *
 * @see IndexBased
 */
public interface ObjectEncodedValue extends EncodedValue {

    void setObject(boolean reverse, IntsRef ref, IndexBased value);

    IndexBased getObject(boolean reverse, IntsRef ref);
}
