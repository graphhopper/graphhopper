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
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * A simple two staged heap where the first N values are stored in a sorted tree.
 * <p/>
 * TODO some bug remaining if it over or underflows (or same problem as with MyDijkstraHeap?) ...
 * use dijkstra simple => cannot remove 8243.565 size:32, smallSize: 22 ...
 * <p/>
 * @author Peter Karich
 */
public class GHDijkstraHeap2 implements BinHeapWrapper<Number, Integer>
{
    private static final double noKey = Double.MAX_VALUE;
    private TreeMap<Double, Object> sorted;
    private double splitter = noKey;
    private IntDoubleBinHeap largeHeap;
    private int smallCapacity;
    private int size;
    private int underflows = 0;
    private int overflows = 0;

    public GHDijkstraHeap2()
    {
        this(102);
    }

    public GHDijkstraHeap2( int cap )
    {
        this(cap < 100 ? 25 : cap / 4, cap < 100 ? 100 : cap);
    }

    public GHDijkstraHeap2( int smallCap, int largeCap )
    {
        smallCapacity = smallCap;
        sorted = new TreeMap<Double, Object>();
        largeHeap = new IntDoubleBinHeap(largeCap);
    }

    @Override
    public void update( Number key, Integer element )
    {
        if (!largeHeap.update_(key.doubleValue(), element))
        {
            // remove
            Iterator<Entry<Double, Object>> iter = sorted.entrySet().iterator();
            boolean removed = false;
            while (iter.hasNext())
            {
                Entry<Double, Object> e = iter.next();
                if (e.getValue() instanceof MyList)
                {
                    MyList list = (MyList) e.getValue();
                    if (list.contains(element))
                    {
                        list.remove((Integer) element);
                        if (list.isEmpty())
                        {
                            iter.remove();
                        }
                        removed = true;
                        break;
                    }
                } else if (((Integer) e.getValue()).equals(element))
                {
                    iter.remove();
                    removed = true;
                }
            }
            if (!removed)
            {
                throw new IllegalStateException("couldn't remove " + element + " with new key " + key);
            }

            // update
            putSorted(key.doubleValue(), element);
        }
        // TODO throw new RuntimeException("update is problematic -> see todo in MyDijkstraHeapTest!");
    }

    public void update_( double oldKey, double key, int element )
    {
        if (oldKey >= splitter)
        {
            largeHeap.update_(key, element);
        } else
        {
            if (handleSmallOverflow())
            {
                insert_(key, element);
                return;
            }

            removeSorted(oldKey, element);
            putSorted(key, element);
        }
        // TODO throw new RuntimeException("update is problematic -> see todo test!");
    }

    @Override
    public void insert( Number key, Integer element )
    {
        insert_(key.doubleValue(), element);
    }

    public void insert_( double key, int element )
    {
        if (key >= splitter)
        {
            largeHeap.insert_(key, element);
        } else
        {
            if (handleSmallOverflow())
            {
                insert_(key, element);
                return;
            }

            putSorted(key, element);
        }
        size++;
    }

    @Override
    public boolean isEmpty()
    {
        return size == 0;
    }

    @Override
    public int getSize()
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
        Object o = sorted.firstEntry().getValue();
        if (o instanceof MyList)
        {
            return ((MyList) o).get(0);
        }

        return (Integer) o;
    }

    @Override
    public Number peekKey()
    {
        return peek_key();
    }

    public double peek_key()
    {
        handleSmallUnderflow();
        return sorted.firstKey();
    }

    @Override
    public Integer pollElement()
    {
        return poll_element();
    }

    public int poll_element()
    {
        handleSmallUnderflow();
        Object o = sorted.firstEntry().getValue();
        int el;
        if (o instanceof MyList)
        {
            MyList list = (MyList) o;
            el = list.remove(list.size() - 1);
            if (list.isEmpty())
            {
                sorted.pollFirstEntry();
            }
        } else
        {
            el = (Integer) sorted.pollFirstEntry().getValue();
        }

        size--;
        return el;
    }

    @Override
    public void clear()
    {
        sorted.clear();
        largeHeap.clear();
        size = 0;
    }

    @Override
    public void ensureCapacity( int size )
    {
        size = size - smallCapacity;
        if (size <= 0)
        {
            return;
        }
        largeHeap.ensureCapacity(size);
    }

    void removeSorted( double key, int value )
    {
        Object old = sorted.remove(key);
        if (old == null)
        {
            throw new IllegalStateException("cannot remove " + key + " " + getStatsInfo());
        }

        if (old instanceof Integer)
        {
            if (!old.equals(value))
            {
                throw new IllegalStateException("cannot remove " + key + " " + getStatsInfo());
            }
            return;
        }
        MyList list = (MyList) old;
        if (!list.remove((Integer) value))
        {
            throw new IllegalStateException("cannot remove " + key + " " + getStatsInfo());
        }
        if (!list.isEmpty())
        {
            sorted.put(key, list);
        }
    }

    void putSorted( double key, int el )
    {
        Object old = sorted.put(key, el);
        if (old == null)
        {
            return;
        }
        MyList list;
        if (old instanceof MyList)
        {
            list = (MyList) old;
        } else
        {
            list = new MyList(5);
            list.add((Integer) old);
        }
        sorted.put(key, list);
        list.add(el);
    }

    private boolean handleSmallUnderflow()
    {
        if (!sorted.isEmpty())
        {
            return false;
        }

        for (int i = 0; !largeHeap.isEmpty() && i < smallCapacity; i++)
        {
            double key = largeHeap.peek_key();
            int el = largeHeap.poll_element();
            putSorted(key, el);
        }
        if (largeHeap.isEmpty())
        {
            splitter = noKey;
        } else
        {
            splitter = largeHeap.peek_key();
        }
        if (sorted.isEmpty())
        {
            throw new IllegalStateException("sorted tree wasn't fill with data? " + getStatsInfo());
        }
        underflows++;
        return true;
    }

    private boolean handleSmallOverflow()
    {
        if (sorted.size() < smallCapacity)
        {
            return false;
        }

        // TODO only approximated as there could be duplicates!!
        int mid = sorted.size() / 2;
        int counter = 0;
        TreeMap<Double, Object> newSorted = new TreeMap<Double, Object>();
        for (Entry<Double, Object> e : sorted.entrySet())
        {
            if (counter < mid)
            {
                newSorted.put(e.getKey(), e.getValue());
            } else
            {
                if (e.getValue() instanceof MyList)
                {
                    for (Integer i : (MyList) e.getValue())
                    {
                        largeHeap.insert(e.getKey(), i);
                    }
                } else
                {
                    largeHeap.insert(e.getKey(), (Integer) e.getValue());
                }
            }
            counter++;
        }
        sorted = newSorted;
        if (largeHeap.isEmpty())
        {
            throw new IllegalStateException("largeHeap wasn't filled with data from small heap!? " + getStatsInfo());
        }
        splitter = largeHeap.peek_key();
        overflows++;
        return true;
    }

    public String getStatsInfo()
    {
        return "size:" + getSize()
                + ", smallSize: " + sorted.size() + " " + sorted
                + ", split:" + splitter
                + ", largeSize: " + largeHeap.getSize() + "(" + largeHeap.getCapacity() + ")"
                + ", overflows:" + overflows + ", underflows:" + underflows;
    }

    // in case of duplicates the value gets a list!
    private static class MyList extends ArrayList<Integer>
    {
        public MyList( int initialCapacity )
        {
            super(initialCapacity);
        }
    }
    private static ReverseComparator comparator = new ReverseComparator();

    private static class ReverseComparator implements Comparator<Double>
    {
        @Override
        public int compare( Double o1, Double o2 )
        {
            return -o1.compareTo(o2);
        }
    }
}
