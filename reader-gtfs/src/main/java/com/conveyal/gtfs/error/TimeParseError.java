package com.conveyal.gtfs.error;

import java.io.Serializable;

/** Represents a problem parsing a time of day field of GTFS feed. */
public class TimeParseError extends GTFSError implements Serializable {
    public static final long serialVersionUID = 1L;

    public TimeParseError(String file, long line, String field) {
        super(file, line, field);
    }

    @Override public String getMessage() {
        return "Could not parse time (format should be HH:MM:SS).";
    }

}
