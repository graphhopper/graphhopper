package com.graphhopper.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by jan on 02.02.15.
 *
 * @author jansoe
 */
public class RoundaboutInstruction extends Instruction
{

    private int exitNr = 0;
    private boolean continuedStreet = false;
    private int clockwise = 0; // 0 undetermined, 1 clockwise, -1 counterclockwise, 2 inconsistent
    private boolean finished = false;
    private double radian = Double.NaN; 

    public RoundaboutInstruction(int sign, String name, InstructionAnnotation ia, PointList pl)
    {
        super(sign, name, ia, pl);
    }

    public boolean istContinuedStreet()
    {
        return continuedStreet;
    }

    /**
    * indicates whether one continues on the same street as before after roundabout
    **/
    public RoundaboutInstruction setContinuedStreet(boolean continued)
    {
        this.continuedStreet = continued;
        return this;
    }

    public RoundaboutInstruction increaseExitNr()
    {
        this.exitNr += 1;
        return this;
    }

    public RoundaboutInstruction setExitNr(int exitNr)
    {
        this.exitNr = exitNr;
        return this;
    }

    public RoundaboutInstruction setDirOfRotation(double deltaIn)
    {
        if (clockwise == 0)
        {
            clockwise = deltaIn > 0 ? 1 : -1;
        }
        else
        {
            int clockwise2 = deltaIn > 0 ? 1 : -1;
            if (clockwise != clockwise2)
            {
                clockwise = 2;
            }
        }
        return this;
    }

    public RoundaboutInstruction setFinished()
    {
        finished = true;
        return this;
    }

    private int getExitNr()
    {
        if (finished && exitNr == 0)
        {
            throw new IllegalStateException("RoundaboutInstruction must contain exitNr>0");
        }
        return exitNr;
    }

    /**
     * @return radian of angle  -2PI < x < 2PI between roundabout entrance and exit
     *         values > 0 are clockwise rotation, <0 counterclockwise, NaN if direction of rotation unclear
     */
    public double getRadian()
    {
        if (Math.abs(clockwise) != 1)
        {
            return Double.NaN;
        }
        else
        {
            double tmpRadian = Math.PI - clockwise*radian;
            tmpRadian *= clockwise;
            return tmpRadian;
        }
    }

    public RoundaboutInstruction setRadian(double radian)
    {
        this.radian = radian;
        return this;
    }


    @Override
    public Map<String, Object> getExtraInfo()
    {
        Map<String, Object> tmpMap = new HashMap<String, Object>();
        tmpMap.put("exitNr", getExitNr());
        double radian = getRadian();
        if (Double.isNaN(radian))
        {
            tmpMap.put("turnAngle", radian);    
        } else {
            tmpMap.put("turnAngle", Helper.round(radian, 2));
        }
        return tmpMap;
        
    }
    
    @Override
    public String getTurnDescription(Translation tr)
    {
        String str;
        String streetName = getName();
        int indi = getSign();
        if (indi == Instruction.USE_ROUNDABOUT)
        {
            if (!finished)
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
        } else
        {
            throw new IllegalStateException(indi + "no Roundabout indication");
        }
        return str;
    }
}