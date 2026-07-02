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

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.function.LongConsumer
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * @author Peter Karich
 */
open class Downloader {
    private var referrer = "http://graphhopper.com"
    private val acceptEncoding = "gzip, deflate"
    private var timeout = 4000

    open fun setTimeout(timeout: Int): Downloader {
        this.timeout = timeout
        return this
    }

    open fun setReferrer(referrer: String): Downloader {
        this.referrer = referrer
        return this
    }

    /**
     * This method initiates a connect call of the provided connection and returns the response
     * stream. It only returns the error stream if it is available and readErrorStreamNoException is
     * true otherwise it throws an IOException if an error happens. Furthermore it wraps the stream
     * to decompress it if the connection content encoding is specified.
     */
    @Throws(IOException::class)
    open fun fetch(connection: HttpURLConnection, readErrorStreamNoException: Boolean): InputStream {
        // create connection but before reading get the correct inputstream based on the compression and if error
        connection.connect()

        val rawStream: InputStream? =
            if (readErrorStreamNoException && connection.responseCode >= 400 && connection.errorStream != null)
                connection.errorStream
            else
                connection.inputStream

        if (rawStream == null)
            throw IOException("Stream is null. Message:" + connection.responseMessage)

        // wrap
        var stream = rawStream
        try {
            val encoding = connection.contentEncoding
            if (encoding != null && encoding.equals("gzip", ignoreCase = true))
                stream = GZIPInputStream(stream, BUFFER_SIZE)
            else if (encoding != null && encoding.equals("deflate", ignoreCase = true))
                stream = InflaterInputStream(stream, Inflater(true), BUFFER_SIZE)
        } catch (ex: IOException) {
        }

        return stream
    }

    @Throws(IOException::class)
    open fun fetch(url: String): InputStream = fetch(createConnection(url), false)

    @Throws(IOException::class)
    open fun createConnection(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        // Will yield in a POST request: conn.setDoOutput(true);
        conn.doInput = true
        conn.useCaches = true
        conn.setRequestProperty("Referrer", referrer)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        // suggest respond to be gzipped or deflated (which is just another compression)
        // http://stackoverflow.com/q/3932117
        conn.setRequestProperty("Accept-Encoding", acceptEncoding)
        conn.readTimeout = timeout
        conn.connectTimeout = timeout
        return conn
    }

    @Throws(IOException::class)
    open fun downloadFile(url: String, toFile: String) {
        val conn = createConnection(url)
        val target = Paths.get(toFile)
        val tmpFile = target.resolveSibling(target.fileName.toString() + ".part")
        fetch(conn, false).use { input ->
            Files.newOutputStream(tmpFile).use { out ->
                input.transferTo(out)
            }
        }
        Files.move(tmpFile, target, StandardCopyOption.ATOMIC_MOVE)
    }

    @Throws(IOException::class)
    open fun downloadAndUnzip(url: String, toFolder: String, progressListener: LongConsumer) {
        val conn = createConnection(url)
        val length = conn.contentLength
        val iStream = fetch(conn, false)

        Unzipper().unzip(iStream, File(toFolder)) { sumBytes -> progressListener.accept((100 * sumBytes / length).toInt().toLong()) }
    }

    @Throws(IOException::class)
    open fun downloadAsString(url: String, readErrorStreamNoException: Boolean): String =
        Helper.isToString(fetch(createConnection(url), readErrorStreamNoException))

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private val USER_AGENT = "graphhopper/" + Constants.VERSION

        @JvmStatic
        @Throws(IOException::class)
        fun main(args: Array<String>) {
            Downloader().downloadAndUnzip("http://graphhopper.com/public/maps/0.1/europe_germany_berlin.ghz", "somefolder"
            ) { value -> println("progress:$value") }
        }
    }
}
