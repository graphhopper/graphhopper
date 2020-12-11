// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
    private final AtomicBoolean parsing = new AtomicBoolean(true);

    /**
     * Creates a new instance.
     * <p>
     *
     * @param file        The file to read.
     * @param executor    The {@link ExecutorService} used to process blobs
     * @param resultQueue Queue to receive blob decoding results
     */
    public PbfReader(File file, ExecutorService executor, BlockingQueue<Future<ReaderBlock>> resultQueue) {
        this.file = file;
        this.executorService = executor;
        this.resultQueue = resultQueue;
    }

    @Override
    public void run() {
        try (FileInputStream fin = new FileInputStream(file); BufferedInputStream bin = new BufferedInputStream(fin, 50000);
                        DataInputStream din = new DataInputStream(bin)) {
            
            validatePbfHeader(din);
            
            PbfStreamSplitter streamSplitter = new PbfStreamSplitter(din);
            while (parsing.get() && streamSplitter.hasNext()) {
                PbfBlob pbfBlob = streamSplitter.next();
                Future<ReaderBlock> blockResult = executorService.submit(new PbfBlobDecoder(pbfBlob));
                resultQueue.put(blockResult);
            }
            // signal EOF
            Future<ReaderBlock> eofResult = executorService.submit(() -> {}, new ReaderBlock(null));
            resultQueue.put(eofResult);
        } catch (Exception e) {
            try {
                Future<ReaderBlock> errorResult = executorService.submit(() -> { throw e; });
                resultQueue.put(errorResult);
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    public void stop() {
        parsing.set(false);
    }
    
    private void validatePbfHeader(DataInputStream in) throws IOException {
        in.mark(10);
        // check file header
        byte[] header = new byte[6];
        if (in.read(header) < header.length) {
            throw new IOException("Unable to read from input file: " + file.getPath());
        }
        in.reset();
        
        if (header[0] == 0 && header[1] == 0 && header[2] == 0 && header[4] == 10 && header[5] == 9
                        && (header[3] == 13 || header[3] == 14)) {
            return;
        }
        throw new IOException("Not a valid pbf file: " + file.getPath());
    }
}
