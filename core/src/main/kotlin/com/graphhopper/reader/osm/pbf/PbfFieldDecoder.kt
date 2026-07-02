// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf

import crosby.binary.Osmformat
import java.util.Date

/**
 * Manages decoding of the lower level PBF data structures.
 *
 * @author Brett Henderson
 */
class PbfFieldDecoder(primitiveBlock: Osmformat.PrimitiveBlock) {
    private val strings: Array<String>
    private val coordGranularity: Int = primitiveBlock.granularity
    private val coordLatitudeOffset: Long = primitiveBlock.latOffset
    private val coordLongitudeOffset: Long = primitiveBlock.lonOffset
    private val dateGranularity: Int = primitiveBlock.dateGranularity

    init {
        val stringTable = primitiveBlock.stringtable
        strings = Array(stringTable.sCount) { i -> stringTable.getS(i).toStringUtf8() }
    }

    /**
     * Decodes a raw latitude value into degrees.
     *
     * @param rawLatitude The PBF encoded value.
     * @return The latitude in degrees.
     */
    fun decodeLatitude(rawLatitude: Long): Double =
        COORDINATE_SCALING_FACTOR * (coordLatitudeOffset + (coordGranularity * rawLatitude))

    /**
     * Decodes a raw longitude value into degrees.
     *
     * @param rawLongitude The PBF encoded value.
     * @return The longitude in degrees.
     */
    fun decodeLongitude(rawLongitude: Long): Double =
        COORDINATE_SCALING_FACTOR * (coordLongitudeOffset + (coordGranularity * rawLongitude))

    /**
     * Decodes a raw timestamp value into a Date.
     *
     * @param rawTimestamp The PBF encoded timestamp.
     * @return The timestamp as a Date.
     */
    fun decodeTimestamp(rawTimestamp: Long): Date = Date(dateGranularity * rawTimestamp)

    /**
     * Decodes a raw string into a String.
     *
     * @param rawString The PBF encoding string.
     * @return The string as a String.
     */
    fun decodeString(rawString: Int): String = strings[rawString]

    companion object {
        private const val COORDINATE_SCALING_FACTOR = 0.000000001
    }
}
