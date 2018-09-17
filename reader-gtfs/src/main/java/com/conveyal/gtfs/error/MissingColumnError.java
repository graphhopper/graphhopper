package com.conveyal.gtfs.error;

import java.io.Serializable;

/** Indicates that a column marked as required is entirely missing from a GTFS feed. */
public class MissingColumnError extends GTFSError implements Serializable {
    public static final long serialVersionUID = 1L;

    public MissingColumnError(String file, String field) {
        super(file, 1, field);
    }

    @Override public String getMessage() {
        return String.format("Missing required column.");
    }

}
