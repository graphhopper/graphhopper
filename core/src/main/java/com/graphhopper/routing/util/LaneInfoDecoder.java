package com.graphhopper.routing.util;

import com.graphhopper.util.Lane;

import java.util.List;

/**
 * Encodes and decodes a turn lane information with an integer flag.
 * This encoder can be enabled setting the flag encoder property <b>lane_info</b> to <code>true</code> in the property
 * <b>graph.flag_encoders</b>.
 * The current implementation requires the property <b>graph.bytes_for_flags</b> to be set to 8.
 */
public interface LaneInfoDecoder {

    /**
     * Transforms the encoded lane information value to the original string from the <b>turn:lanes</b> tag.
     * <p/>
     * Note: the original tag and the decoded value may vary because the encoded tags were simplified.
     * (e.g. through;slight_right => through;right) the directions are respected.
     *
     * @param flags the encoded lane information
     * @return the tag representation of the lane information
     */
    String decodeTurnLanes(long flags);

    /**
     * Transforms the encoded lane information value to the original string from the <b>turn:lanes</b> tag with codes.
     * <p/>
     * Note: the original tag and the decoded value may vary because the encoded tags were simplified.
     * (e.g. through;slight_right => through;right) the directions are respected.
     *
     * @param flags the encoded lane information
     * @return a list of Lanes that contains the string representation of the tags as well as the codes.
     * @see Lane
     */
    List<Lane> decodeTurnLanesToList(long flags);
}