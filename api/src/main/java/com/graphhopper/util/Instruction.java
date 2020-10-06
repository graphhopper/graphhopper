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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Instruction {
    public static final int UNKNOWN = -99;
    public static final int U_TURN_UNKNOWN = -98;
    public static final int U_TURN_LEFT = -8;
    public static final int KEEP_LEFT = -7;
    public static final int LEAVE_ROUNDABOUT = -6; // for future use
    public static final int TURN_SHARP_LEFT = -3;
    public static final int TURN_LEFT = -2;
    public static final int TURN_SLIGHT_LEFT = -1;
    public static final int CONTINUE_ON_STREET = 0;
    public static final int TURN_SLIGHT_RIGHT = 1;
    public static final int TURN_RIGHT = 2;
    public static final int TURN_SHARP_RIGHT = 3;
    public static final int FINISH = 4;
    public static final int REACHED_VIA = 5;
    public static final int USE_ROUNDABOUT = 6;
    public static final int IGNORE = Integer.MIN_VALUE;
    public static final int KEEP_RIGHT = 7;
    public static final int U_TURN_RIGHT = 8;
    public static final int PT_START_TRIP = 101;
    public static final int PT_TRANSFER = 102;
    public static final int PT_END_TRIP = 103;
    private static final int VOICE_INSTRUCTION_MERGE_TRESHHOLD = 100;
    protected PointList points;
    protected final InstructionAnnotation annotation;
    protected boolean rawName;
    protected int sign;
    protected String name;
    protected double distance;
    protected long time;
    protected VoiceInstructionList voiceInstructions;
    protected Map<String, Object> extraInfo = new HashMap<>(3);

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one.
     */
    public Instruction(int sign, String name, InstructionAnnotation ia, PointList pl) {
        this.sign = sign;
        this.name = name;
        this.points = pl;
        this.annotation = ia;
    }

    /**
     * This method does not perform translation or combination with the sign - it just uses the
     * provided name as instruction.
     */
    public void setUseRawName() {
        rawName = true;
    }

    public InstructionAnnotation getAnnotation() {
        return annotation;
    }

    /**
     * The instruction for the person/driver to execute.
     */
    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getExtraInfoJSON() {
        return extraInfo;
    }

    public void setExtraInfo(String key, Object value) {
        extraInfo.put(key, value);
    }

    /**
     * Distance in meter until no new instruction
     */
    public double getDistance() {
        return distance;
    }

    public Instruction setDistance(double distance) {
        this.distance = distance;
        return this;
    }

    /**
     * Duration until the next instruction, in milliseconds
     */
    public long getTime() {
        return time;
    }

    public Instruction setTime(long time) {
        this.time = time;
        return this;
    }

    /* This method returns the points associated to this instruction. Please note that it will not include the last point,
     * i.e. the first point of the next instruction object.
     */
    public PointList getPoints() {
        return points;
    }

    public void setPoints(PointList points) {
        this.points = points;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(sign).append(',');
        sb.append(name).append(',');
        sb.append(distance).append(',');
        sb.append(time);
        sb.append(')');
        return sb.toString();
    }

    /**
     * This method returns the length of an Instruction. The length of an instruction is defined by [the
     * index of the first point of the next instruction] - [the index of the first point of this instruction].
     * <p>
     * In general this will just resolve to the size of the PointList, except for {@link ViaInstruction} and
     * {@link FinishInstruction}, which are only virtual instructions, in a sense that they don't provide
     * a turn instruction, but only an info ("reached via point or destination").
     * <p>
     * See #1216 and #1138
     */
    public int getLength() {
        return points.getSize();
    }

    public String getTurnDescription(Translation tr) {
        if (rawName)
            return getName();

        String str;
        String streetName = getName();
        int indi = getSign();
        if (indi == Instruction.CONTINUE_ON_STREET) {
            str = Helper.isEmpty(streetName) ? tr.tr("continue") : tr.tr("continue_onto", streetName);
        } else if (indi == Instruction.PT_START_TRIP) {
            str = tr.tr("pt_start_trip", streetName);
        } else if (indi == Instruction.PT_TRANSFER) {
            str = tr.tr("pt_transfer_to", streetName);
        } else if (indi == Instruction.PT_END_TRIP) {
            str = tr.tr("pt_end_trip", streetName);
        } else {
            String dir = null;
            switch (indi) {
                case Instruction.U_TURN_UNKNOWN:
                    dir = tr.tr("u_turn");
                    break;
                case Instruction.U_TURN_LEFT:
                    dir = tr.tr("u_turn");
                    break;
                case Instruction.U_TURN_RIGHT:
                    dir = tr.tr("u_turn");
                    break;
                case Instruction.KEEP_LEFT:
                    dir = tr.tr("keep_left");
                    break;
                case Instruction.TURN_SHARP_LEFT:
                    dir = tr.tr("turn_sharp_left");
                    break;
                case Instruction.TURN_LEFT:
                    dir = tr.tr("turn_left");
                    break;
                case Instruction.TURN_SLIGHT_LEFT:
                    dir = tr.tr("turn_slight_left");
                    break;
                case Instruction.TURN_SLIGHT_RIGHT:
                    dir = tr.tr("turn_slight_right");
                    break;
                case Instruction.TURN_RIGHT:
                    dir = tr.tr("turn_right");
                    break;
                case Instruction.TURN_SHARP_RIGHT:
                    dir = tr.tr("turn_sharp_right");
                    break;
                case Instruction.KEEP_RIGHT:
                    dir = tr.tr("keep_right");
                    break;
            }
            if (dir == null)
                str = tr.tr("unknown", indi);
            else
                str = Helper.isEmpty(streetName) ? dir : tr.tr("turn_onto", dir, streetName);
        }
        return str;
    }

    public VoiceInstructionList getVoiceInstructions() {
        if (voiceInstructions == null) {
            return new VoiceInstructionList(0);
        }
        return voiceInstructions;
    }

    public void setVoiceInstructions (InstructionList instructions, int index, VoiceInstructionDistanceConfig distanceConfig) {
        this.voiceInstructions = new VoiceInstructionList();

        Instruction nextInstruction = instructions.get(index + 1);
        String turnDescription = nextInstruction.getTurnDescription(distanceConfig.getTranslation());

        String thenVoiceInstruction = getThenVoiceInstructionpart(instructions, index, distanceConfig.getTranslation());

        List<VoiceInstructionConfig.VoiceInstructionValue> voiceValues = distanceConfig.getVoiceInstructionsForDistance(distance, turnDescription, thenVoiceInstruction);

        for (VoiceInstructionConfig.VoiceInstructionValue voiceValue : voiceValues) {
            this.voiceInstructions.add(
                new VoiceInstruction(voiceValue.turnDescription, voiceValue.spokenDistance)
            );
        }

        // Speak 80m instructions 80 before the turn
        // Note: distanceAlongGeometry: "how far from the upcoming maneuver the voice instruction should begin"
        double distanceAlongGeometry = Helper.round(Math.min(distance, 80), 1);

        // Special case for the arrive instruction
        if (index + 2 == instructions.size())
            distanceAlongGeometry = Helper.round(Math.min(distance, 25), 1);
        
        this.voiceInstructions.add(
            new VoiceInstruction(turnDescription + thenVoiceInstruction, distanceAlongGeometry)
        );
    }

    /**
     * For close turns, it is important to announce the next turn in the earlier instruction.
     * e.g.: instruction i+1= turn right, instruction i+2=turn left, with instruction i+1 distance < VOICE_INSTRUCTION_MERGE_TRESHHOLD
     * The voice instruction should be like "turn right, then turn left"
     * <p>
     * For instruction i+1 distance > VOICE_INSTRUCTION_MERGE_TRESHHOLD an empty String will be returned
     */
    private static String getThenVoiceInstructionpart(InstructionList instructions, int index, Translation translation) {
        if (instructions.size() > index + 2) {
            Instruction firstInstruction = instructions.get(index + 1);
            if (firstInstruction.getDistance() < VOICE_INSTRUCTION_MERGE_TRESHHOLD) {
                Instruction secondInstruction = instructions.get(index + 2);
                if (secondInstruction.getSign() != Instruction.REACHED_VIA)
                    return ", " + translation.tr("navigate.then") + " " + secondInstruction.getTurnDescription(translation);
            }
        }

        return "";
    }
}
