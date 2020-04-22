package com.graphhopper.coll;

import com.graphhopper.storage.SPTEntry;

import java.util.LinkedList;
import java.util.Queue;

/**
 * MIT license https://github.com/juraj67/Pairing-Heap/blob/master/LICENSE
 */
public class PairingHeap {

    private PairingHeapItem root;

    public PairingHeap() {
    }

    public PairingHeap(PairingHeapItem root) {
        this.root = root;
    }

    public PairingHeapItem getRoot() {
        return this.root;
    }

    public SPTEntry peek() {
        return this.root.getData();
    }

    public PairingHeapItem add(SPTEntry entry) {
        PairingHeapItem to_insert = new PairingHeapItem(entry);
        if (this.root == null) {
            this.root = to_insert;
            return this.root;
        } else {
            this.root = pair(this, new PairingHeap(to_insert)).getRoot();
            return to_insert;
        }
    }

    public SPTEntry poll() {
        return (this.root == null) ? null : this.popPairingHeapItem().getData();
    }

    public boolean isEmpty() {
        return this.root == null;
    }

    public void update(SPTEntry entry, double old) {
        if (root == null)
            throw new IllegalStateException("Cannot update " + entry);

        PairingHeapItem item = root;
        while (true) {
            if (item.getData() == entry) {
                item.setData(entry);
                if (item == root || item.getData().weight >= item.getAncestor().getData().weight)
                    return;

                // heap property is violated:
                // TODO NOW update tree structure!
                //  1. remove the subtree
                //  2. merge two trees!
                // similar to popPairingHeapItem
                return;
            }
            if (!item.hasRightSon())
                return;
            item = item.getRightSon();
        }
    }

    /**
     * Method removes the root of the pairing heap, finds the new root and returns the heap item
     */
    private PairingHeapItem popPairingHeapItem() {
        if (this.root == null) {                                  //if the heap is empty
            return null;
        } else {
            PairingHeapItem old_root = this.root;
            this.root = null;
            if (!old_root.hasLeftSon()) {                    //if the heap contains only the root
                return old_root;
            } else {                                        //if the heap contains more items
                if (!old_root.getLeftSon().hasRightSon()) {  //if the heap consists of one heap
                    old_root.getLeftSon().setAncestor(null);
                    this.root = old_root.getLeftSon();
                    return old_root;
                } else {                                    //if the heap consists of multiple heaps
                    PairingHeapItem help_item = old_root.getLeftSon();
                    help_item.setAncestor(null);
                    this.root = multiHeapsMerge(help_item).getRoot(); //returns the heap and sets root
                    return old_root;
                }
            }
        }
    }

    /**
     * Multi-pass pairing heap
     * Method pairs all heaps that remain after the root has been removed
     */
    private PairingHeap multiHeapsMerge(PairingHeapItem paLeftSonOfRoot) {
        Queue<PairingHeap> fifo = new LinkedList<>();

        //fills the queue with all heaps
        PairingHeapItem help_item;
        do {
            help_item = paLeftSonOfRoot.getRightSon();
            paLeftSonOfRoot.removeRightSon();
            paLeftSonOfRoot.setAncestor(null);
            if (paLeftSonOfRoot.hasLeftSon()) {
                paLeftSonOfRoot.getLeftSon().setAncestor(paLeftSonOfRoot);
            }
            fifo.add(new PairingHeap(paLeftSonOfRoot));
            paLeftSonOfRoot = help_item;
        } while (help_item != null);

        //pairs two heaps and put them in a queue until only one heap is in queue
        while (fifo.size() != 1) {
            fifo.add(pair(fifo.remove(), fifo.remove()));
        }
        //returns last pairing heap that remains in the queue
        return fifo.remove();
    }

    /**
     * Method pairs two pairing heaps
     */
    private PairingHeap pair(PairingHeap paHeap1, PairingHeap paHeap2) {
        PairingHeapItem h1Root = paHeap1.getRoot();
        if (h1Root == null)
            return paHeap2;

        PairingHeapItem h2Root = paHeap2.getRoot();
        if (h2Root == null)
            return paHeap1;

        if (h1Root.getData().weight <= h2Root.getData().weight) {
            // if 2 has a worse priority then 1
            if (h1Root.hasLeftSon()) {
                PairingHeapItem helpItem = h1Root.getLeftSon();
                h1Root.setLeftSon(h2Root);
                h2Root.setAncestor(h1Root);
                h2Root.setRightSon(helpItem);
                helpItem.setAncestor(h2Root);
                return paHeap1;
            } else {
                h1Root.setLeftSon(h2Root);
                h2Root.setAncestor(h1Root);
                return paHeap1;
            }
        } else {
            // if 2 has a better priority then 1
            if (h2Root.hasLeftSon()) {
                PairingHeapItem help_item = h2Root.getLeftSon();
                h2Root.setLeftSon(h1Root);
                h1Root.setAncestor(h2Root);
                h1Root.setRightSon(help_item);
                help_item.setAncestor(h1Root);
                return paHeap2;
            } else {
                h2Root.setLeftSon(h1Root);
                h1Root.setAncestor(h2Root);
                return paHeap2;
            }
        }
    }
}
