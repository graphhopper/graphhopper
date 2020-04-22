package com.graphhopper.coll;

import com.graphhopper.storage.SPTEntry;

public class PairingHeapItem {

    private PairingHeapItem ancestor;
    private PairingHeapItem leftSon;
    private PairingHeapItem rightSon;
    private SPTEntry data;

    public PairingHeapItem(SPTEntry data) {
        this.data = data;
    }

    /**
     * Method returns the ancestor in binary form
     */
    public PairingHeapItem getAncestor() {
        return ancestor;
    }

    /**
     * Method sets the ancestor of the heap (in binary form)
     */
    public void setAncestor(PairingHeapItem ancestor) {
        this.ancestor = ancestor;
    }

    public PairingHeapItem getLeftSon() {
        return this.leftSon;
    }

    public PairingHeapItem getRightSon() {
        return this.rightSon;
    }

    public SPTEntry getData() {
        return this.data;
    }

    public void setData(SPTEntry data) {
        this.data = data;
    }

    public void setLeftSon(PairingHeapItem leftSon) {
        this.leftSon = leftSon;
    }

    public void setRightSon(PairingHeapItem rightSon) {
        this.rightSon = rightSon;
    }

    public boolean hasLeftSon() {
        return this.leftSon != null;
    }

    public boolean hasRightSon() {
        return this.rightSon != null;
    }

    public void removeLeftSon() {
        this.leftSon = null;
    }

    public void removeRightSon() {
        this.rightSon = null;
    }

    public boolean itIsLeftSon(PairingHeapItem paNode) {
        return this.leftSon == paNode;
    }
}
