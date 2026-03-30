package pl.cezarysanecki.solver.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinHeapTest {

    @Test
    void shouldBeEmptyWhenCreated() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
    }

    @Test
    void shouldInsertAndExtractSingleElement() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        heap.insert(42);
        assertFalse(heap.isEmpty());
        assertEquals(1, heap.size());
        assertEquals(42, heap.peekMin());
        assertEquals(42, heap.extractMin());
        assertTrue(heap.isEmpty());
    }

    @Test
    void shouldExtractInSortedOrder() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        heap.insert(5);
        heap.insert(3);
        heap.insert(8);
        heap.insert(1);
        heap.insert(4);

        List<Integer> extracted = new ArrayList<>();
        while (!heap.isEmpty()) {
            extracted.add(heap.extractMin());
        }
        assertEquals(List.of(1, 3, 4, 5, 8), extracted);
    }

    @Test
    void shouldSupportContains() {
        var heap = new MinHeap<String>(Comparator.naturalOrder());
        heap.insert("alpha");
        heap.insert("beta");
        assertTrue(heap.contains("alpha"));
        assertTrue(heap.contains("beta"));
        assertFalse(heap.contains("gamma"));
    }

    @Test
    void shouldRemoveFromContainsAfterExtract() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        heap.insert(10);
        heap.insert(20);
        assertTrue(heap.contains(10));
        heap.extractMin(); // removes 10
        assertFalse(heap.contains(10));
        assertTrue(heap.contains(20));
    }

    @Test
    void shouldDecreaseKeyAndReorder() {
        // Mutable wrapper — we need to change the priority
        record Entry(String name, int[] priority) {}

        var heap = new MinHeap<Entry>(Comparator.comparingInt(e -> e.priority()[0]));

        var a = new Entry("A", new int[]{10});
        var b = new Entry("B", new int[]{5});
        var c = new Entry("C", new int[]{8});

        heap.insert(a);
        heap.insert(b);
        heap.insert(c);

        assertEquals(b, heap.peekMin()); // B(5) is min

        // decrease A from 10 to 1
        a.priority()[0] = 1;
        heap.decreaseKey(a);

        assertEquals(a, heap.peekMin()); // A(1) is now min
        assertEquals(a, heap.extractMin());
        assertEquals(b, heap.extractMin());
        assertEquals(c, heap.extractMin());
    }

    @Test
    void shouldThrowOnExtractFromEmptyHeap() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        assertThrows(NoSuchElementException.class, heap::extractMin);
    }

    @Test
    void shouldThrowOnPeekFromEmptyHeap() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        assertThrows(NoSuchElementException.class, heap::peekMin);
    }

    @Test
    void shouldThrowOnDuplicateInsert() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        heap.insert(1);
        assertThrows(IllegalStateException.class, () -> heap.insert(1));
    }

    @Test
    void shouldThrowOnDecreaseKeyForMissingElement() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        assertThrows(IllegalStateException.class, () -> heap.decreaseKey(99));
    }

    @Test
    void shouldHandleLargerHeapCorrectly() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        // insert 100 elements in reverse order
        for (int i = 100; i >= 1; i--) {
            heap.insert(i);
        }
        assertEquals(100, heap.size());
        for (int i = 1; i <= 100; i++) {
            assertEquals(i, heap.extractMin());
        }
        assertTrue(heap.isEmpty());
    }

    @Test
    void shouldAllowReinsertAfterExtract() {
        var heap = new MinHeap<Integer>(Comparator.naturalOrder());
        heap.insert(5);
        heap.extractMin();
        // should be able to insert again after extraction
        heap.insert(5);
        assertEquals(5, heap.extractMin());
    }
}
