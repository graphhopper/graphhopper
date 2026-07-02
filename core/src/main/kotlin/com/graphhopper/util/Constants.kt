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
package com.graphhopper.util

import com.graphhopper.GraphHopper
import java.io.InputStreamReader
import java.util.StringTokenizer

/**
 * Defining several important constants for GraphHopper. Partially taken from Lucene.
 */
object Constants {
    /**
     * The value of `System.getProperty("java.version")`. *
     */
    @JvmField
    val JAVA_VERSION: String = System.getProperty("java.version")

    /**
     * The value of `System.getProperty("os.name")`. *
     */
    @JvmField
    val OS_NAME: String = System.getProperty("os.name")

    /**
     * True iff running on Linux.
     */
    @JvmField
    val LINUX: Boolean = OS_NAME.startsWith("Linux")

    /**
     * True iff running on Windows.
     */
    @JvmField
    val WINDOWS: Boolean = OS_NAME.startsWith("Windows")

    /**
     * True iff running on Mac OS X
     */
    @JvmField
    val MAC_OS_X: Boolean = OS_NAME.startsWith("Mac OS X")

    @JvmField
    val OS_ARCH: String = System.getProperty("os.arch")

    @JvmField
    val OS_VERSION: String = System.getProperty("os.version")

    @JvmField
    val JAVA_VENDOR: String = System.getProperty("java.vendor")

    @JvmField
    val JVM_SPEC_VERSION: String = System.getProperty("java.specification.version")

    @JvmField
    val JRE_IS_MINIMUM_JAVA9: Boolean

    const val VERSION_NODE = 9
    const val VERSION_EDGE = 24

    // this should be increased whenever the format of the serialized EncodingManager is changed
    const val VERSION_EM = 4
    const val VERSION_SHORTCUT = 11
    const val VERSION_NODE_CH = 0
    const val VERSION_GEOMETRY = 9
    const val VERSION_TURN_COSTS = 0
    const val VERSION_LOCATION_IDX = 5
    const val VERSION_KV_STORAGE = 2

    /**
     * The version without the snapshot string
     */
    @JvmField
    val VERSION: String

    @JvmField
    val BUILD_DATE: String

    /**
     * Details about the git commit this artifact was built for, can be null (if not built using maven)
     */
    @JvmField
    val GIT_INFO: GitInfo?

    @JvmField
    val SNAPSHOT: Boolean

    init {
        val st = StringTokenizer(JVM_SPEC_VERSION, ".")
        val jvmMajorVersion = Integer.parseInt(st.nextToken())
        val jvmMinorVersion = if (st.hasMoreTokens()) Integer.parseInt(st.nextToken()) else 0
        JRE_IS_MINIMUM_JAVA9 = jvmMajorVersion > 1 || (jvmMajorVersion == 1 && jvmMinorVersion >= 9)

        var version = "0.0"
        try {
            // see com/graphhopper/version file in resources which is modified in the maven packaging process
            // to contain the current version
            val v = Helper.readFile(InputStreamReader(GraphHopper::class.java.getResourceAsStream("version"), Helper.UTF_CS))
            version = v[0]
        } catch (ex: Exception) {
            System.err.println("GraphHopper Initialization ERROR: cannot read version!? " + ex.message)
        }
        val indexM = version.indexOf("-")
        if ("\${project.version}" == version) {
            VERSION = "0.0"
            SNAPSHOT = true
            System.err.println("GraphHopper Initialization WARNING: maven did not preprocess the version file! Do not use the jar for a release!")
        } else if ("0.0" == version) {
            VERSION = "0.0"
            SNAPSHOT = true
            System.err.println("GraphHopper Initialization WARNING: cannot get version!?")
        } else {
            var tmp = version
            // throw away the "-SNAPSHOT"
            if (indexM >= 0)
                tmp = version.substring(0, indexM)

            SNAPSHOT = Helper.toLowerCase(version).contains("-snapshot")
            VERSION = tmp
        }
        var buildDate = ""
        try {
            val v = Helper.readFile(InputStreamReader(GraphHopper::class.java.getResourceAsStream("builddate"), Helper.UTF_CS))
            buildDate = v[0]
        } catch (ex: Exception) {
        }
        BUILD_DATE = buildDate

        var gitInfos: List<String>? = null
        try {
            val g: List<String> = Helper.readFile(InputStreamReader(GraphHopper::class.java.getResourceAsStream("gitinfo"), Helper.UTF_CS))
            gitInfos = g
            if (g.size != 6) {
                System.err.println("GraphHopper Initialization WARNING: unexpected git info: " + g.toString())
                gitInfos = null
            } else if (g[1].startsWith("$")) {
                gitInfos = null
            }
        } catch (ex: Exception) {
        }
        GIT_INFO = if (gitInfos == null) null else GitInfo(gitInfos[1], gitInfos[2], gitInfos[3], gitInfos[4], java.lang.Boolean.parseBoolean(gitInfos[5]))
    }

    @JvmStatic
    fun getVersions(): String {
        return VERSION_NODE.toString() + "," + VERSION_EDGE + "," + VERSION_GEOMETRY + "," + VERSION_LOCATION_IDX +
                "," + VERSION_KV_STORAGE + "," + VERSION_SHORTCUT
    }

    @JvmStatic
    fun getMajorVersion(): String {
        val firstIdx = VERSION.indexOf(".")
        if (firstIdx < 0)
            throw IllegalStateException("Cannot extract major version from version $VERSION")

        val sndIdx = VERSION.indexOf(".", firstIdx + 1)
        if (sndIdx < 0)
            return VERSION

        return VERSION.substring(0, sndIdx)
    }
}
