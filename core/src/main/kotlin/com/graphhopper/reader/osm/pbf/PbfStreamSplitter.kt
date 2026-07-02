// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf

import crosby.binary.Fileformat
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Parses a PBF data stream and extracts the raw data of each blob in sequence until the end of the
 * stream is reached.
 *
 * @param pbfStream The PBF data stream to be parsed.
 * @author Brett Henderson
 */
class PbfStreamSplitter(pbfStream: DataInputStream) : MutableIterator<PbfRawBlob> {
    private var dis: DataInputStream? = pbfStream
    private var dataBlockCount = 0
    private var eof = false
    private var nextBlob: PbfRawBlob? = null

    @Throws(IOException::class)
    private fun readHeader(headerLength: Int): Fileformat.BlobHeader {
        val headerBuffer = ByteArray(headerLength)
        dis!!.readFully(headerBuffer)

        return Fileformat.BlobHeader.parseFrom(headerBuffer)
    }

    @Throws(IOException::class)
    private fun readRawBlob(blobHeader: Fileformat.BlobHeader): ByteArray {
        val rawBlob = ByteArray(blobHeader.datasize)

        dis!!.readFully(rawBlob)

        return rawBlob
    }

    private fun getNextBlob() {
        try {
            // Read the length of the next header block. This is the only time
            // we should expect to encounter an EOF exception. In all other
            // cases it indicates a corrupt or truncated file.
            val headerLength: Int
            try {
                headerLength = dis!!.readInt()
            } catch (e: EOFException) {
                eof = true
                return
            }

            if (log.isLoggable(Level.FINER)) {
                log.finer("Reading header for blob " + dataBlockCount++)
            }
            val blobHeader = readHeader(headerLength)

            if (log.isLoggable(Level.FINER)) {
                log.finer("Processing blob of type " + blobHeader.type + ".")
            }
            val blobData = readRawBlob(blobHeader)

            nextBlob = PbfRawBlob(blobHeader.type, blobData)

        } catch (e: IOException) {
            throw RuntimeException("Unable to get next blob from PBF stream.", e)
        }
    }

    override fun hasNext(): Boolean {
        if (nextBlob == null && !eof) {
            getNextBlob()
        }

        return nextBlob != null
    }

    override fun next(): PbfRawBlob {
        val result = nextBlob!!
        nextBlob = null

        return result
    }

    override fun remove() {
        throw UnsupportedOperationException()
    }

    fun release() {
        val d = dis
        if (d != null) {
            try {
                d.close()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
        dis = null
    }

    companion object {
        private val log: Logger = Logger.getLogger(PbfStreamSplitter::class.java.name)
    }
}
