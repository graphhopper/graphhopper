package com.conveyal.gtfs.error;

import java.io.Serializable;

/** Represents a problem parsing a date field from a GTFS feed. */
public class DateParseError extends GTFSError implements Serializable {
    public static final long serialVersionUID = 1L;

    public DateParseError(String file, long line, String field) {
        super(file, line, field);
    }

    @Override public String getMessage() {
        return "Could not parse date (format should be YYYYMMDD).";
    }

}
