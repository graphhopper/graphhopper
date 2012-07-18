/*
 *  Copyright 2011 Peter Karich info@jetsli.de
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
package de.jetsli.graph.util;

import de.jetsli.graph.coll.MyBitSet;
import java.io.*;
import java.nio.MappedByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 *
 * @author Peter Karich, info@jetsli.de
 */
public class Helper {

    public static final int MB = 1 << 20;

    public static BufferedReader createBuffReader(File file) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    }

    public static BufferedReader createBuffReader(InputStream is) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedReader(new InputStreamReader(is, "UTF-8"));
    }

    public static BufferedWriter createBuffWriter(File file) throws FileNotFoundException, UnsupportedEncodingException {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    }

    public static List<String> readFile(String file) throws IOException {
        return readFile(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    }

    public static List<String> readFile(Reader simpleReader) throws IOException {
        BufferedReader reader = new BufferedReader(simpleReader);
        try {
            List<String> res = new ArrayList();
            String line;
            while ((line = reader.readLine()) != null) {
                res.add(line);
            }
            return res;
        } finally {
            reader.close();
        }
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

    public static String getBeanMemInfo() {
        java.lang.management.OperatingSystemMXBean mxbean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sunmxbean = (com.sun.management.OperatingSystemMXBean) mxbean;
        long freeMemory = sunmxbean.getFreePhysicalMemorySize();
        long availableMemory = sunmxbean.getTotalPhysicalMemorySize();
        return "free:" + freeMemory / MB + ", available:" + availableMemory / MB + ", rfree:" + Runtime.getRuntime().freeMemory() / MB;
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

    public static void cleanMappedByteBuffer(MappedByteBuffer mapping) {
        if (mapping == null)
            return;

        sun.misc.Cleaner cleaner = ((sun.nio.ch.DirectBuffer) mapping).cleaner();
        if (cleaner != null)
            cleaner.clean();
    }

    public static void close(XMLStreamReader r) {
        try {
            if (r != null)
                r.close();
        } catch (XMLStreamException ex) {
            throw new RuntimeException("Couldn't close xml reader", ex);
        }
    }

    public static void close(Closeable cl) {
        try {
            if (cl != null)
                cl.close();
        } catch (IOException ex) {
            throw new RuntimeException("Couldn't close resource", ex);
        }
    }

    public static CmdArgs readCmdArgs(String[] args) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String arg : args) {
            String strs[] = arg.split("\\=");
            if (strs.length != 2)
                continue;

            String key = strs[0];
            if (key.startsWith("-")) {
                key = key.substring(1);
            }
            if (key.startsWith("-")) {
                key = key.substring(1);
            }
            String value = strs[1];
            map.put(key, value);
        }

        return new CmdArgs(map);
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
            int len = ints.length;
            out.writeInt(len);
            for (int i = 0; i < len; i++) {
                out.writeInt(ints[i]);
            }
        } finally {
            out.close();
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
            int len = in.readInt();
            int[] ints = new int[len];
            for (int i = 0; i < len; i++) {
                ints[i] = in.readInt();
            }
            return ints;
        } finally {
            in.close();
        }
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
}
