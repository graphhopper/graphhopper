package com.graphhopper.util;

/**
 * Instruction for an Autonomous Vehicle (AV)
 * Created by bill@rideos.ai on 8/2/17.
 */
public class InstructionAV extends Instruction {

    protected boolean enableAutonomy;

    public InstructionAV(int sign, String name, InstructionAnnotation ia, PointList pl) {
        super(sign, name, ia, pl);
        this.enableAutonomy = false;
    }

    /**
     * Set whether autonomy can be enabled for this instruction.
     * @param enableAutonomy
     */
    public void setEnableAutonomy(boolean enableAutonomy) {
        this.enableAutonomy = enableAutonomy;
    }

    /**
     * Returns whether autonomy is enabled for this instruction.
     * @return
     */
    public boolean getEnableAutonomy() {
        return this.enableAutonomy;
    }
}
