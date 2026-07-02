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
package com.graphhopper.reader.osm.pbf

import com.graphhopper.reader.ReaderElement
import com.graphhopper.reader.osm.OSMInput
import com.graphhopper.reader.osm.SkipOptions
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayDeque
import java.util.Collections
import java.util.Deque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Pipelined PBF reader: blobs.map(decode).flatten
 *
 * - Reader thread: splits stream into blobs
 * - Coordinator thread: submits blobs to workers, queues decoded results in order
 * - Worker threads: decode blobs in parallel
 * - Consumer: iterates through queued results via getNext()
 */
class PbfReader(
    private val inputStream: InputStream,
    private val workers: Int,
    private val skipOptions: SkipOptions
) : OSMInput {

    private val blobQueue: BlockingQueue<PbfRawBlob> = ArrayBlockingQueue(workers * 2)
    private val resultQueue: BlockingQueue<List<ReaderElement>> = LinkedBlockingQueue(workers * 2)

    @Volatile
    private var readerException: Throwable? = null

    @Volatile
    private var coordinatorException: Throwable? = null

    @Volatile
    private var coordinatorDone = false
    private var eof = false

    private var currentBatch: Iterator<ReaderElement> = Collections.emptyIterator()
    private var readerThread: Thread? = null
    private var coordinatorThread: Thread? = null
    private var decoderExecutor: ExecutorService? = null

    fun start(): PbfReader {
        decoderExecutor = Executors.newFixedThreadPool(workers)
        readerThread = Thread({ runReader() }, "PBF-IO-Reader")
        coordinatorThread = Thread({ runCoordinator() }, "PBF-Coordinator")
        readerThread!!.start()
        coordinatorThread!!.start()
        return this
    }

    override fun getNext(): ReaderElement? {
        if (eof)
            throw IllegalStateException("EOF reached")

        while (!currentBatch.hasNext()) {
            if (coordinatorDone && resultQueue.isEmpty()) {
                checkExceptions()
                eof = true
                return null
            }
            try {
                val batch = resultQueue.poll(100, TimeUnit.MILLISECONDS)
                if (batch != null) {
                    currentBatch = batch.iterator()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                eof = true
                return null
            }
        }
        return currentBatch.next()
    }

    private fun runReader() {
        try {
            val splitter = PbfStreamSplitter(DataInputStream(inputStream))
            try {
                while (splitter.hasNext()) {
                    blobQueue.put(splitter.next())
                }
            } finally {
                splitter.release()
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            readerException = t
        } finally {
            try {
                blobQueue.put(END_OF_STREAM)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun runCoordinator() {
        try {
            val pending: Deque<Future<List<ReaderElement>>> = ArrayDeque()
            val maxPending = workers + 1

            while (true) {
                // Fill pending queue
                while (pending.size < maxPending) {
                    val blob = blobQueue.poll(50, TimeUnit.MILLISECONDS)
                    if (blob == null) {
                        checkReaderException()
                        break
                    }
                    if (blob === END_OF_STREAM) {
                        drainAll(pending)
                        return
                    }
                    pending.addLast(decoderExecutor!!.submit(Callable {
                        PbfBlobDecoder(blob.type, blob.data, skipOptions).decode()
                    }))
                }

                checkReaderException()

                // Send completed results (in order)
                while (!pending.isEmpty() && pending.peekFirst().isDone) {
                    resultQueue.put(pending.pollFirst().get())
                }

                // If full, block on first result
                if (pending.size >= maxPending && !pending.isEmpty()) {
                    resultQueue.put(pending.pollFirst().get())
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            coordinatorException = t
        } finally {
            coordinatorDone = true
            decoderExecutor!!.shutdownNow()
        }
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    private fun drainAll(pending: Deque<Future<List<ReaderElement>>>) {
        while (!pending.isEmpty()) {
            resultQueue.put(pending.pollFirst().get())
        }
    }

    private fun checkReaderException() {
        readerException?.let {
            throw RuntimeException("PBF reader thread failed", it)
        }
    }

    private fun checkExceptions() {
        readerException?.let {
            throw RuntimeException("Unable to read PBF file.", it)
        }
        coordinatorException?.let {
            throw RuntimeException("Unable to read PBF file.", it)
        }
    }

    @Throws(IOException::class)
    override fun close() {
        checkExceptions()
        eof = true
        readerThread?.let { if (it.isAlive) it.interrupt() }
        coordinatorThread?.let { if (it.isAlive) it.interrupt() }
        inputStream.close()
    }

    companion object {
        private val END_OF_STREAM = PbfRawBlob("END", ByteArray(0))
    }
}
