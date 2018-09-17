package com.conveyal.gtfs.error;

import java.io.Serializable;

/** Represents a problem parsing an integer field of GTFS feed. */
public class NumberParseError extends GTFSError implements Serializable {
    public static final long serialVersionUID = 1L;

    public NumberParseError(String file, long line, String field) {
        super(file, line, field);
    }

    @Override public String getMessage() {
        return String.format("Error parsing a number from a string.");
    }

}
