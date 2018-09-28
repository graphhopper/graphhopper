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
