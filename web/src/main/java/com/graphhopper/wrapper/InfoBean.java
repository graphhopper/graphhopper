package com.graphhopper.wrapper;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class InfoBean {

    /**
     * How many ms the request took on the server, of course without network latency taken into account.
     * Note: info.took is established from the request, therefore here is always set to -1
     */
    private Integer took = -1;

    /**
     * A list of error messages
     *
     * Sometimes a point can be "off the road" and you'll get 'cannot find point', this normally does not
     * indicate a bug in the routing engine and is expected to a certain degree if too far away.
     */
    @XmlElement(name = "error")
    @XmlElementWrapper(name = "errors")
    private List<ErrorBean> errors;

    public int getTook() {
        return took;
    }

    public void setTook(int took) {
        this.took = took;
    }

    public List<ErrorBean> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorBean> errors) {
        this.errors = errors;
    }
}
