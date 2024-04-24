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

import com.graphhopper.util.BitUtil;

public class EdgeBytesAccessArray implements EdgeBytesAccess {
    private static final BitUtil bitUtil = BitUtil.LITTLE;
    private final byte[] bytes;

    public EdgeBytesAccessArray(byte[] bytes) {
        this.bytes = bytes;
    }

    public EdgeBytesAccessArray(int cap) {
        this.bytes = new byte[cap];
    }

    @Override
    public void setBytes(int edgeId, int edgeRowBytesOffset, byte[] bytes, int bytesOffset, int len) {
        System.arraycopy(bytes, bytesOffset, this.bytes, edgeRowBytesOffset, len);
    }

    @Override
    public void getBytes(int edgeId, int edgeRowBytesOffset, byte[] bytes, int bytesOffset, int len) {
        System.arraycopy(this.bytes, edgeRowBytesOffset, bytes, bytesOffset, len);
    }

   @Override
   public int getInt(int edgeId, int index) {
       if (index + 3 == bytes.length)
           return bitUtil.toUInt3(bytes, index);
       if (index + 2 == bytes.length)
           return bitUtil.toShort(bytes, index);
       if (index + 1 == bytes.length)
           return bytes[index];
       return bitUtil.toInt(bytes, index);
   }

   @Override
   public void setInt(int edgeId, int index, int value) {
       if (index + 3 == bytes.length) {
           if (value < 0)
               throw new IllegalArgumentException("value was " + value + " but negative currently not supported");
           bitUtil.fromUInt3(bytes, value, index);
       } else if (index + 2 == bytes.length)
           bitUtil.fromShort(bytes, (short) value, index);
       else if (index + 1 == bytes.length)
           bytes[index] = (byte) value;
       else
           bitUtil.fromInt(bytes, value, index);
   }

}
