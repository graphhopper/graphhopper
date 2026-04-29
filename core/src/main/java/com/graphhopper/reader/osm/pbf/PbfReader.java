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
import com.graphhopper.reader.osm.OSMInput;
import com.graphhopper.reader.osm.SkipOptions;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/**
 * Pipelined PBF reader: blobs.map(decode).flatten
 * <p>
 * - Reader thread: splits stream into blobs
 * - Coordinator thread: submits blobs to workers, queues decoded results in order
 * - Worker threads: decode blobs in parallel
 * - Consumer: iterates through queued results via getNext()
 */
public class PbfReader implements OSMInput {
    private static final PbfRawBlob END_OF_STREAM = new PbfRawBlob("END", new byte[0]);

    private final InputStream inputStream;
    private final int workers;
    private final SkipOptions skipOptions;

    private final BlockingQueue<PbfRawBlob> blobQueue;
    private final BlockingQueue<List<ReaderElement>> resultQueue;

    private volatile Throwable readerException;
    private volatile Throwable coordinatorException;
    private volatile boolean coordinatorDone;
    private boolean eof;

    private Iterator<ReaderElement> currentBatch = Collections.emptyIterator();
    private Thread readerThread;
    private Thread coordinatorThread;
    private ExecutorService decoderExecutor;

    public PbfReader(InputStream in, int workers, SkipOptions skipOptions) {
        this.inputStream = in;
        this.workers = workers;
        this.skipOptions = skipOptions;
        this.blobQueue = new ArrayBlockingQueue<>(workers * 2);
        this.resultQueue = new LinkedBlockingQueue<>(workers * 2);
    }

    public PbfReader start() {
        decoderExecutor = Executors.newFixedThreadPool(workers);
        readerThread = new Thread(this::runReader, "PBF-IO-Reader");
        coordinatorThread = new Thread(this::runCoordinator, "PBF-Coordinator");
        readerThread.start();
        coordinatorThread.start();
        return this;
    }

    @Override
    public ReaderElement getNext() {
        if (eof)
            throw new IllegalStateException("EOF reached");

        while (!currentBatch.hasNext()) {
            if (coordinatorDone && resultQueue.isEmpty()) {
                checkExceptions();
                eof = true;
                return null;
            }
            try {
                List<ReaderElement> batch = resultQueue.poll(100, TimeUnit.MILLISECONDS);
                if (batch != null) {
                    currentBatch = batch.iterator();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                eof = true;
                return null;
            }
        }
        return currentBatch.next();
    }

    private void runReader() {
        try {
            PbfStreamSplitter splitter = new PbfStreamSplitter(new DataInputStream(inputStream));
            try {
                while (splitter.hasNext()) {
                    blobQueue.put(splitter.next());
                }
            } finally {
                splitter.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            readerException = t;
        } finally {
            try {
                blobQueue.put(END_OF_STREAM);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runCoordinator() {
        try {
            Deque<Future<List<ReaderElement>>> pending = new ArrayDeque<>();
            int maxPending = workers + 1;

            while (true) {
                // Fill pending queue
                while (pending.size() < maxPending) {
                    PbfRawBlob blob = blobQueue.poll(50, TimeUnit.MILLISECONDS);
                    if (blob == null) {
                        checkReaderException();
                        break;
                    }
                    if (blob == END_OF_STREAM) {
                        drainAll(pending);
                        return;
                    }
                    pending.addLast(decoderExecutor.submit(() ->
                            new PbfBlobDecoder(blob.getType(), blob.getData(), skipOptions).decode()));
                }

                checkReaderException();

                // Send completed results (in order)
                while (!pending.isEmpty() && pending.peekFirst().isDone()) {
                    resultQueue.put(pending.pollFirst().get());
                }

                // If full, block on first result
                if (pending.size() >= maxPending && !pending.isEmpty()) {
                    resultQueue.put(pending.pollFirst().get());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            coordinatorException = t;
        } finally {
            coordinatorDone = true;
            decoderExecutor.shutdownNow();
        }
    }

    private void drainAll(Deque<Future<List<ReaderElement>>> pending)
            throws ExecutionException, InterruptedException {
        while (!pending.isEmpty()) {
            resultQueue.put(pending.pollFirst().get());
        }
    }

    private void checkReaderException() {
        if (readerException != null) {
            throw new RuntimeException("PBF reader thread failed", readerException);
        }
    }

    private void checkExceptions() {
        if (readerException != null) {
            throw new RuntimeException("Unable to read PBF file.", readerException);
        }
        if (coordinatorException != null) {
            throw new RuntimeException("Unable to read PBF file.", coordinatorException);
        }
    }

    @Override
    public void close() throws IOException {
        checkExceptions();
        eof = true;
        if (readerThread != null && readerThread.isAlive())
            readerThread.interrupt();
        if (coordinatorThread != null && coordinatorThread.isAlive())
            coordinatorThread.interrupt();
        inputStream.close();
    }
}
