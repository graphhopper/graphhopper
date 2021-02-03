package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EncodedValueLookup;
import com.graphhopper.storage.IntsRef;

import java.util.List;

public interface NodeTagParser {

    void createNodeEncodedValues(EncodedValueLookup lookup, List<EncodedValue> nodeEncodedValues, List<EncodedValue> edgeEncodedValues);

    /**
     * Parse tags on nodes. Node tags can add to speed (like traffic_signals) or blocks access (like a barrier). This
     * method is called in the second parsing step.
     */
    IntsRef handleNodeTags(IntsRef nodeFlags, ReaderNode node);

    /**
     * This method copies the information stored in nodeFlags into the specified edgeFlags (different bits) and returns
     * it.
     */
    IntsRef copyNodeToEdge(IntsRef nodeFlags, IntsRef edgeFlags);
}
