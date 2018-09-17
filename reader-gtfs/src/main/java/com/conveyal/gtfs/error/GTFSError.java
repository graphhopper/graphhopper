package com.conveyal.gtfs.error;

import java.io.Serializable;

/**
 * Represents an error encountered
 */
public abstract class GTFSError implements Comparable<GTFSError>, Serializable {

    public final String file; // TODO GTFSTable enum? Or simply use class objects.
    public final long   line;
    public final String field;
    public final String affectedEntityId;
    public final String errorType;

    public GTFSError(String file, long line, String field) {
        this(file, line, field, null);
    }

    public GTFSError(String file, long line, String field, String affectedEntityId) {
        this.file  = file;
        this.line  = line;
        this.field = field;
        this.affectedEntityId = affectedEntityId;
        this.errorType = this.getClass().getSimpleName();
    }

    public String getMessage() {
        return "no message";
    }

    public String getMessageWithContext() {
        StringBuilder sb = new StringBuilder();
        sb.append(file);
        sb.append(' ');
        if (line >= 0) {
            sb.append("line ");
            sb.append(line);
        } else {
            sb.append("(no line)");
        }
        if (field != null) {
            sb.append(", field '");
            sb.append(field);
            sb.append('\'');
        }
        sb.append(": ");
        sb.append(getMessage());
        return sb.toString();
    }

    /** must be comparable to put into mapdb */
    public int compareTo (GTFSError o) {
        if (this.file == null && o.file != null) return -1;
        else if (this.file != null && o.file == null) return 1;

        int file = this.file == null && o.file == null ? 0 : String.CASE_INSENSITIVE_ORDER.compare(this.file, o.file);
        if (file != 0) return file;
        int errorType = String.CASE_INSENSITIVE_ORDER.compare(this.errorType, o.errorType);
        if (errorType != 0) return errorType;
        int affectedEntityId = this.affectedEntityId == null && o.affectedEntityId == null ? 0 : String.CASE_INSENSITIVE_ORDER.compare(this.affectedEntityId, o.affectedEntityId);
        if (affectedEntityId != 0) return affectedEntityId;
        else return Long.compare(this.line, o.line);
    }

    @Override
    public String toString() {
        return "GTFSError: " + getMessageWithContext();
    }

}
