package com.graphhopper;

import java.util.Collections;
import java.util.List;

public class MultiException extends RuntimeException {

    private final List<Throwable> errors;

    public MultiException(List<Throwable> errors) {
        this.errors = errors;
    }

    public MultiException(Throwable e) {
        this(Collections.singletonList(e));
    }

    public List<Throwable> getErrors() {
        return errors;
    }

}
