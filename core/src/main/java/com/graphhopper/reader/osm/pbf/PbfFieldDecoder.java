// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import org.openstreetmap.osmosis.osmbinary.Osmformat;

import java.util.Date;

/**
 * Manages decoding of the lower level PBF data structures.
 * <p>
 *
 * @author Brett Henderson
 *         <p>
 */
public class PbfFieldDecoder {
    private static final double COORDINATE_SCALING_FACTOR = 0.000000001;
    private String[] strings;
    private int coordGranularity;
    private long coordLatitudeOffset;
    private long coordLongitudeOffset;
    private int dateGranularity;

    // ORS-GH MOD START
    // Modification by Maxim Rylov: Added a new class variable.
    private byte[] fieldsToSkip;
    // Modification by Maxim Rylov: Added a new method.
    public boolean skip(int rawString) {
        return fieldsToSkip[rawString] == 1;
    }
    // ORS-GH MOD END

    /**
     * Creates a new instance.
     * <p>
     *
     * @param primitiveBlock The primitive block containing the fields to be decoded.
     */
    public PbfFieldDecoder(Osmformat.PrimitiveBlock primitiveBlock) {
        this.coordGranularity = primitiveBlock.getGranularity();
        this.coordLatitudeOffset = primitiveBlock.getLatOffset();
        this.coordLongitudeOffset = primitiveBlock.getLonOffset();
        this.dateGranularity = primitiveBlock.getDateGranularity();

        Osmformat.StringTable stringTable = primitiveBlock.getStringtable();
        strings = new String[stringTable.getSCount()];
        // ORS-GH MOD START
        fieldsToSkip = new byte[stringTable.getSCount()];
        // ORS-GH MOD END
        for (int i = 0; i < strings.length; i++) {
            strings[i] = stringTable.getS(i).toStringUtf8();
            // ORS-GH MOD START
            if ("".equals(strings[i]) || "created_by".equals(strings[i]) || strings[i].startsWith("TMC") || strings[i].startsWith("addr:")) {
                fieldsToSkip[i] = 1;
            }else {
                fieldsToSkip[i] = 0;
            }
            // ORS-GH MOD END

        }
    }

    /**
     * Decodes a raw latitude value into degrees.
     * <p>
     *
     * @param rawLatitude The PBF encoded value.
     * @return The latitude in degrees.
     */
    public double decodeLatitude(long rawLatitude) {
        return COORDINATE_SCALING_FACTOR * (coordLatitudeOffset + (coordGranularity * rawLatitude));
    }

    /**
     * Decodes a raw longitude value into degrees.
     * <p>
     *
     * @param rawLongitude The PBF encoded value.
     * @return The longitude in degrees.
     */
    public double decodeLongitude(long rawLongitude) {
        return COORDINATE_SCALING_FACTOR * (coordLongitudeOffset + (coordGranularity * rawLongitude));
    }

    /**
     * Decodes a raw timestamp value into a Date.
     * <p>
     *
     * @param rawTimestamp The PBF encoded timestamp.
     * @return The timestamp as a Date.
     */
    public Date decodeTimestamp(long rawTimestamp) {
        return new Date(dateGranularity * rawTimestamp);
    }

    /**
     * Decodes a raw string into a String.
     * <p>
     *
     * @param rawString The PBF encoding string.
     * @return The string as a String.
     */
    public String decodeString(int rawString) {
        return strings[rawString];
    }
}
