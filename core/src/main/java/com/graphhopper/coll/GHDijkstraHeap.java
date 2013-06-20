/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.coll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * TODO do not use if you need the update method which is broken!
 * <p/>
 * Several papers regarding queues are interesting:
 * <p/>
 * "Priority Queues and Dijkstra’s Algorithm" http://www.cs.utexas.edu/~shaikat/papers/TR-07-54.pdf
 * -> 2007, Chen et al, Auxiliary Buffer Heap, also the idea of a dijkstra with no decrement key is
 * shown
 * <p/>
 * "Fast Priority Queues for Cached Memory" http://algo2.iti.kit.edu/sanders/papers/falenex.ps.gz ->
 * 1998, Peter Sanders, sequence heap and tuned other heaps
 * <p/>
 * "Revisiting priority queues for image analysis"
 * http://www.cb.uu.se/~cris/Documents/j.patcog.2010.04.002_preprint.pdf -> 2010, Hendriks, ladder
 * queue
 * <p/>
 * @author Peter Karich
 */
public class GHDijkstraHeap implements BinHeapWrapper<Number, Integer>
{
    private IntDoubleBinHeap smallHeap;
    private IntDoubleBinHeap midHeap;
    private IntDoubleBinHeap largeHeap;
    private double noKey = Double.MAX_VALUE;
    private double midMin = noKey, largeMin = noKey;
    private int smallCapacity, midCapacity;
    private int size;
    private int underflows = 0;
    private int overflows = 0;

    public GHDijkstraHeap()
    {
        this(16, 2048, 2048);
    }

    public GHDijkstraHeap( int cap )
    {
        this(16, 16, cap < 100 ? 100 : cap);
    }

    public GHDijkstraHeap( int smallCap, int midCap, int largeCap )
    {
        smallCapacity = smallCap;
        midCapacity = midCap;
        smallHeap = new IntDoubleBinHeap(smallCapacity);
        midHeap = new IntDoubleBinHeap(midCapacity);
        largeHeap = new IntDoubleBinHeap(largeCap);
    }

    @Override
    public void update( Number key, Integer element )
    {
        // SLOW but we do not have old key!
        if (smallHeap.update_(key.intValue(), element))
        {
            return;
        }

        if (midHeap.update_(key.intValue(), element))
        {
            return;
        }

        if (!largeHeap.update_(key.intValue(), element))
        {
            throw new IllegalStateException("cannot update key:" + key + ", element:" + element);
        }

//        throw new RuntimeException("update is problematic -> see todo test!");
    }

    public void update_( double oldKey, double key, int element )
    {
        // TODO problematic -> see todo test!
        if (oldKey >= midMin)
        {
            if (oldKey >= largeMin)
            {
                if (!largeHeap.update_(key, element))
                {
                    throw new IllegalStateException("cannot update large key:" + key + " (" + oldKey + "), element:"
                            + element + ", midMin:" + midMin + ", largeMin:" + largeMin);
                }
            } else
            {
                if (!midHeap.update_(key, element))
                {
                    throw new IllegalStateException("cannot update mid key:" + key + " (" + oldKey + "), element:"
                            + element + ", midMin:" + midMin);
                }
            }
        } else
        {
            if (!smallHeap.update_(key, element))
            {
                throw new IllegalStateException("cannot update small key:" + key + " (" + oldKey + "), element:"
                        + element + ", midMin:" + midMin);
            }
        }

//        throw new RuntimeException("update is problematic -> see todo test!");
    }

    @Override
    public void insert( Number key, Integer element )
    {
        insert_(key.doubleValue(), element);
    }

    public void insert_( double key, int element )
    {
        // 1. find out the correct heap
        if (key >= midMin)
        {
            if (key >= largeMin)
            {
                largeHeap.insert_(key, element);
            } else
            {
                if (handleMidOverflow())
                {
                    insert_(key, element);
                    return;
                }
                midHeap.insert(key, element);
            }
        } else
        {
            if (handleSmallOverflow())
            {
                insert_(key, element);
                return;
            }

            smallHeap.insert_(key, element);
        }

        size++;
    }

    @Override
    public boolean isEmpty()
    {
        return size == 0;
    }

    @Override
    public int size()
    {
        return size;
    }

    @Override
    public Integer peekElement()
    {
        return peek_element();
    }

    public int peek_element()
    {
        handleSmallUnderflow();
        return smallHeap.peek_element();
    }

    @Override
    public Number peekKey()
    {
        return peek_key();
    }

    public double peek_key()
    {
        handleSmallUnderflow();
        return smallHeap.peek_key();
    }

    @Override
    public Integer pollElement()
    {
        return poll_element();
    }

    public int poll_element()
    {
        handleSmallUnderflow();
        int el = smallHeap.poll_element();
        size--;
        return el;
    }

    @Override
    public void clear()
    {
        smallHeap.clear();
        midHeap.clear();
        largeHeap.clear();
        size = 0;
    }

    @Override
    public void ensureCapacity( int size )
    {
        size = size - (smallHeap.getCapacity() + midHeap.getCapacity());
        if (size <= 0)
        {
            return;
        }
        largeHeap.ensureCapacity(size);
    }

    private boolean handleSmallUnderflow()
    {
        if (!smallHeap.isEmpty())
        {
            return false;
        }

        handleMidUnderflow();
        for (int i = 0; !midHeap.isEmpty() && i < smallCapacity; i++)
        {
            double key = midHeap.peek_key();
            int el = midHeap.poll_element();
            smallHeap.insert_(key, el);
        }
        // TODO we need this, but test this!
        handleMidUnderflow();
        if (midHeap.isEmpty())
        {
            midMin = noKey;
        } else
        {
            midMin = midHeap.peek_key();
        }
        underflows++;
        return true;
    }

    private boolean handleSmallOverflow()
    {
        if (smallHeap.size() < smallCapacity)
        {
            return false;
        }

        handleMidOverflow();
        smallHeap = move(smallCapacity, smallHeap, midHeap);
        if (midHeap.isEmpty())
        {
            throw new IllegalStateException("mid heap wasn't filled with data from small heap!?");
        }
        midMin = midHeap.peek_key();
        overflows++;
        return true;
    }

    private boolean handleMidUnderflow()
    {
        if (!midHeap.isEmpty())
        {
            return false;
        }

        for (int i = 0; !largeHeap.isEmpty() && i < midCapacity; i++)
        {
            double key = largeHeap.peek_key();
            int el = largeHeap.poll_element();
            midHeap.insert_(key, el);
        }
        if (midHeap.isEmpty())
        {
            midMin = noKey;
        } else
        {
            midMin = midHeap.peek_key();
        }
        if (largeHeap.isEmpty())
        {
            largeMin = noKey;
        } else
        {
            largeMin = largeHeap.peek_key();
        }
        underflows++;
        return true;
    }

    private boolean handleMidOverflow()
    {
        if (midHeap.size() < midCapacity)
        {
            return false;
        }

        midHeap = move(midCapacity, midHeap, largeHeap);
        if (midHeap.isEmpty())
        {
            throw new IllegalStateException("something went wrong while copying into large heap!?");
        }
        midMin = midHeap.peek_key();
        if (largeHeap.isEmpty())
        {
            throw new IllegalStateException("large heap wasn't filled with data from large heap!?");
        }
        largeMin = largeHeap.peek_key();
        overflows++;
        return true;
    }

    static IntDoubleBinHeap move( int capacity, IntDoubleBinHeap from, IntDoubleBinHeap to )
    {
        // TODO can we improve performance?

        // put the smaller values into the 'newFrom' heap
        IntDoubleBinHeap newFrom = new IntDoubleBinHeap(capacity);
        List<Entry<Double, Integer>> sortedList = new ArrayList<Entry<Double, Integer>>();
        int len = from.size();
        for (int i = 1; i <= len; i++)
        {
            sortedList.add(new MapEntry<Double, Integer>((double) from.getKey(i), from.getElement(i)));
        }

        Collections.sort(sortedList, comparator);
        int mid = sortedList.size() / 2;
        int counter = 0;
        for (Map.Entry<Double, Integer> e : sortedList)
        {
            if (counter < mid)
            {
                newFrom.insert_(e.getKey(), e.getValue());
            } else
            {
                to.insert(e.getKey(), e.getValue());
            }
            counter++;
        }
        return newFrom;
    }

    public String stats()
    {
        return "size:" + size()
                + ", midMin:" + midMin + ", largeMin:" + largeMin
                + ", smallSize: " + smallHeap.size() + "(" + smallHeap.getCapacity() + ")"
                + ", midSize: " + midHeap.size() + "(" + midHeap.getCapacity() + ")"
                + ", largeSize: " + largeHeap.size() + "(" + largeHeap.getCapacity() + ")"
                + ", overflows:" + overflows + ", underflows:" + underflows;
    }

    public String containsValue( int value )
    {
        int index = smallHeap.indexOfValue(value);
        if (index > 0)
        {
            return "sma " + index + " " + smallHeap.getKey(index);
        }

        index = midHeap.indexOfValue(value);
        if (index > 0)
        {
            return "mid " + index + " " + midHeap.getKey(index);
        }

        index = largeHeap.indexOfValue(value);
        if (index > 0)
        {
            return "lar " + index + " " + largeHeap.getKey(index);
        }

        return "null";
    }
    private static EntryComparator comparator = new EntryComparator();

    private static class EntryComparator implements Comparator<Entry<Double, Integer>>
    {
        @Override
        public int compare( Entry<Double, Integer> o1, Entry<Double, Integer> o2 )
        {
            return o1.getKey().compareTo(o2.getKey());
        }
    }
}
