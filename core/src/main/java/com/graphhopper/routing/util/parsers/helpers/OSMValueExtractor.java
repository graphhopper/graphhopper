package com.graphhopper.routing.util.parsers.helpers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.routing.ev.MaxSpeed;
import com.graphhopper.util.DistanceCalcEarth;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

import static com.graphhopper.util.Helper.toLowerCase;

public class OSMValueExtractor {

    public static final double MAXSPEED_NONE = -1;

    private static final Pattern TON_PATTERN = Pattern.compile("tons?");
    private static final Pattern MGW_PATTERN = Pattern.compile("mgw");
    private static final Pattern WSPACE_PATTERN = Pattern.compile("\\s");
    private static final Pattern METER_PATTERN = Pattern.compile("meters?|mtrs?|mt|m\\.");
    private static final Pattern INCH_PATTERN = Pattern.compile("\"|\'\'");
    private static final Pattern FEET_PATTERN = Pattern.compile("\'|feet");
    private static final Pattern APPROX_PATTERN = Pattern.compile("~|approx");
    private static final Logger logger = LoggerFactory.getLogger(OSMValueExtractor.class);

    private OSMValueExtractor() {
        // utility class
    }

    public static void extractTons(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys) {
        final String rawValue = way.getFirstValue(keys);
        double value = stringToTons(rawValue);

        if (Double.isNaN(value)) value = Double.POSITIVE_INFINITY;

        valueEncoder.setDecimal(false, edgeId, edgeIntAccess, value);
        // too many
//        if (value - valueEncoder.getDecimal(false, edgeFlags) > 2)
//            logger.warn("Value " + value + " for " + valueEncoder.getName() + " was too large and truncated to " + valueEncoder.getDecimal(false, edgeFlags));
    }

    /**
     * This parses the weight for a conditional value like "delivery @ (weight > 7.5)"
     */
    public static double conditionalWeightToTons(String value) {
        try {
            int index = value.indexOf("weight>"); // maxweight or weight
            if (index < 0) {
                index = value.indexOf("weight >");
                if (index > 0) index += "weight >".length();
            } else {
                index += "weight>".length();
            }
            if (index > 0) {
                int lastIndex = value.indexOf(')', index); // (value) or value
                if (lastIndex < 0) lastIndex = value.length() - 1;
                if (lastIndex > index)
                    return OSMValueExtractor.stringToTons(value.substring(index, lastIndex));
            }
            return Double.NaN;
        } catch (Exception ex) {
            throw new RuntimeException("value " + value, ex);
        }
    }

    public static double stringToTons(String value) {
        value = TON_PATTERN.matcher(toLowerCase(value)).replaceAll("t");
        value = MGW_PATTERN.matcher(value).replaceAll("").trim();
        if (isInvalidValue(value))
            return Double.NaN;

        double factor = 1;
        if (value.endsWith("st")) {
            value = value.substring(0, value.length() - 2);
            factor = 0.907194048807;
        } else if (value.endsWith("t")) {
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

    public static void extractMeter(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, DecimalEncodedValue valueEncoder, List<String> keys) {
        final String rawValue = way.getFirstValue(keys);
        double value = stringToMeter(rawValue);

        if (Double.isNaN(value)) value = Double.POSITIVE_INFINITY;

        valueEncoder.setDecimal(false, edgeId, edgeIntAccess, value);
        // too many
//        if (value - valueEncoder.getDecimal(false, edgeFlags) > 2)
//            logger.warn("Value " + value + " for " + valueEncoder.getName() + " was too large and truncated to " + valueEncoder.getDecimal(false, edgeFlags));
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

    /**
     ** @return the speed in km/h, or {@link MaxSpeed.UNSET_SPEED} if the string is invalid, or {@link MAXSPEED_NONE} in case it equals 'none'
     */
    public static double stringToKmh(String str) {
        if (Helper.isEmpty(str))
            return MaxSpeed.UNSET_SPEED;

        if ("walk".equals(str.trim()))
            return 6;

        if ("none".equals(str.trim()))
            // Special case intended to be used when there is actually no speed limit and drivers
            // can go as fast as they want like on parts of the German Autobahn. However, in OSM
            // this is sometimes misused by mappers trying to indicate that there is no additional
            // sign apart from the general speed limit.
            return MAXSPEED_NONE;

        int mpInteger = str.indexOf("mp");
        int knotInteger = str.indexOf("knots");
        int kmInteger = str.indexOf("km");
        int kphInteger = str.indexOf("kph");

        double factor;
        if (mpInteger > 0) {
            str = str.substring(0, mpInteger).trim();
            factor = DistanceCalcEarth.KM_MILE;
        } else if (knotInteger > 0) {
            str = str.substring(0, knotInteger).trim();
            factor = 1.852; // see https://en.wikipedia.org/wiki/Knot_%28unit%29#Definitions
        } else {
            if (kmInteger > 0) {
                str = str.substring(0, kmInteger).trim();
            } else if (kphInteger > 0) {
                str = str.substring(0, kphInteger).trim();
            }
            factor = 1;
        }

        double value;
        try {
            value = Double.parseDouble(str) * factor;
        } catch (Exception ex) {
            return MaxSpeed.UNSET_SPEED;
        }

        if (value < 4.8)
            // We consider maxspeed < 4.8km/h a bug in OSM data and act as if the tag wasn't there.
            // The limit is chosen such that maxspeed=3mph is still valid, because there actually are
            // some road signs using 3mph.
            // https://github.com/graphhopper/graphhopper/pull/3077#discussion_r1826842203
            return MaxSpeed.UNSET_SPEED;

        return value;
    }
}
