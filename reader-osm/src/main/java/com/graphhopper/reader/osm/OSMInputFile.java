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

import java.io.*;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A readable OSM file.
 * <p>
 *
 * @author Nop
 */
public class OSMInputFile implements Sink, OSMInput {
    private static final int MAX_BATCH_SIZE = 1_000;
    private final InputStream bis;
    private final BlockingQueue<ReaderElement> itemQueue;
    private final Queue<ReaderElement> itemBatch;
    Thread pbfReaderThread;
    private boolean eof;
    private boolean hasIncomingData;
    private int workerThreads = -1;

    public OSMInputFile(File file) throws IOException {
        bis = decode(file);
        itemQueue = new LinkedBlockingQueue<>(50_000);
        itemBatch = new ArrayDeque<>(MAX_BATCH_SIZE);
    }

    public OSMInputFile open() {
        hasIncomingData = true;
        if (workerThreads <= 0)
            workerThreads = 1;

        PbfReader reader = new PbfReader(bis, this, workerThreads);
        pbfReaderThread = new Thread(reader, "PBF Reader");
        pbfReaderThread.start();
        return this;
    }

    /**
     * Currently on for pbf format. Default is number of cores.
     */
    public OSMInputFile setWorkerThreads(int num) {
        workerThreads = num;
        return this;
    }

    private InputStream decode(File file) throws IOException {
        InputStream ips = null;
        try {
            ips = new BufferedInputStream(new FileInputStream(file), 50000);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        ips.mark(10);

        // check file header
        byte[] header = new byte[6];
        if (ips.read(header) < 0)
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());

        if (header[0] == 0 && header[1] == 0 && header[2] == 0
                && header[4] == 10 && header[5] == 9
                && (header[3] == 13 || header[3] == 14)) {
            ips.reset();
            return ips;
        }
        
        throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());
    }

    @Override
    public ReaderElement getNext() {
        if (eof)
            throw new IllegalStateException("EOF reached");

        ReaderElement item = getNextPBF();

        if (item != null)
            return item;

        eof = true;
        return null;
    }

    public boolean isEOF() {
        return eof;
    }

    @Override
    public void close() throws IOException {
        eof = true;
        bis.close();
        // if exception happened on OSMInputFile-thread we need to shutdown the pbf handling
        if (pbfReaderThread != null && pbfReaderThread.isAlive())
            pbfReaderThread.interrupt();
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

    public int getUnprocessedElements() {
        return itemQueue.size() + itemBatch.size();
    }

    @Override
    public void complete() {
        hasIncomingData = false;
    }

    private ReaderElement getNextPBF() {
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
}
