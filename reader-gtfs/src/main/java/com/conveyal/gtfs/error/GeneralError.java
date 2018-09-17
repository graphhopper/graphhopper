package com.conveyal.gtfs.error;

import java.io.Serializable;

/** Represents any GTFS loading problem that does not have its own class, with a free-text message. */
public class GeneralError extends GTFSError implements Serializable {
    public static final long serialVersionUID = 1L;

    private String message;

    public GeneralError(String file, long line, String field, String message) {
        super(file, line, field);
    }

    @Override public String getMessage() {
        return message;
    }

}
