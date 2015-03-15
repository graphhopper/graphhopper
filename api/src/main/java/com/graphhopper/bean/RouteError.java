package com.graphhopper.bean;


import com.fasterxml.jackson.annotation.JsonInclude;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlAccessorType(value = XmlAccessType.FIELD)
// not the real "Throwable" inside, but who cares? Abstraction is the key :)
public class RouteError {

    private String text;

    private String details;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
