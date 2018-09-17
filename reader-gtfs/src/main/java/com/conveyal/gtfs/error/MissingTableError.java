package com.conveyal.gtfs.error;

import java.io.Serializable;

/** Indicates that a table marked as required is not present in a GTFS feed. */
public class MissingTableError extends GTFSError implements Serializable {
    public static final long serialVersionUID = 1L;

    public MissingTableError(String file) {
        super(file, 0, null);
    }

    @Override public String getMessage() {
        return String.format("This table is required by the GTFS specification but is missing.");
    }

}
