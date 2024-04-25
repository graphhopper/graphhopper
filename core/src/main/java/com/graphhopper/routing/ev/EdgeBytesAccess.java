/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.routing.ev;

public interface EdgeBytesAccess {
    void setBytes(int edgeId, int edgeRowBytesOffset, byte[] bytes, int bytesOffset, int len);

    void getBytes(int edgeId, int edgeRowBytesOffset, byte[] bytes, int bytesOffset, int len);

    byte getByte(int edgeId, int edgeRowBytesOffset);

    /**
     * Gets the int value at the given index for the given edgeId
     */
    int getInt(int edgeId, int edgeRowBytesOffset);

    /**
     * Sets the int value at the given index for the given edgeId
     */
    void setInt(int edgeId, int edgeRowBytesOffset, int value);
}
