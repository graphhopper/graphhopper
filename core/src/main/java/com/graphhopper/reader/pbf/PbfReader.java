// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.pbf;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An OSM data source reading from a PBF file. The entire contents of the file
 * are read.
 *
 * @author Brett Henderson
 */
public class PbfReader implements Runnable {

    private InputStream inputStream;
    private Sink sink;
    private int workers;

    /**
     * Creates a new instance.
     *
     * @param in The file to read.
     * @param workers The number of worker threads for decoding PBF blocks.
     */
    public PbfReader(InputStream in, Sink sink, int workers) {
        this.inputStream = in;
        this.sink = sink;
        this.workers = workers;
    }

    public void run() {
        PbfStreamSplitter streamSplitter = null;
        ExecutorService executorService = Executors.newFixedThreadPool(workers);

        try {
            // Create a stream splitter to break the PBF stream into blobs.
            streamSplitter = new PbfStreamSplitter(new DataInputStream(inputStream));

            // Process all blobs of data in the stream using threads from the
            // executor service. We allow the decoder to issue an extra blob
            // than there are workers to ensure there is another blob
            // immediately ready for processing when a worker thread completes.
            // The main thread is responsible for splitting blobs from the
            // request stream, and sending decoded entities to the sink.
            PbfDecoder pbfDecoder = new PbfDecoder(streamSplitter, executorService, workers + 1, sink);
            pbfDecoder.run();

            sink.complete();

        } catch (Exception e) {
            throw new RuntimeException("Unable to read PBF file.", e);
        } finally {
            //sink.release();

            executorService.shutdownNow();
        }
    }
}
