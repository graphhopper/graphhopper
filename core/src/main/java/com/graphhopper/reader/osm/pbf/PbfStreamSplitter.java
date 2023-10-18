// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import org.openstreetmap.osmosis.osmbinary.Fileformat;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses a PBF data stream and extracts the raw data of each blob in sequence until the end of the
 * stream is reached.
 * <p>
 *
 * @author Brett Henderson
 */
public class PbfStreamSplitter implements Iterator<PbfRawBlob> {
    private static Logger log = Logger.getLogger(PbfStreamSplitter.class.getName());
    private DataInputStream dis;
    private int dataBlockCount;
    private boolean eof;
    private PbfRawBlob nextBlob;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param pbfStream The PBF data stream to be parsed.
     */
    public PbfStreamSplitter(DataInputStream pbfStream) {
        dis = pbfStream;
        dataBlockCount = 0;
        eof = false;
    }

    private Fileformat.BlobHeader readHeader(int headerLength) throws IOException {
        byte[] headerBuffer = new byte[headerLength];
        dis.readFully(headerBuffer);

        Fileformat.BlobHeader blobHeader = Fileformat.BlobHeader.parseFrom(headerBuffer);

        return blobHeader;
    }

    private byte[] readRawBlob(Fileformat.BlobHeader blobHeader) throws IOException {
        byte[] rawBlob = new byte[blobHeader.getDatasize()];

        dis.readFully(rawBlob);

        return rawBlob;
    }

    private void getNextBlob() {
        try {
            // Read the length of the next header block. This is the only time
            // we should expect to encounter an EOF exception. In all other
            // cases it indicates a corrupt or truncated file.
            int headerLength;
            try {
                headerLength = dis.readInt();
            } catch (EOFException e) {
                eof = true;
                return;
            }

            if (log.isLoggable(Level.FINER)) {
                log.finer("Reading header for blob " + dataBlockCount++);
            }
            Fileformat.BlobHeader blobHeader = readHeader(headerLength);

            if (log.isLoggable(Level.FINER)) {
                log.finer("Processing blob of type " + blobHeader.getType() + ".");
            }
            byte[] blobData = readRawBlob(blobHeader);

            nextBlob = new PbfRawBlob(blobHeader.getType(), blobData);

        } catch (IOException e) {
            throw new RuntimeException("Unable to get next blob from PBF stream.", e);
        }
    }

    @Override
    public boolean hasNext() {
        if (nextBlob == null && !eof) {
            getNextBlob();
        }

        return nextBlob != null;
    }

    @Override
    public PbfRawBlob next() {
        PbfRawBlob result = nextBlob;
        nextBlob = null;

        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public void release() {
        if (dis != null) {
            try {
                dis.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        dis = null;
    }
}
