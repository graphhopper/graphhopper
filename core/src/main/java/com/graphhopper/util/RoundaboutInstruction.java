package com.graphhopper.util;

/**
 * Created by jan on 02.02.15.
 *
 * @author jansoe
 */
public class RoundaboutInstruction extends Instruction {

    private int exitNr = 0;
    private boolean continuedStreet = false;
    private int clockwise = 0; // 0 undetermined, 1 clockwise, -1 counterclockwise, -2 inconsistent
    private boolean unfinished = true;

    public RoundaboutInstruction(int sign, String name, InstructionAnnotation ia, PointList pl, double radian) {
        super(sign, name, ia, pl, radian);
    }

    public boolean getContinuedStreet()
    {
        return continuedStreet;
    }

    /**
    * indicates whether on continues on the same street as before after roundabout
    **/
    public RoundaboutInstruction setContinuedStreet(boolean continued)
    {
        this.continuedStreet = continued;
        return this;
    }

    public RoundaboutInstruction increaseExitNr() {
        this.exitNr += 1;
        return this;
    }

    public RoundaboutInstruction setDirOfRotation(double deltaIn)
    {
        if (clockwise == 0) {
            clockwise = deltaIn > 0 ? 1 : -1;
        }
        else
        {
            int clockwise2 = deltaIn > 0 ? 1 : -1;
            if (clockwise != clockwise2) {
                clockwise = 2;
            }
        }
        return this;
    }

    public RoundaboutInstruction setFinished()
    {
        unfinished = false;
        return this;
    }

    public int getExitNr() {
        if (exitNr == 0) {
            throw new IllegalStateException("RoundaboutInstruction must contain exitNr>0");
        }
        return exitNr;
    }

    @Override
    public double getRadian() {
        if (Math.abs(clockwise) != 1) {
            throw new IllegalStateException("Roundabout direction of rotation is not determined");
        }
        double tmpRadian = Math.PI - clockwise*radian;
        return tmpRadian;
    }

    @Override
    public String getTurnDescription(Translation tr) {
        String str;
        String streetName = getName();
        int indi = getSign();
        if (indi == Instruction.USE_ROUNDABOUT)
        {
            if (unfinished)
            {
                //str = tr.tr("roundaboutEntering");
                str = tr.tr("roundaboutInstruction", 0);
            } else if (continuedStreet)
            {
                //str = tr.tr("roundaboutInstructionContinue", getExitNr());
                str = tr.tr("roundaboutInstructionWithDir", getExitNr(), streetName);
            } else {
                str = Helper.isEmpty(streetName) ? tr.tr("roundaboutInstruction", getExitNr()) :
                        tr.tr("roundaboutInstructionWithDir", getExitNr(), streetName);
            }
        } else {
            throw new IllegalStateException(indi + "no Roundabout indication");
        }
        return str;
    }
}