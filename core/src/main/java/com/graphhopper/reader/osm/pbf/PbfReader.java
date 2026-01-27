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
package com.graphhopper.reader.osm.pbf;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.SkipOptions;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;

/**
 * Pipelined PBF reader with separate threads for reading and coordination.
 * <p>
 * Architecture:
 * - Reader thread: reads blobs from stream, puts in queue (I/O bound)
 * - Coordinator (this thread): takes from queue, submits to workers, sends results to sink
 * - Worker threads: decode blobs (CPU bound, parallelized)
 * <p>
 * This pipelines I/O with CPU work, so reading continues while decoding happens.
 */
public class PbfReader implements Runnable {
    private static final PbfRawBlob END_OF_STREAM = new PbfRawBlob("END", new byte[0]);

    private final InputStream inputStream;
    private final Sink sink;
    private final int workers;
    private final SkipOptions skipOptions;

    private final BlockingQueue<PbfRawBlob> blobQueue;
    private volatile Throwable readerException;
    private volatile Throwable coordinatorException;

    public PbfReader(InputStream in, Sink sink, int workers, SkipOptions skipOptions) {
        this.inputStream = in;
        this.sink = sink;
        this.workers = workers;
        this.skipOptions = skipOptions;
        // Bounded queue provides backpressure if coordinator falls behind
        this.blobQueue = new ArrayBlockingQueue<>(workers * 2);
    }

    @Override
    public void run() {
        ExecutorService decoderExecutor = Executors.newFixedThreadPool(workers);
        Thread readerThread = new Thread(this::runReader, "PBF-IO-Reader");
        readerThread.start();

        try {
            runCoordinator(decoderExecutor);
        } catch (Throwable t) {
            coordinatorException = t;
        } finally {
            sink.complete();
            decoderExecutor.shutdownNow();
            readerThread.interrupt();
            try {
                readerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Reader thread: reads blobs and puts them in queue. Pure I/O, no coordination.
     */
    private void runReader() {
        try {
            PbfStreamSplitter splitter = new PbfStreamSplitter(new DataInputStream(inputStream));
            try {
                while (splitter.hasNext()) {
                    PbfRawBlob blob = splitter.next();
                    blobQueue.put(blob);
                }
            } finally {
                splitter.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            readerException = t;
        } finally {
            // Signal end of stream
            try {
                blobQueue.put(END_OF_STREAM);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Coordinator: takes blobs from queue, submits to workers, sends results to sink in order.
     */
    private void runCoordinator(ExecutorService executor) throws InterruptedException, ExecutionException {
        // Track pending decode results, maintaining blob order
        Deque<Future<List<ReaderElement>>> pendingResults = new ArrayDeque<>();
        int maxPending = workers + 1;

        while (true) {
            // Submit new work while we have capacity
            while (pendingResults.size() < maxPending) {
                // Use poll with timeout to periodically check for reader errors
                PbfRawBlob blob = blobQueue.poll(50, TimeUnit.MILLISECONDS);

                if (blob == null) {
                    // Queue empty, check for reader errors and process any ready results
                    checkReaderException();
                    break;
                }

                if (blob == END_OF_STREAM) {
                    // Reader done, drain remaining results and return
                    drainAllResults(pendingResults);
                    return;
                }

                // Submit blob for decoding
                Future<List<ReaderElement>> future = executor.submit(() -> {
                    PbfBlobDecoderSync decoder = new PbfBlobDecoderSync(blob.getType(), blob.getData(), skipOptions);
                    return decoder.decode();
                });
                pendingResults.addLast(future);
            }

            checkReaderException();

            // Send completed results to sink (in order)
            sendCompletedResults(pendingResults);

            // If at max capacity, we must wait for the first result
            if (pendingResults.size() >= maxPending && !pendingResults.isEmpty()) {
                Future<List<ReaderElement>> future = pendingResults.pollFirst();
                sendToSink(future.get());
            }
        }
    }

    private void sendCompletedResults(Deque<Future<List<ReaderElement>>> pendingResults)
            throws ExecutionException, InterruptedException {
        // Send all results that are already done (non-blocking)
        while (!pendingResults.isEmpty() && pendingResults.peekFirst().isDone()) {
            Future<List<ReaderElement>> future = pendingResults.pollFirst();
            sendToSink(future.get());
        }
    }

    private void drainAllResults(Deque<Future<List<ReaderElement>>> pendingResults)
            throws ExecutionException, InterruptedException {
        while (!pendingResults.isEmpty()) {
            Future<List<ReaderElement>> future = pendingResults.pollFirst();
            sendToSink(future.get());
        }
    }

    private void sendToSink(List<ReaderElement> entities) {
        for (ReaderElement entity : entities) {
            sink.process(entity);
        }
    }

    private void checkReaderException() {
        if (readerException != null) {
            throw new RuntimeException("PBF reader thread failed", readerException);
        }
    }

    public void close() {
        if (readerException != null) {
            throw new RuntimeException("Unable to read PBF file.", readerException);
        }
        if (coordinatorException != null) {
            throw new RuntimeException("Unable to read PBF file.", coordinatorException);
        }
    }
}
