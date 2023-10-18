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
package com.graphhopper.util;

import java.util.*;

/**
 * List of instructions.
 */
public class InstructionList extends AbstractList<Instruction> {

    private final List<Instruction> instructions;
    private final Translation tr;

    public InstructionList(Translation tr) {
        this(10, tr);
    }

    public InstructionList(int cap, Translation tr) {
        instructions = new ArrayList<>(cap);
        this.tr = tr;
    }

    @Override
    public int size() {
        return instructions.size();
    }

    @Override
    public Instruction get(int index) {
        return instructions.get(index);
    }

    @Override
    public Instruction set(int index, Instruction element) {
        return instructions.set(index, element);
    }

    @Override
    public void add(int index, Instruction element) {
        instructions.add(index, element);
    }

    @Override
    public Instruction remove(int index) {
        return instructions.remove(index);
    }

    public Translation getTr() {
        return tr;
    }

}
