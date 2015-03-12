package com.graphhopper.wrapper;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class ErrorBean {

    /**
     * Error additiona info
     *
     * E.g. to see the underlying exception, if any
     */
    private String details;

    /**
     * Error message
     *
     * Not intended to be displayed to the user as it is currently not translated
     */
    private String message;

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
