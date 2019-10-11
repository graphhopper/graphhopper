package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EncodedValueLookup;
import com.graphhopper.storage.IntsRef;

import java.util.List;

/**
 * This interface makes it possible to read relation tags to create relation flags and convert them in the second read
 * into edge flags.
 */
public interface RelationTagParser extends TagParser {

    void createRelationEncodedValues(EncodedValueLookup lookup, List<EncodedValue> registerNewEncodedValue);

    /**
     * Analyze the tags of a relation and create the routing flags for the second read step.
     * In the pre-parsing step this method will be called to determine the useful relation tags.
     */
    IntsRef handleRelationTags(IntsRef relFlags, ReaderRelation relation);
}
