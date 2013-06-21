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
package com.graphhopper.trees;

import com.graphhopper.util.Helper;

/**
 * @author Peter Karich
 */
class QTBranchNode<V> implements QTNode<V>
{
    QTNode<V> node0;
    QTNode<V> node1;
    QTNode<V> node2;
    QTNode<V> node3;

    public QTBranchNode()
    {
    }

    @Override
    public final QTNode<V> get( int num )
    {
        switch (num)
        {
            case 0:
                return node0;
            case 1:
                return node1;
            case 2:
                return node2;
            default:
                return node3;
        }
    }

    @Override
    public void set( int num, QTNode<V> n )
    {
        switch (num)
        {
            case 0:
                node0 = n;
                return;
            case 1:
                node1 = n;
                return;
            case 2:
                node2 = n;
                return;
            default:
                node3 = n;
                return;
        }
    }

    @Override
    public final boolean hasData()
    {
        return false;
    }

    @Override
    public String toString()
    {
        return "B 0:" + node0.hasData() + " 1:" + node1.hasData() + " 2:" + node2.hasData() + " 3:" + node3.hasData();
    }

    @Override
    public long getMemoryUsageInBytes( int factor )
    {
        // recursivly fetch the results
        long all = 4 * Helper.getSizeOfObjectRef(factor);
        if (node0 != null)
        {
            all += node0.getMemoryUsageInBytes(factor);
        }
        if (node1 != null)
        {
            all += node1.getMemoryUsageInBytes(factor);
        }
        if (node2 != null)
        {
            all += node2.getMemoryUsageInBytes(factor);
        }
        if (node3 != null)
        {
            all += node3.getMemoryUsageInBytes(factor);
        }
        return all;
    }

    @Override
    public int count()
    {
        int all = 0;
        if (node0 != null)
        {
            all += node0.count();
        }
        if (node1 != null)
        {
            all += node1.count();
        }
        if (node2 != null)
        {
            all += node2.count();
        }
        if (node3 != null)
        {
            all += node3.count();
        }
        return all;
    }

    @Override
    public long getEmptyEntries( boolean onlyBranches )
    {
        int all = 0;
        if (node0 == null)
        {
            all++;
        } else
        {
            all += node0.getEmptyEntries(onlyBranches);
        }

        if (node1 == null)
        {
            all++;
        } else
        {
            all += node1.getEmptyEntries(onlyBranches);
        }

        if (node2 == null)
        {
            all++;
        } else
        {
            all += node2.getEmptyEntries(onlyBranches);
        }

        if (node3 == null)
        {
            all++;
        } else
        {
            all += node3.getEmptyEntries(onlyBranches);
        }
        return all;
    }
}
