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
package com.graphhopper.reader.dem

import com.graphhopper.storage.DAType
import com.graphhopper.storage.Directory
import com.graphhopper.storage.GHDirectory
import com.graphhopper.util.Downloader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * Provides basic methods that are usually used in an ElevationProvider using tiles from files.
 *
 * @author Robin Boldt
 */
abstract class TileBasedElevationProvider protected constructor(
    private val cacheDirString: String
) : ElevationProvider {

    @JvmField
    internal val logger: Logger = LoggerFactory.getLogger(javaClass)

    @JvmField
    internal var downloader: Downloader? = null

    @JvmField
    internal var cacheDir: File? = null

    @JvmField
    internal var baseUrl: String? = null

    @JvmField
    internal var dir: Directory? = null

    @JvmField
    internal var daType: DAType = DAType.MMAP

    @JvmField
    internal var interpolate: Boolean = false

    @JvmField
    internal var autoRemoveTemporary: Boolean = true

    @JvmField
    internal var sleep: Long = 2000

    override fun init(): ElevationProvider {
        val cacheDir = File(cacheDirString)
        if (cacheDir.exists() && !cacheDir.isDirectory)
            throw IllegalArgumentException("Cache path has to be a directory")
        try {
            this.cacheDir = cacheDir.canonicalFile
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
        return this
    }

    /**
     * Configuration option to use bilinear interpolation to find the elevation at a point from the
     * surrounding elevation points. Has only an effect if called before the first getEle call.
     * Turned off by default.
     */
    open fun setInterpolate(interpolate: Boolean): TileBasedElevationProvider {
        this.interpolate = interpolate
        return this
    }

    override fun canInterpolate(): Boolean = this.interpolate

    @JvmName("setSleep")
    internal fun setSleep(sleep: Long) {
        this.sleep = sleep
    }

    /**
     * Specifies the service URL where to download the elevation data. An empty string should set it
     * to the default URL. Default is a provider-dependent URL which should work out of the box.
     */
    open fun setBaseURL(baseUrl: String?): TileBasedElevationProvider {
        if (baseUrl == null || baseUrl.isEmpty())
            throw IllegalArgumentException("baseUrl cannot be empty")

        this.baseUrl = baseUrl
        return this
    }

    /**
     * Set to true if you have a small area and need high speed access. Default is DAType.MMAP
     */
    open fun setDAType(daType: DAType): TileBasedElevationProvider {
        this.daType = daType
        return this
    }

    /**
     * Creating temporary files can take a long time as we need to unpack them as well as to fill
     * our DataAccess object, so this option can be used to disable the default clear mechanism via
     * specifying 'false'.
     */
    open fun setAutoRemoveTemporaryFiles(autoRemoveTemporary: Boolean): TileBasedElevationProvider {
        this.autoRemoveTemporary = autoRemoveTemporary
        return this
    }

    open fun setDownloader(downloader: Downloader): TileBasedElevationProvider {
        this.downloader = downloader
        return this
    }

    protected fun getCacheDir(): File? = cacheDir

    protected fun getDirectory(): Directory {
        val dir = this.dir
        if (dir != null)
            return dir

        logger.info(this.toString() + " Elevation Provider, from: " + baseUrl + ", to: " + cacheDir + ", as: " + daType +
                " using interpolate: " + interpolate)
        val newDir = GHDirectory(cacheDir!!.absolutePath, daType)
        this.dir = newDir
        return newDir
    }
}
