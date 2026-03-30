package pl.cezarysanecki.solver.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Min-heap with O(log n) decreaseKey operation.
 * A key data structure for Dijkstra and A*.
 * <p>
 * Unlike {@code java.util.PriorityQueue}:
 * <ul>
 *   <li>decreaseKey in O(log n) instead of O(n) (remove + add)</li>
 *   <li>contains in O(1)</li>
 * </ul>
 * <p>
 * Implementation: binary heap in ArrayList + HashMap(element → index)
 * for O(1) lookup. Inspired by {@code MinHeapWithUpdate} from GraphHopper,
 * but fully generic.
 *
 * @param <T> element type
 */
public class MinHeap<T> {

    private final Comparator<T> comparator;
    private final ArrayList<T> tree;        // 1-indexed; tree[0] unused
    private final Map<T, Integer> positions; // element → index in tree

    public MinHeap(Comparator<T> comparator) {
        this.comparator = comparator;
        this.tree = new ArrayList<>();
        this.tree.add(null); // placeholder for 1-indexed
        this.positions = new HashMap<>();
    }

    /** Inserts an element. O(log n). Throws IllegalStateException if the element is already in the heap. */
    public void insert(T element) {
        if (positions.containsKey(element))
            throw new IllegalStateException("Element already in heap: " + element);
        tree.add(element);
        int index = size();
        positions.put(element, index);
        percolateUp(index);
    }

    /** Returns and removes the minimum element. O(log n). */
    public T extractMin() {
        if (isEmpty())
            throw new NoSuchElementException("Heap is empty");
        T min = tree.get(1);
        int last = size();
        if (last == 1) {
            tree.removeLast();
            positions.remove(min);
            return min;
        }
        // move last element to root
        T lastElement = tree.get(last);
        tree.set(1, lastElement);
        positions.put(lastElement, 1);
        tree.removeLast();
        positions.remove(min);
        percolateDown(1);
        return min;
    }

    /**
     * Notifies the heap that the element's priority has decreased. O(log n).
     * The element must be in the heap — otherwise {@code IllegalStateException}.
     */
    public void decreaseKey(T element) {
        Integer index = positions.get(element);
        if (index == null)
            throw new IllegalStateException("Element not in heap: " + element);
        percolateUp(index);
    }

    /** Returns the minimum element without removing it. O(1). */
    public T peekMin() {
        if (isEmpty())
            throw new NoSuchElementException("Heap is empty");
        return tree.get(1);
    }

    /** Does the heap contain the element? O(1). */
    public boolean contains(T element) {
        return positions.containsKey(element);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        return tree.size() - 1; // minus placeholder at index 0
    }

    // --- percolation ---

    private void percolateUp(int index) {
        T element = tree.get(index);
        while (index > 1) {
            int parentIndex = index >> 1;
            T parent = tree.get(parentIndex);
            if (comparator.compare(element, parent) >= 0)
                break;
            // move parent down
            tree.set(index, parent);
            positions.put(parent, index);
            index = parentIndex;
        }
        tree.set(index, element);
        positions.put(element, index);
    }

    private void percolateDown(int index) {
        int sz = size();
        if (sz == 0) return;
        T element = tree.get(index);
        while ((index << 1) <= sz) {
            int child = index << 1;
            // pick the smaller child
            if (child < sz && comparator.compare(tree.get(child + 1), tree.get(child)) < 0)
                child++;
            if (comparator.compare(tree.get(child), element) >= 0)
                break;
            // move child up
            T childElement = tree.get(child);
            tree.set(index, childElement);
            positions.put(childElement, index);
            index = child;
        }
        tree.set(index, element);
        positions.put(element, index);
    }
}
