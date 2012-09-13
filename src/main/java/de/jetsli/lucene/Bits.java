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
package de.jetsli.lucene;

/**
 * Interface for Bitset-like structures.
 *
 * @lucene.experimental
 */
public interface Bits {

    public boolean get(int index);

    public int length();
    public static final Bits[] EMPTY_ARRAY = new Bits[0];

    /**
     * Bits impl of the specified length with all bits set.
     */
    public static class MatchAllBits implements Bits {

        final int len;

        public MatchAllBits(int len) {
            this.len = len;
        }

        public boolean get(int index) {
            return true;
        }

        public int length() {
            return len;
        }
    }

    /**
     * Bits impl of the specified length with no bits set.
     */
    public static class MatchNoBits implements Bits {

        final int len;

        public MatchNoBits(int len) {
            this.len = len;
        }

        public boolean get(int index) {
            return false;
        }

        public int length() {
            return len;
        }
    }
}
