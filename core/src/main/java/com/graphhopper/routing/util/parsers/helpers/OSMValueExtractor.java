package com.graphhopper.routing.util.parsers.helpers;

import static com.graphhopper.util.Helper.toLowerCase;

import java.util.List;
import java.util.regex.Pattern;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.storage.IntsRef;

public class OSMValueExtractor {
    
    private static final Pattern TON_PATTERN    = Pattern.compile("tons?");
    private static final Pattern MGW_PATTERN    = Pattern.compile("mgw");
    private static final Pattern WSPACE_PATTERN = Pattern.compile("\\s");
    private static final Pattern METER_PATTERN  = Pattern.compile("meters?|mtrs?|mt|m\\.");
    private static final Pattern INCH_PATTERN   = Pattern.compile("\"|\'\'");
    private static final Pattern FEET_PATTERN   = Pattern.compile("\'|feet");
    private static final Pattern APPROX_PATTERN = Pattern.compile("~|approx");

    private OSMValueExtractor() {
        // utility class
    }

    public static void extractTons(IntsRef edgeFlags, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys) {
        final String rawValue = way.getFirstPriorityTag(keys);
        double value = stringToTons(rawValue);
        
        if (Double.isNaN(value)) {
            return;
        }
        
        if (value > valueEncoder.getMaxDecimal())
            value = valueEncoder.getMaxDecimal();
        valueEncoder.setDecimal(false, edgeFlags, value);
    }

    public static double stringToTons(String value) {
        value = TON_PATTERN.matcher(toLowerCase(value)).replaceAll("t");
        value = MGW_PATTERN.matcher(value).replaceAll("").trim();
        if (isInvalidValue(value))
            return Double.NaN;

        double factor = 1;
        if (value.endsWith("t")) {
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("lbs")) {
            value = value.substring(0, value.length() - 3);
            factor = 0.00045359237;
        } else if (value.endsWith("kg")) {
            value = value.substring(0, value.length() - 2);
            factor = 0.001;
        }

        try {
            return Double.parseDouble(value) * factor;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static void extractMeter(IntsRef edgeFlags, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys) {
        final String rawValue = way.getFirstPriorityTag(keys);
        double value = stringToMeter(rawValue);
        
        if (Double.isNaN(value)) {
            return;
        }

        if (value > valueEncoder.getMaxDecimal())
            value = valueEncoder.getMaxDecimal();
        valueEncoder.setDecimal(false, edgeFlags, value);
    }

    public static double stringToMeter(String value) {
        value = WSPACE_PATTERN.matcher(toLowerCase(value)).replaceAll("");
        value = METER_PATTERN.matcher(value).replaceAll("m");
        value = INCH_PATTERN.matcher(value).replaceAll("in");
        value = FEET_PATTERN.matcher(value).replaceAll("ft");
        if (isInvalidValue(value))
            return Double.NaN;
        double factor = 1;
        double offset = 0;
        if (value.startsWith("~") || value.contains("approx")) {
            value = APPROX_PATTERN.matcher(value).replaceAll("").trim();
            factor = 0.8;
        }

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
            try {
                offset = Double.parseDouble(inchValue) * 0.0254;
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }

        if (value.endsWith("ft")) {
            value = value.substring(0, value.length() - 2);
            factor *= 0.3048;
        } else if (value.endsWith("cm")) {
            value = value.substring(0, value.length() - 2);
            factor *= 0.01;
        } else if (value.endsWith("m")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.isEmpty()) {
            return offset;
        }
        
        try {
            return Double.parseDouble(value) * factor + offset;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    public static boolean isInvalidValue(String value) {
        value = toLowerCase(value);
        return value.isEmpty() || value.startsWith("default") || value.equals("none") || value.equals("unknown")
                || value.contains("unrestricted") || value.startsWith("〜")
                || value.contains("narrow") || value.equals("unsigned") || value.equals("fixme") || value.equals("small")
                || value.contains(";") || value.contains(":") || value.contains("(")
                || value.contains(">") || value.contains("<") || value.contains("-")
                // only support '.' and no German decimals
                || value.contains(",");
    }
}