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

import com.graphhopper.GraphHopper;

import java.io.InputStreamReader;
import java.util.List;
import java.util.StringTokenizer;

import static com.graphhopper.util.Helper.*;

/**
 * Defining several important constants for GraphHopper. Partially taken from Lucene.
 */
public class Constants {
    /**
     * The value of <code>System.getProperty("java.version")</code>. *
     */
    public static final String JAVA_VERSION = System.getProperty("java.version");

    /**
     * The value of <code>System.getProperty("os.name")</code>. *
     */
    public static final String OS_NAME = System.getProperty("os.name");
    /**
     * True iff running on Linux.
     */
    public static final boolean LINUX = OS_NAME.startsWith("Linux");
    /**
     * True iff running on Windows.
     */
    public static final boolean WINDOWS = OS_NAME.startsWith("Windows");
    /**
     * True iff running on Mac OS X
     */
    public static final boolean MAC_OS_X = OS_NAME.startsWith("Mac OS X");
    public static final String OS_ARCH = System.getProperty("os.arch");
    public static final String OS_VERSION = System.getProperty("os.version");
    public static final String JAVA_VENDOR = System.getProperty("java.vendor");
    public static final String JVM_SPEC_VERSION = System.getProperty("java.specification.version");
    public static final boolean JRE_IS_MINIMUM_JAVA9;
    private static final int JVM_MAJOR_VERSION;
    private static final int JVM_MINOR_VERSION;

    public static final int VERSION_NODE = 9;
    public static final int VERSION_EDGE = 24;
    // this should be increased whenever the format of the serialized EncodingManager is changed
    public static final int VERSION_EM = 4;
    public static final int VERSION_SHORTCUT = 10;
    public static final int VERSION_NODE_CH = 0;
    public static final int VERSION_GEOMETRY = 8;
    public static final int VERSION_TURN_COSTS = 0;
    public static final int VERSION_LOCATION_IDX = 5;
    public static final int VERSION_KV_STORAGE = 2;
    /**
     * The version without the snapshot string
     */
    public static final String VERSION;
    public static final String BUILD_DATE;
    /**
     * Details about the git commit this artifact was built for, can be null (if not built using maven)
     */
    public static final GitInfo GIT_INFO;
    public static final boolean SNAPSHOT;

    static {
        final StringTokenizer st = new StringTokenizer(JVM_SPEC_VERSION, ".");
        JVM_MAJOR_VERSION = Integer.parseInt(st.nextToken());
        if (st.hasMoreTokens()) {
            JVM_MINOR_VERSION = Integer.parseInt(st.nextToken());
        } else {
            JVM_MINOR_VERSION = 0;
        }
        JRE_IS_MINIMUM_JAVA9 = JVM_MAJOR_VERSION > 1 || (JVM_MAJOR_VERSION == 1 && JVM_MINOR_VERSION >= 9);

        String version = "0.0";
        try {
            // see com/graphhopper/version file in resources which is modified in the maven packaging process
            // to contain the current version
            List<String> v = readFile(new InputStreamReader(GraphHopper.class.getResourceAsStream("version"), UTF_CS));
            version = v.get(0);
        } catch (Exception ex) {
            System.err.println("GraphHopper Initialization ERROR: cannot read version!? " + ex.getMessage());
        }
        int indexM = version.indexOf("-");
        if ("${project.version}".equals(version)) {
            VERSION = "0.0";
            SNAPSHOT = true;
            System.err.println("GraphHopper Initialization WARNING: maven did not preprocess the version file! Do not use the jar for a release!");
        } else if ("0.0".equals(version)) {
            VERSION = "0.0";
            SNAPSHOT = true;
            System.err.println("GraphHopper Initialization WARNING: cannot get version!?");
        } else {
            String tmp = version;
            // throw away the "-SNAPSHOT"
            if (indexM >= 0)
                tmp = version.substring(0, indexM);

            SNAPSHOT = toLowerCase(version).contains("-snapshot");
            VERSION = tmp;
        }
        String buildDate = "";
        try {
            List<String> v = readFile(new InputStreamReader(GraphHopper.class.getResourceAsStream("builddate"), UTF_CS));
            buildDate = v.get(0);
        } catch (Exception ex) {
        }
        BUILD_DATE = buildDate;

        List<String> gitInfos = null;
        try {
            gitInfos = readFile(new InputStreamReader(GraphHopper.class.getResourceAsStream("gitinfo"), UTF_CS));
            if (gitInfos.size() != 6) {
                System.err.println("GraphHopper Initialization WARNING: unexpected git info: " + gitInfos.toString());
                gitInfos = null;
            } else if (gitInfos.get(1).startsWith("$")) {
                gitInfos = null;
            }
        } catch (Exception ex) {
        }
        GIT_INFO = gitInfos == null ? null : new GitInfo(gitInfos.get(1), gitInfos.get(2), gitInfos.get(3), gitInfos.get(4), Boolean.parseBoolean(gitInfos.get(5)));
    }

    public static String getVersions() {
        return VERSION_NODE + "," + VERSION_EDGE + "," + VERSION_GEOMETRY + "," + VERSION_LOCATION_IDX
                + "," + VERSION_KV_STORAGE + "," + VERSION_SHORTCUT;
    }

    public static String getMajorVersion() {
        int firstIdx = VERSION.indexOf(".");
        if (firstIdx < 0)
            throw new IllegalStateException("Cannot extract major version from version " + VERSION);

        int sndIdx = VERSION.indexOf(".", firstIdx + 1);
        if (sndIdx < 0)
            return VERSION;
        return VERSION.substring(0, sndIdx);
    }
}
