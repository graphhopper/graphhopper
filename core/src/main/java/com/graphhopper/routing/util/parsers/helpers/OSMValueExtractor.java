package com.graphhopper.routing.util.parsers.helpers;

import static com.graphhopper.util.Helper.isEmpty;
import static com.graphhopper.util.Helper.toLowerCase;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.storage.IntsRef;

public class OSMValueExtractor {
    
    private static final Logger LOG = LoggerFactory.getLogger(OSMValueExtractor.class);

    private OSMValueExtractor() {
        // utility class
    }

    public static void extractTons(IntsRef edgeFlags, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys, boolean enableLog) {
        String value = way.getFirstPriorityTag(keys);
        if (isEmpty(value))
            return;
        try {
            double val = stringToTons(value);
            if (val > valueEncoder.getMaxDecimal())
                val = valueEncoder.getMaxDecimal();
            valueEncoder.setDecimal(false, edgeFlags, val);
        } catch (Exception ex) {
            if (enableLog)
                LOG.warn("Unable to extract tons from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId());
        }
    }

    public static double stringToTons(String value) {
        value = toLowerCase(value).replaceAll(" ", "").replaceAll("(tons|ton)", "t");
        value = value.replace("mgw", "").trim();
        double factor = 1;
        if (value.equals("default") || value.equals("none")) {
            return -1;
        } else if (value.endsWith("t")) {
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("lbs")) {
            value = value.substring(0, value.length() - 3);
            factor = 0.00045359237;
        }
    
        return Double.parseDouble(value) * factor;
    }

    public static void extractMeter(IntsRef edgeFlags, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys, boolean enableLog) {
        String value = way.getFirstPriorityTag(keys);
        if (isEmpty(value))
            return;
        try {
            double val = stringToMeter(value);
            if (val > valueEncoder.getMaxDecimal())
                val = valueEncoder.getMaxDecimal();
            valueEncoder.setDecimal(false, edgeFlags, val);
        } catch (Exception ex) {
            if (enableLog)
                LOG.warn("Unable to extract meter from malformed road attribute '{}' for way (OSM_ID = {}).", value, way.getId());
        }
    }

    public static double stringToMeter(String value) {
        value = toLowerCase(value).replaceAll(" ", "").replaceAll("(meters|meter|mtrs|mtr|mt|m\\.)", "m");
        double factor = 1;
        double offset = 0;
        value = value.replaceAll("(\\\"|\'\')", "in").replaceAll("(\'|feet)", "ft");
        if (value.startsWith("~") || value.contains("approx")) {
            value = value.replaceAll("(\\~|approx)", "").trim();
            factor = 0.8;
        }
    
        if (value.equals("default") || value.equals("none") || value.equals("unsigned"))
            return -1;
    
        if (value.endsWith("in")) {
            int startIndex = value.indexOf("ft");
            String inchValue;
            if (startIndex < 0) {
                startIndex = 0;
            } else {
                startIndex += 2;
            }
    
            inchValue = value.substring(startIndex, value.length() - 2);
            value = value.substring(0, startIndex);
            offset = Double.parseDouble(inchValue) * 0.0254;
        }
    
        if (value.endsWith("ft")) {
            value = value.substring(0, value.length() - 2);
            factor *= 0.3048;
        } else if (value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
        }
    
        if (value.isEmpty()) {
            return offset;
        } else {
            return Double.parseDouble(value) * factor + offset;
        }
    }
}