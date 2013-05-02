package com.graphhopper.http;

import com.graphhopper.util.PointList;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;

/**
 * @author Peter Karich
 */
public class WebHelper {

    public static String encodeURL(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception _ignore) {
            return str;
        }
    }

    public static PointList decodePolyline(String encoded, int initCap) {
        PointList poly = new PointList(initCap);
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            // latitude
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLatitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += deltaLatitude;

            // longitute
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int deltaLongitude = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += deltaLongitude;
            poly.add((double) lat / 1E5, (double) lng / 1E5);
        }
        return poly;
    }

    // https://developers.google.com/maps/documentation/utilities/polylinealgorithm?hl=de
    public static String encodePolyline(PointList poly) {
        StringBuilder sb = new StringBuilder();
        int size = poly.size();
        int prevLat = 0;
        int prevLon = 0;
        for (int i = 0; i < size; i++) {
            int num = (int) Math.floor(poly.latitude(i) * 1e5);
            encodeNumber(sb, num - prevLat);
            prevLat = num;
            num = (int) Math.floor(poly.longitude(i) * 1e5);
            encodeNumber(sb, num - prevLon);
            prevLon = num;
        }
        return sb.toString();
    }

    private static void encodeNumber(StringBuilder sb, int num) {
        num = num << 1;
        if (num < 0)
            num = ~num;
        while (num >= 0x20) {
            int nextValue = (0x20 | (num & 0x1f)) + 63;
            sb.append((char) (nextValue));
            num >>= 5;
        }
        num += 63;
        sb.append((char) (num));
    }

    public static String readString(InputStream inputStream) throws IOException {
        String encoding = "UTF-8";
        InputStream in = new BufferedInputStream(inputStream, 4096);
        try {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int numRead;
            while ((numRead = in.read(buffer)) != -1) {
                output.write(buffer, 0, numRead);
            }
            return output.toString(encoding);
        } finally {
            in.close();
        }
    }
}
