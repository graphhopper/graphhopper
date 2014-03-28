/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package com.graphhopper.coll;

import java.util.Arrays;

/**
 * Taken from opentripplanner.
 */
public class IntDoubleBinHeap implements BinHeapWrapper<Number, Integer>
{
    private static final double GROW_FACTOR = 2.0;
    private float[] keys;
    private int[] elem;
    private int size;
    private int capacity;

    public IntDoubleBinHeap()
    {
        this(1000);
    }

    public IntDoubleBinHeap( int capacity )
    {
        if (capacity < 10)
        {
            capacity = 10;
        }
        this.capacity = capacity;
        size = 0;
        elem = new int[capacity + 1];
        // 1-based indexing
        keys = new float[capacity + 1];
        // set sentinel
        keys[0] = Float.NEGATIVE_INFINITY;
    }

    @Override
    public int getSize()
    {
        return size;
    }

    public int size()
    {
        return size;
    }

    @Override
    public boolean isEmpty()
    {
        return size == 0;
    }

    @Override
    public Double peekKey()
    {
        return peek_key();
    }

    public double peek_key()
    {
        if (size > 0)
        {
            return keys[1];
        } else
        {
            throw new IllegalStateException("An empty queue does not have a minimum key.");
        }
    }

    @Override
    public Integer peekElement()
    {
        return peek_element();
    }

    public int peek_element()
    {
        if (size > 0)
        {
            return elem[1];
        } else
        {
            throw new IllegalStateException("An empty queue does not have a minimum value.");
        }
    }

    @Override
    public Integer pollElement()
    {
        return poll_element();
    }

    public int poll_element()
    {
        int i, child;
        int minElem = elem[1];
        int lastElem = elem[size];
        double lastPrio = keys[size];
        if (size <= 0)
        {
            throw new IllegalStateException("An empty queue does not have a minimum value.");
        }
        size -= 1;
        for (i = 1; i * 2 <= size; i = child)
        {
            child = i * 2;
            if (child != size && keys[child + 1] < keys[child])
            {
                child++;
            }
            if (lastPrio > keys[child])
            {
                elem[i] = elem[child];
                keys[i] = keys[child];
            } else
            {
                break;
            }
        }
        elem[i] = lastElem;
        keys[i] = (float) lastPrio;
        return minElem;
    }

    @Override
    public void update( Number key, Integer element )
    {
        update_(key.doubleValue(), element);
    }

    public boolean update_( double key, int element )
    {
        // Perform "inefficient" but straightforward linear search 
        // for an element then change its key by sifting up or down
        int i;
        for (i = 1; i <= size; i++)
        {
            if (elem[i] == element)
            {
                break;
            }
        }
        if (i > size)
        {
            return false;
        }

        if (key > keys[i])
        {
            // sift up (as in extract)
            while (i * 2 <= size)
            {
                int child = i * 2;
                if (child != size && keys[child + 1] < keys[child])
                {
                    child++;
                }
                if (key > keys[child])
                {
                    elem[i] = elem[child];
                    keys[i] = keys[child];
                    i = child;
                } else
                {
                    break;
                }
            }
            elem[i] = element;
            keys[i] = (float) key;
        } else
        {
            // sift down (as in insert_)
            while (keys[i / 2] > key)
            {
                elem[i] = elem[i / 2];
                keys[i] = keys[i / 2];
                i /= 2;
            }
            elem[i] = element;
            keys[i] = (float) key;
        }
        return true;
    }

    @Override
    public void insert( Number key, Integer element )
    {
        insert_(key.doubleValue(), element);
    }

    public void insert_( double key, int element )
    {
        int i;
        size += 1;
        if (size > capacity)
        {
            ensureCapacity((int) (capacity * GROW_FACTOR));
        }
        for (i = size; keys[i / 2] > key; i /= 2)
        {
            elem[i] = elem[i / 2];
            keys[i] = keys[i / 2];
        }
        elem[i] = element;
        keys[i] = (float) key;
    }

    @Override
    public void ensureCapacity( int capacity )
    {
        // System.out.println("Growing queue to " + capacity);
        if (capacity < size)
        {
            throw new IllegalStateException("BinHeap contains too many elements to fit in new capacity.");
        }
        this.capacity = capacity;
        keys = Arrays.copyOf(keys, capacity + 1);
        elem = Arrays.copyOf(elem, capacity + 1);
    }

    public int getCapacity()
    {
        return capacity;
    }

    float getKey( int index )
    {
        return keys[index];
    }

    int getElement( int index )
    {
        return elem[index];
    }

    void set( int index, float key, int element )
    {
        keys[index] = key;
        elem[index] = element;
    }

    void trimTo( int toSize )
    {
        this.size = toSize;
        toSize++;
        // necessary?
        Arrays.fill(keys, toSize, size + 1, 0f);
        Arrays.fill(elem, toSize, size + 1, 0);
    }

    @Override
    public void clear()
    {
        trimTo(0);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= size; i++)
        {
            if (i > 1)
            {
                sb.append(", ");
            }
            sb.append(keys[i]).append(":").append(elem[i]);
        }
        return sb.toString();
    }

    public String toKeyString()
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= size; i++)
        {
            if (i > 1)
            {
                sb.append(", ");
            }
            sb.append(keys[i]);
        }
        return sb.toString();
    }

    public int indexOfValue( int value )
    {
        for (int i = 0; i <= size; i++)
        {
            if (elem[i] == value)
            {
                return i;
            }
        }
        return -1;
    }
}
