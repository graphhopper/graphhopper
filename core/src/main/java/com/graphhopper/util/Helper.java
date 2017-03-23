/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
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

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * Several utility classes which are compatible with Java6 on Android.
 * <p>
 *
 * @author Peter Karich
 * @see Helper7 for none-Android compatible methods.
 */
public class Helper {
    public static final DistanceCalc DIST_EARTH = new DistanceCalcEarth();
    public static final DistanceCalc3D DIST_3D = new DistanceCalc3D();
    public static final DistancePlaneProjection DIST_PLANE = new DistancePlaneProjection();
    public static final AngleCalc ANGLE_CALC = new AngleCalc();
    public static final Charset UTF_CS = Charset.forName("UTF-8");
    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    public static final long MB = 1L << 20;
    private static final Logger LOGGER = LoggerFactory.getLogger(Helper.class);
    // +- 180 and +-90 => let use use 400
    private static final float DEGREE_FACTOR = Integer.MAX_VALUE / 400f;
    // milli meter is a bit extreme but we have integers
    private static final float ELE_FACTOR = 1000f;

    private Helper() {
    }

    public static ArrayList<Integer> intListToArrayList(IntIndexedContainer from) {
        int len = from.size();
        ArrayList<Integer> list = new ArrayList<Integer>(len);
        for (int i = 0; i < len; i++) {
            list.add(from.get(i));
        }
        return list;
    }

    public static Locale getLocale(String param) {
        int pointIndex = param.indexOf('.');
        if (pointIndex > 0)
            param = param.substring(0, pointIndex);

        param = param.replace("-", "_");
        int index = param.indexOf("_");
        if (index < 0) {
            return new Locale(param);
        }
        return new Locale(param.substring(0, index), param.substring(index + 1));
    }

    static String packageToPath(Package pkg) {
        return pkg.getName().replaceAll("\\.", File.separator);
    }

    public static int countBitValue(int maxTurnCosts) {
        if (maxTurnCosts < 0)
            throw new IllegalArgumentException("maxTurnCosts cannot be negative " + maxTurnCosts);

        int counter = 0;
        while (maxTurnCosts > 0) {
            maxTurnCosts >>= 1;
            counter++;
        }
        return counter++;
    }

    public static void loadProperties(Map<String, String> map, Reader tmpReader) throws IOException {
        BufferedReader reader = new BufferedReader(tmpReader);
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("//") || line.startsWith("#")) {
                    continue;
                }

                if (Helper.isEmpty(line)) {
                    continue;
                }

                int index = line.indexOf("=");
                if (index < 0) {
                    LOGGER.warn("Skipping configuration at line:" + line);
                    continue;
                }

                String field = line.substring(0, index);
                String value = line.substring(index + 1);
                map.put(field.trim(), value.trim());
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
        return readFile(new InputStreamReader(new FileInputStream(file), UTF_CS));
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

    public static String isToString(InputStream inputStream) throws IOException {
        int size = 1024 * 8;
        String encoding = "UTF-8";
        InputStream in = new BufferedInputStream(inputStream, size);
        try {
            byte[] buffer = new byte[size];
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
        if (!file.exists()) {
            return true;
        }

        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                removeDir(f);
            }
        }

        return file.delete();
    }

    public static long getTotalMB() {
        return Runtime.getRuntime().totalMemory() / MB;
    }

    public static long getUsedMB() {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB;
    }

    public static String getMemInfo() {
        return "totalMB:" + getTotalMB() + ", usedMB:" + getUsedMB();
    }

    public static int getSizeOfObjectRef(int factor) {
        // pointer to class, flags, lock
        return factor * (4 + 4 + 4);
    }

    public static int getSizeOfLongArray(int length, int factor) {
        // pointer to class, flags, lock, size
        return factor * (4 + 4 + 4 + 4) + 8 * length;
    }

    public static int getSizeOfObjectArray(int length, int factor) {
        // improvements: add 4byte to make a multiple of 8 in some cases plus compressed oop
        return factor * (4 + 4 + 4 + 4) + 4 * length;
    }

    public static void close(Closeable cl) {
        try {
            if (cl != null)
                cl.close();
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't close resource", ex);
        }
    }

    public static boolean isEmpty(String str) {
        return str == null || str.trim().length() == 0;
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

    public static int calcIndexSize(BBox graphBounds) {
        if (!graphBounds.isValid())
            throw new IllegalArgumentException("Bounding box is not valid to calculate index size: " + graphBounds);

        double dist = DIST_EARTH.calcDist(graphBounds.maxLat, graphBounds.minLon,
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

    public static List<Double> createDoubleList(double[] values) {
        List<Double> list = new ArrayList<>();
        for (double v : values) {
            list.add(v);
        }
        return list;
    }

    public static IntArrayList createTList(int... list) {
        return IntArrayList.from(list);
    }

    public static PointList createPointList(double... list) {
        if (list.length % 2 != 0)
            throw new IllegalArgumentException("list should consist of lat,lon pairs!");

        int max = list.length / 2;
        PointList res = new PointList(max, false);
        for (int i = 0; i < max; i++) {
            res.add(list[2 * i], list[2 * i + 1], Double.NaN);
        }
        return res;
    }

    public static PointList createPointList3D(double... list) {
        if (list.length % 3 != 0)
            throw new IllegalArgumentException("list should consist of lat,lon,ele tuples!");

        int max = list.length / 3;
        PointList res = new PointList(max, true);
        for (int i = 0; i < max; i++) {
            res.add(list[3 * i], list[3 * i + 1], list[3 * i + 2]);
        }
        return res;
    }

    /**
     * Converts into an integer to be compatible with the still limited DataAccess class (accepts
     * only integer values). But this conversion also reduces memory consumption where the precision
     * loss is acceptable. As +- 180° and +-90° are assumed as maximum values.
     * <p>
     *
     * @return the integer of the specified degree
     */
    public static final int degreeToInt(double deg) {
        if (deg >= Double.MAX_VALUE)
            return Integer.MAX_VALUE;
        if (deg <= -Double.MAX_VALUE)
            return -Integer.MAX_VALUE;
        return (int) (deg * DEGREE_FACTOR);
    }

    /**
     * Converts back the integer value.
     * <p>
     *
     * @return the degree value of the specified integer
     */
    public static final double intToDegree(int storedInt) {
        if (storedInt == Integer.MAX_VALUE)
            return Double.MAX_VALUE;
        if (storedInt == -Integer.MAX_VALUE)
            return -Double.MAX_VALUE;
        return (double) storedInt / DEGREE_FACTOR;
    }

    /**
     * Converts elevation value (in meters) into integer for storage.
     */
    public static final int eleToInt(double ele) {
        if (ele >= Integer.MAX_VALUE)
            return Integer.MAX_VALUE;
        return (int) (ele * ELE_FACTOR);
    }

    /**
     * Converts the integer value retrieved from storage into elevation (in meters). Do not expect
     * more precision than meters although it currently is!
     */
    public static final double intToEle(int integEle) {
        if (integEle == Integer.MAX_VALUE)
            return Double.MAX_VALUE;
        return integEle / ELE_FACTOR;
    }

    public static void cleanMappedByteBuffer(final ByteBuffer buffer) {
        // TODO avoid reflection on every call
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                    if (Constants.JAVA_VERSION.equals("9-ea")) {
                        // >=JDK9 class sun.misc.Unsafe { void invokeCleaner(ByteBuffer buf) }
                        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                        // we do not need to check for a specific class, we can call the Unsafe method with any buffer class
                        MethodHandle unmapper = MethodHandles.lookup().findVirtual(unsafeClass, "invokeCleaner",
                                MethodType.methodType(void.class, ByteBuffer.class));
                        // fetch the unsafe instance and bind it to the virtual MethodHandle
                        final Field f = unsafeClass.getDeclaredField("theUnsafe");
                        f.setAccessible(true);
                        final Object theUnsafe = f.get(null);
                        try {
                            unmapper.bindTo(theUnsafe).invokeExact(buffer);
                            return null;
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    }

                    if (buffer.getClass().getSimpleName().equals("MappedByteBufferAdapter")) {
                        if (!Constants.ANDROID)
                            throw new RuntimeException("MappedByteBufferAdapter only supported for Android at the moment");

                        // For Android 4.1 call ((MappedByteBufferAdapter)buffer).free() see #914
                        Class<?> directByteBufferClass = Class.forName("java.nio.MappedByteBufferAdapter");
                        callBufferFree(buffer, directByteBufferClass);
                    } else {
                        // <=JDK8 class DirectByteBuffer { sun.misc.Cleaner cleaner(Buffer buf) }
                        //        then call sun.misc.Cleaner.clean
                        final Class<?> directByteBufferClass = Class.forName("java.nio.DirectByteBuffer");
                        try {
                            final Method dbbCleanerMethod = directByteBufferClass.getMethod("cleaner");
                            dbbCleanerMethod.setAccessible(true);
                            // call: cleaner = ((DirectByteBuffer)buffer).cleaner()
                            final Object cleaner = dbbCleanerMethod.invoke(buffer);
                            if (cleaner != null) {
                                final Class<?> cleanerMethodReturnType = dbbCleanerMethod.getReturnType();
                                final Method cleanMethod = cleanerMethodReturnType.getDeclaredMethod("clean");
                                cleanMethod.setAccessible(true);
                                // call: ((sun.misc.Cleaner)cleaner).clean()
                                cleanMethod.invoke(cleaner);
                            }
                        } catch (NoSuchMethodException ex2) {
                            if (Constants.ANDROID)
                                // For Android 5.1.1 call ((DirectByteBuffer)buffer).free() see #933
                                callBufferFree(buffer, directByteBufferClass);
                            else
                                // ignore if method cleaner or clean is not available
                                LOGGER.warn("NoSuchMethodException | " + Constants.JAVA_VERSION, ex2);
                        }
                    }

                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Unable to unmap the mapped buffer", e);
        }
    }

    private static void callBufferFree(ByteBuffer buffer, Class<?> directByteBufferClass)
            throws InvocationTargetException, IllegalAccessException {
        try {
            final Method dbbFreeMethod = directByteBufferClass.getMethod("free");
            dbbFreeMethod.setAccessible(true);
            dbbFreeMethod.invoke(buffer);
        } catch (NoSuchMethodException ex2) {
            LOGGER.warn("NoSuchMethodException | " + Constants.JAVA_VERSION, ex2);
        }
    }

    /**
     * Trying to force the release of the mapped ByteBuffer. See
     * http://stackoverflow.com/q/2972986/194609 and use only if you know what you are doing.
     */
    public static void cleanHack() {
        System.gc();
    }

    public static String nf(long no) {
        // I like french localization the most: 123654 will be 123 654 instead
        // of comma vs. point confusion for English/German people.
        // NumberFormat is not thread safe => but getInstance looks like it's cached
        return NumberFormat.getInstance(Locale.FRANCE).format(no);
    }

    public static String firstBig(String sayText) {
        if (sayText == null || sayText.length() <= 0) {
            return sayText;
        }

        return Character.toUpperCase(sayText.charAt(0)) + sayText.substring(1);
    }

    /**
     * This methods returns the value or min if too small or max if too big.
     */
    public static final double keepIn(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * Round the value to the specified exponent
     */
    public static double round(double value, int exponent) {
        double factor = Math.pow(10, exponent);
        return Math.round(value * factor) / factor;
    }

    public static final double round6(double value) {
        return Math.round(value * 1e6) / 1e6;
    }

    public static final double round4(double value) {
        return Math.round(value * 1e4) / 1e4;
    }

    public static final double round2(double value) {
        return Math.round(value * 100) / 100d;
    }

    /**
     * This creates a date formatter for yyyy-MM-dd'T'HH:mm:ss'Z' which is has to be identical to
     * buildDate used in pom.xml
     */
    public static DateFormat createFormatter() {
        return createFormatter("yyyy-MM-dd'T'HH:mm:ss'Z'");
    }

    /**
     * Creates a SimpleDateFormat with the UK locale.
     */
    public static DateFormat createFormatter(String str) {
        DateFormat df = new SimpleDateFormat(str, Locale.UK);
        df.setTimeZone(UTC);
        return df;
    }

    /**
     * This method handles the specified (potentially negative) int as unsigned bit representation
     * and returns the positive converted long.
     */
    public static final long toUnsignedLong(int x) {
        return ((long) x) & 0xFFFFffffL;
    }

    /**
     * Converts the specified long back into a signed int (reverse method for toUnsignedLong)
     */
    public static final int toSignedInt(long x) {
        return (int) x;
    }

    public static final String camelCaseToUnderScore(String key) {
        if (key.isEmpty())
            return key;

        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c))
                sb.append("_").append(Character.toLowerCase(c));
            else
                sb.append(c);
        }

        return sb.toString();
    }

    public static final String underScoreToCamelCase(String key) {
        if (key.isEmpty())
            return key;

        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                i++;
                if (i < key.length())
                    sb.append(Character.toUpperCase(key.charAt(i)));
                else
                    sb.append(c);
            } else
                sb.append(c);
        }

        return sb.toString();
    }
}
