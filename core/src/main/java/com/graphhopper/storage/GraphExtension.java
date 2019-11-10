/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
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
package com.graphhopper.storage;

/**
 * If you need custom storages, like turn cost tables, or osmid tables for your graph you implement
 * this interface and add it to any graph storage you want.
 */
public interface GraphExtension extends Storable<GraphExtension> {
    /**
     * @return true, if and only if an additional field at the graphs node storage is required
     */
    boolean isRequireNodeField();

    /**
     * @return true, if and only if an additional field at the graphs edge storage is required
     */
    boolean isRequireEdgeField();

    /**
     * @return the default field value which will be set for default when creating nodes
     */
    int getDefaultNodeFieldValue();

    /**
     * @return the default field value which will be set for default when creating edges
     */
    int getDefaultEdgeFieldValue();

    /**
     * initializes the extended storage by giving the base graph
     */
    void init(Graph graph, Directory dir);

    /**
     * sets the segment size in all additional data storages
     */
    void setSegmentSize(int bytes);

    /**
     * creates a copy of this extended storage
     */
    GraphExtension copyTo(GraphExtension extStorage);

}
