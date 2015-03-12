package com.graphhopper.wrapper;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class InstructionBean {

    /**
     * The distance for this instruction, in meter
     */
    private double distance;

    /**
     * An array containing the first and the last index (relative to paths[0].points) of the points for this instruction.
     * This is useful to know for which part of the route the instructions are valid.
     */
    private List<Integer> interval;

    /**
     * A number which specifies the sign to show e.g. for right turn etc
     * TURN_SHARP_LEFT = -3
     * TURN_LEFT = -2
     * TURN_SLIGHT_LEFT = -1
     * CONTINUE_ON_STREET = 0
     * TURN_SLIGHT_RIGHT = 1
     * TURN_RIGHT = 2
     * TURN_SHARP_RIGHT = 3
     * FINISH = 4
     * VIA_REACHED = 5
     * USE_ROUNDABOUT = 6
     */
    private int sign;

    /**
     * A description what the user has to do in order to follow the route. The language depends on the locale parameter.
     */
    private String text;

    /**
     * [optional] A text describing the instruction in more detail, e.g. like surface of the way, warnings or involved costs
     */
    @XmlElement(name="annotation_text")
    private String annotationText;

    /**
     * [optional] 0 stands for INFO, 1 for warning, 2 for costs, 3 for costs and warning
     */
    @XmlElement(name="annotation_importance")
    private Integer annotationImportance;

    /**
     * The duration for this instruction, in ms
     */
    private long time;

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public List<Integer> getInterval() {
        return interval;
    }

    public void setInterval(List<Integer> interval) {
        this.interval = interval;
    }

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAnnotationText() {
        return annotationText;
    }

    public void setAnnotationText(String annotationText) {
        this.annotationText = annotationText;
    }

    public Integer getAnnotationImportance() {
        return annotationImportance;
    }

    public void setAnnotationImportance(Integer annotationImportance) {
        this.annotationImportance = annotationImportance;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}

