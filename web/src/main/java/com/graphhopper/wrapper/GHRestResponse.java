package com.graphhopper.wrapper;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Bean astraction for GHResponse
 *
 * @see com.graphhopper.http.WebHelper#wrapResponse(com.graphhopper.GHResponse, boolean, boolean)
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "response")
public class GHRestResponse {

    private InfoBean info;

    private PathsBean paths;

    public InfoBean getInfo() {
        return info;
    }

    public void setInfo(InfoBean info) {
        this.info = info;
    }

    public PathsBean getPaths() {
        return paths;
    }

    public void setPaths(PathsBean paths) {
        this.paths = paths;
    }
}
