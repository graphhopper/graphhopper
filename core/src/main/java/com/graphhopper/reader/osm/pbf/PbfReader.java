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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * Pipelined PBF reader implementing OSMInput interface.
 * <p>
 * Architecture:
 * - Reader thread: reads blobs from stream, puts in queue (I/O bound)
 * - Coordinator thread: takes from queue, submits to workers, puts results in output queue
 * - Worker threads: decode blobs (CPU bound, parallelized)
 * - Consumer: calls getNext() to take from output queue
 */
public class PbfReader implements OSMInput {
    private static final int MAX_BATCH_SIZE = 1_000;
    private static final PbfRawBlob END_OF_STREAM = new PbfRawBlob("END", new byte[0]);

    private final InputStream inputStream;
    private final int workers;
    private final SkipOptions skipOptions;

    private final BlockingQueue<PbfRawBlob> blobQueue;
    private final BlockingQueue<ReaderElement> itemQueue;
    private final Queue<ReaderElement> itemBatch;

    private volatile Throwable readerException;
    private volatile Throwable coordinatorException;
    private volatile boolean coordinatorDone;
    private boolean eof;

    private Thread readerThread;
    private Thread coordinatorThread;
    private ExecutorService decoderExecutor;

    public PbfReader(InputStream in, int workers, SkipOptions skipOptions) {
        this.inputStream = in;
        this.workers = workers;
        this.skipOptions = skipOptions;
        this.blobQueue = new ArrayBlockingQueue<>(workers * 2);
        this.itemQueue = new LinkedBlockingQueue<>(50_000);
        this.itemBatch = new ArrayDeque<>(MAX_BATCH_SIZE);
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

        ReaderElement item = getNextFromQueue();
        if (item != null)
            return item;

        eof = true;
        return null;
    }

    private ReaderElement getNextFromQueue() {
        while (itemBatch.isEmpty()) {
            if (coordinatorDone && itemQueue.isEmpty()) {
                checkExceptions();
                return null; // EOF
            }

            if (itemQueue.drainTo(itemBatch, MAX_BATCH_SIZE) == 0) {
                try {
                    ReaderElement element = itemQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (element != null) {
                        return element;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }

        return itemBatch.poll();
    }

    @Override
    public int getUnprocessedElements() {
        return itemQueue.size() + itemBatch.size();
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
            try {
                blobQueue.put(END_OF_STREAM);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Coordinator: takes blobs from queue, submits to workers, puts results in output queue.
     */
    private void runCoordinator() {
        try {
            Deque<Future<List<ReaderElement>>> pendingResults = new ArrayDeque<>();
            int maxPending = workers + 1;

            while (true) {
                while (pendingResults.size() < maxPending) {
                    PbfRawBlob blob = blobQueue.poll(50, TimeUnit.MILLISECONDS);

                    if (blob == null) {
                        checkReaderException();
                        break;
                    }

                    if (blob == END_OF_STREAM) {
                        drainAllResults(pendingResults);
                        return;
                    }

                    Future<List<ReaderElement>> future = decoderExecutor.submit(() -> {
                        PbfBlobDecoder decoder = new PbfBlobDecoder(blob.getType(), blob.getData(), skipOptions);
                        return decoder.decode();
                    });
                    pendingResults.addLast(future);
                }

                checkReaderException();
                sendCompletedResults(pendingResults);

                if (pendingResults.size() >= maxPending && !pendingResults.isEmpty()) {
                    Future<List<ReaderElement>> future = pendingResults.pollFirst();
                    sendToQueue(future.get());
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

    private void sendCompletedResults(Deque<Future<List<ReaderElement>>> pendingResults)
            throws ExecutionException, InterruptedException {
        while (!pendingResults.isEmpty() && pendingResults.peekFirst().isDone()) {
            Future<List<ReaderElement>> future = pendingResults.pollFirst();
            sendToQueue(future.get());
        }
    }

    private void drainAllResults(Deque<Future<List<ReaderElement>>> pendingResults)
            throws ExecutionException, InterruptedException {
        while (!pendingResults.isEmpty()) {
            Future<List<ReaderElement>> future = pendingResults.pollFirst();
            sendToQueue(future.get());
        }
    }

    private void sendToQueue(List<ReaderElement> entities) throws InterruptedException {
        for (ReaderElement entity : entities) {
            itemQueue.put(entity);
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
