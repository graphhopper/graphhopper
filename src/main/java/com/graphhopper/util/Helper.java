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

import com.graphhopper.routing.AStar;
import com.graphhopper.routing.AStarBidirection;
import com.graphhopper.routing.DijkstraBidirection;
import com.graphhopper.routing.DijkstraBidirectionRef;
import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.util.AlgorithmPreparation;
import com.graphhopper.routing.util.NoOpAlgorithmPreparation;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.shapes.BBox;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
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
 * @author Peter Karich,
 */
public class Helper {

    private static Logger logger = LoggerFactory.getLogger(Helper.class);
    public static final int MB = 1 << 20;

    private Helper() {
    }

    public static BufferedReader createBuffReader(File file) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    }

    public static BufferedReader createBuffReader(InputStream is) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(is, "UTF-8"));
    }

    public static BufferedWriter createBuffWriter(File file) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    }

    public static void loadProperties(Map<String, String> map, Reader tmpReader) throws IOException {
        BufferedReader reader = new BufferedReader(tmpReader);
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("//") || line.startsWith("#"))
                    continue;

                if (line.isEmpty())
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
            if (need <= (1 << i) - 12)
                return (1 << i) - 12;
        }

        return need;
    }

    /**
     * @return a sorted list where the object with the highest integer value comes first!
     */
    public static <T> List<Entry<T, Integer>> sort(Collection<Entry<T, Integer>> entrySet) {
        List<Entry<T, Integer>> sorted = new ArrayList<Entry<T, Integer>>(entrySet);
        Collections.sort(sorted, new Comparator<Entry<T, Integer>>() {
            @Override
            public int compare(Entry<T, Integer> o1, Entry<T, Integer> o2) {
                int i1 = o1.getValue();
                int i2 = o2.getValue();
                if (i1 < i2)
                    return 1;
                else if (i1 > i2)
                    return -1;
                else
                    return 0;
            }
        });

        return sorted;
    }

    /**
     * @return a sorted list where the string with the highest integer value comes first!
     */
    public static <T> List<Entry<T, Long>> sortLong(Collection<Entry<T, Long>> entrySet) {
        List<Entry<T, Long>> sorted = new ArrayList<Entry<T, Long>>(entrySet);
        Collections.sort(sorted, new Comparator<Entry<T, Long>>() {
            @Override
            public int compare(Entry<T, Long> o1, Entry<T, Long> o2) {
                long i1 = o1.getValue();
                long i2 = o2.getValue();
                if (i1 < i2)
                    return 1;
                else if (i1 > i2)
                    return -1;
                else
                    return 0;
            }
        });

        return sorted;
    }

    public static void deleteDir(File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteDir(f);
            }
        }

        file.delete();
    }

    public static void deleteFilesStartingWith(String string) {
        File specificFile = new File(string);
        File pFile = specificFile.getParentFile();
        if (pFile != null) {
            for (File f : pFile.listFiles()) {
                if (f.getName().startsWith(specificFile.getName()))
                    f.delete();
            }
        }
    }

    public static String getMemInfo() {
        return "totalMB:" + Runtime.getRuntime().totalMemory() / MB
                + ", usedMB:" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MB;
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
            if (cl != null)
                cl.close();
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't close resource", ex);
        }
    }

    public static boolean isEmpty(String strOsm) {
        return strOsm == null || strOsm.trim().isEmpty();
    }

    public static void writeSettings(String file, Object... objs) throws IOException {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(file), 1024));
        try {
            out.writeInt(objs.length);
            for (Object obj : objs) {
                char cl;
                if (obj instanceof Byte)
                    cl = 'Y';
                else
                    cl = obj.getClass().getSimpleName().charAt(0);

                out.writeChar(cl);
                if (obj instanceof String)
                    out.writeUTF((String) obj);
                else if (obj instanceof Float)
                    out.writeFloat((Float) obj);
                else if (obj instanceof Double)
                    out.writeDouble((Double) obj);
                else if (obj instanceof Integer)
                    out.writeInt((Integer) obj);
                else if (obj instanceof Long)
                    out.writeLong((Long) obj);
                else if (obj instanceof Boolean)
                    out.writeBoolean((Boolean) obj);
                else
                    throw new IllegalStateException("Unsupported type");
            }
        } finally {
            out.close();
        }
    }

    public static String readString(InputStream is, String encoding) throws IOException {
        InputStream in = is instanceof BufferedInputStream
                ? (BufferedInputStream) is : new BufferedInputStream(is);;
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

    public static Object[] readSettings(String file) throws IOException {
        DataInputStream in = new DataInputStream(new FileInputStream(file));
        try {
            Object[] res = new Object[in.readInt()];
            for (int i = 0; i < res.length; i++) {
                char cl = in.readChar();
                switch (cl) {
                    case 'S':
                        res[i] = in.readUTF();
                        break;
                    case 'F':
                        res[i] = in.readFloat();
                        break;
                    case 'D':
                        res[i] = in.readDouble();
                        break;
                    case 'I':
                        res[i] = in.readInt();
                        break;
                    case 'L':
                        res[i] = in.readLong();
                        break;
                    case 'B':
                        res[i] = in.readBoolean();
                        break;
                    case 'Y':
                        res[i] = in.readByte();
                        break;
                    default:
                        throw new IllegalStateException("cannot read type " + cl + " from " + file);
                }
            }
            return res;
        } finally {
            in.close();
        }
    }

    public static void writeInts(String file, int[] ints) throws IOException {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(file), 4 * 1024));
        try {
            writeInts(out, ints);
        } finally {
            out.close();
        }
    }

    public static void writeInts(DataOutputStream out, int[] ints) throws IOException {
        int len = ints.length;
        out.writeInt(len);
        for (int i = 0; i < len; i++) {
            out.writeInt(ints[i]);
        }
    }

    public static void writeFloats(String file, float[] floats) throws IOException {
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new FileOutputStream(file), 4 * 1024));
        try {
            int len = floats.length;
            out.writeInt(len);
            for (int i = 0; i < len; i++) {
                out.writeFloat(floats[i]);
            }
        } finally {
            out.close();
        }
    }

    public static int[] readInts(String file) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(
                new FileInputStream(file), 4 * 1024));
        try {
            return readInts(in);
        } finally {
            in.close();
        }
    }

    public static int[] readInts(DataInputStream in) throws IOException {
        int len = in.readInt();
        int[] ints = new int[len];
        for (int i = 0; i < len; i++) {
            ints[i] = in.readInt();
        }
        return ints;
    }

    public static float[] readFloats(String file) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(
                new FileInputStream(file), 4 * 1024));
        try {
            int len = in.readInt();
            float[] floats = new float[len];
            for (int i = 0; i < len; i++) {
                floats[i] = in.readFloat();
            }
            return floats;
        } finally {
            in.close();
        }
    }

    /**
     * Creates a preparation wrapper for the specified algorithm. Warning/TODO: set the _graph for
     * the instance otherwise you'll get NPE when calling createAlgo. Possible values for
     * algorithmStr: astar (A* algorithm), astarbi (bidirectional A*) dijkstra (Dijkstra),
     * dijkstrabi and dijkstraNative (a bit faster bidirectional Dijkstra).
     */
    public static AlgorithmPreparation createAlgoPrepare(final String algorithmStr) {
        return new NoOpAlgorithmPreparation() {
            @Override public RoutingAlgorithm createAlgo() {
                return createAlgoFromString(_graph, algorithmStr);
            }
        };
    }

    /**
     * Possible values: astar (A* algorithm), astarbi (bidirectional A*) dijkstra (Dijkstra),
     * dijkstrabi and dijkstraNative (a bit faster bidirectional Dijkstra).
     */
    public static RoutingAlgorithm createAlgoFromString(Graph g, String algorithmStr) {
        if (g == null)
            throw new NullPointerException("You have to specify a graph different from null!");
        RoutingAlgorithm algo;
        if ("dijkstrabi".equalsIgnoreCase(algorithmStr))
            algo = new DijkstraBidirectionRef(g);
        else if ("dijkstraNative".equalsIgnoreCase(algorithmStr))
            algo = new DijkstraBidirection(g);
        else if ("dijkstra".equalsIgnoreCase(algorithmStr))
            algo = new DijkstraSimple(g);
        else if ("astarbi".equalsIgnoreCase(algorithmStr))
            algo = new AStarBidirection(g).setApproximation(true);
        else
            algo = new AStar(g);
        return algo;
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
            Helper.deleteDir(from);

        return true;
    }

    public static int calcIndexSize(BBox graphBounds) {
        double dist = new DistanceCalc().calcDist(graphBounds.maxLat, graphBounds.minLon, graphBounds.minLat, graphBounds.maxLon);
        // convert to km and maximum 5000km => 25mio capacity, minimum capacity is 2000
        dist = Math.min(dist / 1000, 5000);
        return Math.max(2000, (int) (dist * dist));
    }

    public static String pruneFileEnd(String file) {
        int index = file.lastIndexOf(".");
        if (index < 0)
            return file;
        return file.substring(0, index);
    }
    /**
     * The file version is independent of the real world version. E.g. to make major version jumps
     * without the need to change the file version.
     */
    public static final int VERSION_FILE = 2;
    /**
     * The version without the snapshot string
     */
    public static final String VERSION;
    public static final boolean SNAPSHOT;

    static {
        String version = "0.0";
        try {
            List<String> v = readFile(new InputStreamReader(Helper.class.getResourceAsStream("/version"), "UTF-8"));
            version = v.get(0);
        } catch (Exception ex) {
            System.err.println("GraphHopper Initialization ERROR: cannot read version!? " + ex.getMessage());
        }
        int indexM = version.indexOf("-");
        int indexP = version.indexOf(".");
        if ("${project.version}".equals(version)) {
            VERSION = "0.0";
            SNAPSHOT = true;
            System.err.println("GraphHopper Initialization WARNING: maven did not preprocess the version file!?");
        } else if ("0.0".equals(version) || indexM < 0 || indexP >= indexM) {
            VERSION = "0.0";
            SNAPSHOT = true;
            System.err.println("GraphHopper Initialization WARNING: cannot get version!?");
        } else {
            // throw away the "-SNAPSHOT"
            int major = -1, minor = -1;
            try {
                major = Integer.parseInt(version.substring(0, indexP));
                minor = Integer.parseInt(version.substring(indexP + 1, indexM));
            } catch (Exception ex) {
                System.err.println("GraphHopper Initialization WARNING: cannot parse version!? " + ex.getMessage());
            }
            SNAPSHOT = version.toLowerCase().contains("-snapshot");
            VERSION = major + "." + minor;
        }
    }
}
