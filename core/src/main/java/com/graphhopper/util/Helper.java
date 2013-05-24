/*
 *  Copyright 2011 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import com.graphhopper.routing.util.BikeFlagEncoder;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EdgePropertyEncoder;
import com.graphhopper.routing.util.FootFlagEncoder;
import com.graphhopper.util.shapes.BBox;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Several utility classes which are compatible with Java6 on Android.
 *
 * @see Helper7 for none-Android compatible methods.
 * @author Peter Karich
 */
public class Helper {

    private static Logger logger = LoggerFactory.getLogger(Helper.class);
    public static final int MB = 1 << 20;

    public static ArrayList<Integer> tIntListToArrayList(TIntList from) {
        int len = from.size();
        ArrayList<Integer> list = new ArrayList<Integer>(len);
        for (int i = 0; i < len; i++) {
            list.add(from.get(i));
        }
        return list;
    }

    private Helper() {
    }

    public static void loadProperties(Map<String, String> map, Reader tmpReader) throws IOException {
        BufferedReader reader = new BufferedReader(tmpReader);
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("//") || line.startsWith("#"))
                    continue;

                if (Helper.isEmpty(line))
                    continue;

                int index = line.indexOf("=");
                if (index < 0) {
                    logger.warn("Skipping configuration at line:" + line);
                    continue;
                }

                String field = line.substring(0, index);
                String value = line.substring(index + 1);
                map.put(field, value);
            }
        } finally {
            reader.close();
        }
    }

    public static void saveProperties(Map<String, String> map, Writer tmpWriter) throws IOException {
        BufferedWriter writer = new BufferedWriter(tmpWriter);
        try {
            for (Entry<String, String> e : map.entrySet()) {
                writer.append(e.getKey());
                writer.append('=');
                writer.append(e.getValue());
                writer.append('\n');
            }
        } finally {
            writer.close();
        }
    }

    public static List<String> readFile(String file) throws IOException {
        return readFile(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    }

    public static List<String> readFile(Reader simpleReader) throws IOException {
        BufferedReader reader = new BufferedReader(simpleReader);
        try {
            List<String> res = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                res.add(line);
            }
            return res;
        } finally {
            reader.close();
        }
    }

    public static int idealIntArraySize(int need) {
        return idealByteArraySize(need * 4) / 4;
    }

    public static int idealByteArraySize(int need) {
        for (int i = 4; i < 32; i++) {
            if (need <= (1 << i) - 12) {
                return (1 << i) - 12;
            }
        }
        return need;
    }

    public static boolean removeDir(File file) {
        if (!file.exists())
            return true;

        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                removeDir(f);
            }
        }

        return file.delete();
    }

    public static long totalMB() {
        return Runtime.getRuntime().totalMemory() / MB;
    }

    public static long usedMB() {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB;
    }

    public static String memInfo() {
        return "totalMB:" + totalMB() + ", usedMB:" + usedMB();
    }

    public static int sizeOfObjectRef(int factor) {
        // pointer to class, flags, lock
        return factor * (4 + 4 + 4);
    }

    public static int sizeOfLongArray(int length, int factor) {
        // pointer to class, flags, lock, size
        return factor * (4 + 4 + 4 + 4) + 8 * length;
    }

    public static int sizeOfObjectArray(int length, int factor) {
        // TODO add 4byte to make a multiple of 8 in some cases
        // TODO compressed oop
        return factor * (4 + 4 + 4 + 4) + 4 * length;
    }

    public static void close(Closeable cl) {
        try {
            if (cl != null) {
                cl.close();
            }
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't close resource", ex);
        }
    }

    public static boolean isEmpty(String strOsm) {
        return strOsm == null || strOsm.trim().length() == 0;
    }

    public static EdgePropertyEncoder getVehicleEncoder(String str) {
        str = str.toLowerCase();
        if (str.isEmpty() || "car".equals(str))
            return new CarFlagEncoder();
        else if ("foot".equals(str))
            return new FootFlagEncoder();
        else if ("bike".equals(str))
            return new BikeFlagEncoder();
        throw new RuntimeException("VehicleEncoder not found " + str);
    }

    /**
     * Determines if the specified ByteBuffer is one which maps to a file!
     */
    public static boolean isFileMapped(ByteBuffer bb) {
        if (bb instanceof MappedByteBuffer) {
            try {
                ((MappedByteBuffer) bb).isLoaded();
                return true;
            } catch (UnsupportedOperationException ex) {
            }
        }
        return false;
    }

    public static void unzip(String from, boolean remove) throws IOException {
        String to = pruneFileEnd(from);
        unzip(from, to, remove);
    }

    public static boolean unzip(String fromStr, String toStr, boolean remove) throws IOException {
        File from = new File(fromStr);
        File to = new File(toStr);
        if (!from.exists() || fromStr.equals(toStr))
            return false;

        if (!to.exists())
            to.mkdirs();

        ZipInputStream zis = new ZipInputStream(new FileInputStream(from));
        try {
            ZipEntry ze = zis.getNextEntry();
            byte[] buffer = new byte[1024];
            while (ze != null) {
                if (ze.isDirectory()) {
                    new File(to, ze.getName()).mkdir();
                } else {
                    File newFile = new File(to, ze.getName());
                    FileOutputStream fos = new FileOutputStream(newFile);
                    try {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    } finally {
                        fos.close();
                    }
                }
                ze = zis.getNextEntry();
            }
            zis.closeEntry();
        } finally {
            zis.close();
        }

        if (remove)
            Helper.removeDir(from);

        return true;
    }

    public static int calcIndexSize(BBox graphBounds) {
        if (!graphBounds.isValid())
            throw new IllegalArgumentException("Bounding box is not valid to calculate index size: " + graphBounds);
        double dist = new DistanceCalc().calcDist(graphBounds.maxLat, graphBounds.minLon,
                graphBounds.minLat, graphBounds.maxLon);
        // convert to km and maximum is 50000km => 1GB
        dist = Math.min(dist / 1000, 50000);
        return Math.max(2000, (int) (dist * dist));
    }

    public static String pruneFileEnd(String file) {
        int index = file.lastIndexOf(".");
        if (index < 0)
            return file;
        return file.substring(0, index);
    }

    public static TIntList createTList(int... list) {
        TIntList res = new TIntArrayList(list.length);
        for (int val : list) {
            res.add(val);
        }
        return res;
    }

    public static PointList createPointList(double... list) {
        if (list.length % 2 != 0)
            throw new IllegalArgumentException("list should consist of lat,lon pairs!");
        PointList res = new PointList(list.length);
        int max = list.length / 2;
        for (int i = 0; i < max; i++) {
            res.add(list[2 * i], list[2 * i + 1]);
        }
        return res;
    }

    /**
     * Converts a double (maximum value 10000) into an integer.
     *
     * @return the integer to be stored
     */
    public static int doubleToInt(double deg) {
        return (int) (deg * INT_FACTOR);
    }

    /**
     * Converts back the once transformed storedInt from doubleToInt
     */
    public static double intToDouble(int storedInt) {
        return (double) storedInt / INT_FACTOR;
    }

    /**
     * Converts into an integer to be compatible with the still limited
     * DataAccess class (accepts only integer values). But this conversation
     * also reduces memory consumption where the precision loss is accceptable.
     * As +- 180° and +-90° are assumed as maximum values.
     *
     * @return the integer of the specified degree
     */
    public static int degreeToInt(double deg) {
        return (int) (deg * DEGREE_FACTOR);
    }

    /**
     * Converts back the integer value.
     *
     * @return the degree value of the specified integer
     */
    public static double intToDegree(int storedInt) {
        // Double.longBitsToDouble();
        return (double) storedInt / DEGREE_FACTOR;
    }
    // +- 180 and +-90 => let use use 400
    private static final float DEGREE_FACTOR = Integer.MAX_VALUE / 400f;
    private static final float INT_FACTOR = Integer.MAX_VALUE / 10000f;

    public static void cleanMappedByteBuffer(final ByteBuffer buffer) {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override public Object run() throws Exception {
                    final Method getCleanerMethod = buffer.getClass().getMethod("cleaner");
                    getCleanerMethod.setAccessible(true);
                    final Object cleaner = getCleanerMethod.invoke(buffer);
                    if (cleaner != null)
                        cleaner.getClass().getMethod("clean").invoke(cleaner);
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("unable to unmap the mapped buffer", e);
        }
    }

    public static String nf(long no) {
        // I like french localization the most: 123654 will be 123 654 instead
        // of comma vs. point confusion for english/german guys.
        // NumberFormat is not thread safe => but getInstance looks like it's cached
        return NumberFormat.getInstance(Locale.FRANCE).format(no);
    }
}
