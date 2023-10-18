/*
 * Copyright (c) 2015, Conveyal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.conveyal.gtfs.error;

import java.io.Serializable;
import java.util.Locale;

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
        return String.format("Invalid reference %s", badReference);
    }

}
