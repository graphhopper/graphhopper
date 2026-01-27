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
package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.pbf.PbfReader;
import com.graphhopper.reader.osm.pbf.Sink;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * OSM input implementation for PBF (Protocol Buffer Binary) format.
 */
class OSMPbfInput implements Sink, OSMInput {
    private static final int MAX_BATCH_SIZE = 1_000;

    private final InputStream inputStream;
    private final BlockingQueue<ReaderElement> itemQueue;
    private final Queue<ReaderElement> itemBatch;
    private boolean eof;
    private PbfReader pbfReader;
    private Thread pbfReaderThread;
    private volatile boolean hasIncomingData;
    private int workerThreads = 1;
    private SkipOptions skipOptions = SkipOptions.none();

    OSMPbfInput(InputStream inputStream) {
        this.inputStream = inputStream;
        this.itemQueue = new LinkedBlockingQueue<>(50_000);
        this.itemBatch = new ArrayDeque<>(MAX_BATCH_SIZE);
    }

    OSMPbfInput setWorkerThreads(int threads) {
        if (threads > 0)
            this.workerThreads = threads;
        return this;
    }

    OSMPbfInput setSkipOptions(SkipOptions skipOptions) {
        this.skipOptions = skipOptions;
        return this;
    }

    OSMPbfInput open() {
        hasIncomingData = true;
        pbfReader = new PbfReader(inputStream, this, workerThreads, skipOptions);
        pbfReaderThread = new Thread(pbfReader, "PBF Reader");
        pbfReaderThread.start();
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
            if (!hasIncomingData && itemQueue.isEmpty()) {
                return null; // signal EOF
            }

            if (itemQueue.drainTo(itemBatch, MAX_BATCH_SIZE) == 0) {
                try {
                    ReaderElement element = itemQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (element != null) {
                        return element; // short circuit
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null; // signal EOF
                }
            }
        }

        return itemBatch.poll();
    }

    @Override
    public int getUnprocessedElements() {
        return itemQueue.size() + itemBatch.size();
    }

    @Override
    public void process(ReaderElement item) {
        try {
            // blocks if full
            itemQueue.put(item);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void complete() {
        hasIncomingData = false;
    }

    @Override
    public void close() throws IOException {
        try {
            pbfReader.close();
        } finally {
            eof = true;
            inputStream.close();
            // if exception happened on caller thread we need to shutdown the pbf handling
            if (pbfReaderThread != null && pbfReaderThread.isAlive())
                pbfReaderThread.interrupt();
        }
    }
}
