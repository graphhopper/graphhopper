// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import org.openstreetmap.osmosis.osmbinary.Fileformat;
import org.openstreetmap.osmosis.osmbinary.Fileformat.Blob;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses a PBF data stream and extracts the raw data of each blob in sequence until the end of the
 * stream is reached.
 * <p>
 *
 * @author Brett Henderson
 */
public class PbfStreamSplitter implements Iterator<PbfBlob> {
    private static Logger log = Logger.getLogger(PbfStreamSplitter.class.getName());
    private DataInputStream dis;
    private int dataBlockCount;
    private PbfBlob nextBlob;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param pbfStream The PBF data stream to be parsed.
     */
    public PbfStreamSplitter(DataInputStream pbfStream) {
        dis = pbfStream;
        dataBlockCount = 0;
        nextBlob = getNextBlob();
    }

    private Fileformat.BlobHeader readHeader(int headerLength) throws IOException {
        byte[] headerBuffer = new byte[headerLength];
        dis.readFully(headerBuffer);
        return Fileformat.BlobHeader.parseFrom(headerBuffer);
    }

    private PbfBlob readBlob(Fileformat.BlobHeader blobHeader) throws IOException {
        byte[] rawBlob = new byte[blobHeader.getDatasize()];
        dis.readFully(rawBlob);
        Blob blob = Fileformat.Blob.parseFrom(rawBlob);
        return new PbfBlob(blobHeader.getType(), blob);
    }

    private PbfBlob getNextBlob() {
        try {
            // Read the length of the next header block. This is the only time
            // we should expect to encounter an EOF exception. In all other
            // cases it indicates a corrupt or truncated file.
            int headerLength;
            try {
                headerLength = dis.readInt();
            } catch (EOFException e) {
                return null;
            }

            if (log.isLoggable(Level.FINER)) {
                log.finer("Reading header for blob " + dataBlockCount++);
            }
            Fileformat.BlobHeader blobHeader = readHeader(headerLength);

            if (log.isLoggable(Level.FINER)) {
                log.finer("Processing blob of type " + blobHeader.getType() + ".");
            }
            return readBlob(blobHeader);

        } catch (IOException e) {
            throw new RuntimeException("Unable to get next blob from PBF stream.", e);
        }
    }

    @Override
    public boolean hasNext() {
        return nextBlob != null;
    }

    @Override
    public PbfBlob next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        PbfBlob result = nextBlob;
        nextBlob = getNextBlob();

        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
