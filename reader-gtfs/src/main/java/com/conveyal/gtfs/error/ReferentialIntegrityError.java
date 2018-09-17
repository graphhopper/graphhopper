package com.conveyal.gtfs.error;

import java.io.Serializable;

/** Indicates that an entity referenced another entity that does not exist. */
public class ReferentialIntegrityError extends GTFSError implements Serializable {
    public static final long serialVersionUID = 1L;

    // TODO: maybe also store the entity ID of the entity which contained the bad reference, in addition to the row number
    public final String badReference;

    public ReferentialIntegrityError(String tableName, long row, String field, String badReference) {
        super(tableName, row, field);
        this.badReference = badReference;
    }

    /** must be comparable to put into mapdb */
    @Override
    public int compareTo (GTFSError o) {
        int compare = super.compareTo(o);
        if (compare != 0) return compare;
        return this.badReference.compareTo((((ReferentialIntegrityError) o).badReference));
    }

    @Override public String getMessage() {
        return String.format(badReference);
    }

}
