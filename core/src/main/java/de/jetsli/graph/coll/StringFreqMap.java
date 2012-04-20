/**
 * Copyright (C) 2010 Peter Karich <info@jetsli.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.jetsli.graph.coll;

import de.jetsli.graph.util.Helper;
import java.util.Map.Entry;
import java.util.*;

/**
 * Holds string frequency
 *
 * @author Peter Karich, info@jetsli.de
 */
public class StringFreqMap extends LinkedHashMap<String, Integer> {

    private static final long serialVersionUID = 1L;

    public StringFreqMap() {
    }

    public StringFreqMap(Map<? extends String, ? extends Integer> m) {
        super(m);
    }

    public StringFreqMap(int initialCapacity) {
        super(initialCapacity);
    }

    public StringFreqMap set(String key, Integer count) {
        put(key, count);
        return this;
    }

    public StringFreqMap setAll(Map<String, Integer> map) {
        putAll(map);
        return this;
    }

//    public Set<String> and(Map<String, Integer> map) {
//        Set<String> res = new LinkedHashSet<String>();
//        Set<String> iterSet;
//        Set<String> otherSet;
//        if (size() > map.size()) {
//            iterSet = map.keySet();
//            otherSet = keySet();
//        } else {
//            iterSet = this.keySet();
//            otherSet = map.keySet();
//        }
//
//        for (String iterStr : iterSet) {
//            if (otherSet.contains(iterStr))
//                res.add(iterStr);
//        }
//
//        return res;
//    }
    public int andSize(Map<String, Integer> other) {
        Set<Entry<String, Integer>> iterSet;
        Map<String, Integer> otherMap;
        if (size() > other.size()) {
            iterSet = other.entrySet();
            otherMap = this;
        } else {
            iterSet = this.entrySet();
            otherMap = other;
        }

        int counter = 0;
        for (Entry<String, Integer> iterEntry : iterSet) {
            if (otherMap.containsKey(iterEntry.getKey()))
                counter += iterEntry.getValue();
        }
        return counter;
    }

    public int orSize(Map<String, Integer> map) {
        int counter = 0;
        for (Entry<String, Integer> e : or(map).entrySet()) {
            counter += e.getValue();
        }
        return counter;
    }

    /**
     * Returns unsorted merge of all strings
     */
    public Map<String, Integer> or(Map<String, Integer> otherMap) {
        Map<String, Integer> res = new LinkedHashMap<String, Integer>(this);
        for (Entry<String, Integer> entry : otherMap.entrySet()) {
            Integer oldInt = res.put(entry.getKey(), entry.getValue());
            if (oldInt != null)
                res.put(entry.getKey(), Math.max(oldInt, entry.getValue()));
        }

        return res;
    }

    public StringFreqMap addOne2All(Map<String, Integer> map) {
        for (Entry<String, Integer> e : map.entrySet()) {
            inc(e.getKey(), 1);
        }
        return this;
    }

    public StringFreqMap addValue2All(Map<String, Integer> map) {
        for (Entry<String, Integer> e : map.entrySet()) {
            inc(e.getKey(), e.getValue());
        }
        return this;
    }

    public boolean inc(String key, int val) {
        Integer integ = get(key);
        if (integ == null)
            integ = 0;

        put(key, integ + val);
        return true;
    }

    /**
     * @return a list of sorted entries (highest integer values comes first)
     */
    public List<Entry<String, Integer>> getSorted() {
        return Helper.sort(entrySet());
    }

    public List<Entry<String, Integer>> getSortedLimited(int termMaxCount) {
        List<Entry<String, Integer>> res = Helper.sort(entrySet());
        int min = Math.min(termMaxCount, res.size());
        return res.subList(0, min);
    }

    public Collection<String> getSortedTermsLimited(int termMaxCount) {
        List<Entry<String, Integer>> list = getSortedLimited(termMaxCount);
        Set<String> tags = new LinkedHashSet<String>(list.size());
        for (Entry<String, Integer> e : list) {
            tags.add(e.getKey());
        }
        return tags;
    }

    /**
     *
     * @param freq specifies the relative limit to the maximal frequency.
     * E.g. you have "a 10", "b 2", "c 1" and specifes percentage=0.2 (means 20%) then you
     * would get only "a 10", "b 2" (freq limit is inclusive)
     */
    public List<Entry<String, Integer>> getSortedFreqLimit(float freq) {
        if (size() == 0)
            return Collections.emptyList();

        List<Entry<String, Integer>> tmp = Helper.sort(entrySet());
        List<Entry<String, Integer>> res = new ArrayList<Entry<String, Integer>>();

        int cmpFreq = Math.round(freq * tmp.get(0).getValue());
        for (Entry<String, Integer> e : tmp) {
            if (e.getValue() >= cmpFreq)
                res.add(e);
        }
        return res;
    }
}
