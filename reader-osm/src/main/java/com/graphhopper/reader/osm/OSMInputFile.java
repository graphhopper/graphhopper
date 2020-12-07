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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.graphhopper.reader.ReaderBlock;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.pbf.PbfReader;

/**
 * A readable OSM file.
 * <p>
 *
 * @author Nop
 */
public class OSMInputFile implements OSMInput {
    private static final int MAX_CONCURRENT_BLOBS = 10;
    
    private final ExecutorService executorService;
    private final BlockingQueue<Future<ReaderBlock>> itemQueue;
    private final AtomicBoolean parsing = new AtomicBoolean(true);
    private final Thread pbfReaderThread;
    private Iterator<ReaderElement> iter;
    
    public OSMInputFile(File file) {
        this(file, 1);
    }

    public OSMInputFile(File file, int workerThreads) {
        this.executorService = Executors.newFixedThreadPool(workerThreads);
        this.itemQueue = new LinkedBlockingQueue<>(MAX_CONCURRENT_BLOBS);
        PbfReader reader = new PbfReader(file, executorService, itemQueue, parsing);
        this.pbfReaderThread = new Thread(reader, "PBF Reader");
        this.pbfReaderThread.setDaemon(true);
        this.pbfReaderThread.start();
    }

    @Override
    public ReaderElement getNext() {
        if (iter != null && iter.hasNext()) {
            return iter.next();
        }
        
        ReaderBlock block;
        try {
            Future<ReaderBlock> futureBlock = itemQueue.take();
            block = futureBlock.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        
        if (block.getElements() == null) {
            return null; // signal EOF
        }
        
        this.iter = block.getElements().iterator();
        if (iter.hasNext()) {
            return iter.next();
        }
        
        return getNext();
    }

    @Override
    public void close() throws IOException {
        // if exception happened on OSMInputFile-thread we need to shutdown the pbf handling
        parsing.set(false);
        if (pbfReaderThread.isAlive()) {
            pbfReaderThread.interrupt();
            try {
                pbfReaderThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(100L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public int getUnprocessedElements() {
        return itemQueue.size();
    }
}
