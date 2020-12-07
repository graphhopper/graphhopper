// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.graphhopper.reader.ReaderBlock;

/**
 * An OSM data source reading from a PBF file. The entire contents of the file are read.
 * <p>
 *
 * @author Brett Henderson
 * @author Thomas Butz
 */
public class PbfReader implements Runnable {
    private final File file;
    private final ExecutorService executorService;
    private final BlockingQueue<Future<ReaderBlock>> resultQueue;
    private final AtomicBoolean parsing;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param file        The file to read.
     * @param executor    The {@link ExecutorService} used to process blobs
     * @param resultQueue Queue to receive blob decoding results
     * @param parsing     allows to abort parsing
     */
    public PbfReader(File file, ExecutorService executor, BlockingQueue<Future<ReaderBlock>> resultQueue, AtomicBoolean parsing) {
        this.file = file;
        this.executorService = executor;
        this.resultQueue = resultQueue;
        this.parsing = parsing;
    }

    @Override
    public void run() {
        try (FileInputStream fin = new FileInputStream(file); BufferedInputStream bin = new BufferedInputStream(fin, 50000);
                        DataInputStream din = new DataInputStream(bin)) {
            
            validatePbfHeader(din);
            
            PbfStreamSplitter streamSplitter = new PbfStreamSplitter(din);
            while (parsing.get() && streamSplitter.hasNext()) {
                PbfRawBlob rawBlob = streamSplitter.next();
                Future<ReaderBlock> futureBlockResult = executorService.submit(new PbfBlobDecoder(rawBlob));
                resultQueue.put(futureBlockResult);
            }
            // signal EOF
            resultQueue.put(new StaticFuture<>(new ReaderBlock(null)));
        } catch (Exception e) {
            try {
                resultQueue.put(new StaticFuture<>(null, e));
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void validatePbfHeader(DataInputStream in) throws IOException {
        in.mark(10);
        // check file header
        byte[] header = new byte[6];
        if (in.read(header) < 0) {
            throw new IOException("Unable to read from input file: " + file.getPath());
        }
        in.reset();
        
        if (header[0] == 0 && header[1] == 0 && header[2] == 0 && header[4] == 10 && header[5] == 9
                        && (header[3] == 13 || header[3] == 14)) {
            return;
        }
        throw new IOException("Not a valid pbf file: " + file.getPath());
    }
    
    private static class StaticFuture<V> implements Future<V> {
        
        private final V result;
        private final Exception exception;
        
        public StaticFuture(V result) {
            this(result, null);
        }
        
        public StaticFuture(V result, Exception e) {
            this.result = result;
            this.exception = e;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public V get() throws ExecutionException {
            if (exception != null) {
                throw new ExecutionException(exception);
            }
            return result;
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws ExecutionException {
            return get();
        }
        
    }
}
