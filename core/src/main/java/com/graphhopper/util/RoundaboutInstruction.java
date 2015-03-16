package com.graphhopper.util;

import java.util.HashMap;
import java.util.Map;

/**
 * @author jansoe
 */
public class RoundaboutInstruction extends Instruction
{
    private int exitNumber = 0;
    // 0 undetermined, 1 clockwise, -1 counterclockwise, 2 inconsistent
    private int clockwise = 0;
    private boolean exited = false;
    private double radian = Double.NaN;

    public RoundaboutInstruction( int sign, String name, InstructionAnnotation ia, PointList pl )
    {
        super(sign, name, ia, pl);
    }

    public RoundaboutInstruction increaseExitNumber()
    {
        this.exitNumber += 1;
        return this;
    }

    public RoundaboutInstruction setExitNumber( int exitNumber )
    {
        this.exitNumber = exitNumber;
        return this;
    }

    public RoundaboutInstruction setDirOfRotation( double deltaIn )
    {
        if (clockwise == 0)
        {
            clockwise = deltaIn > 0 ? 1 : -1;
        } else
        {
            int clockwise2 = deltaIn > 0 ? 1 : -1;
            if (clockwise != clockwise2)
            {
                clockwise = 2;
            }
        }
        return this;
    }

    public RoundaboutInstruction setExited()
    {
        exited = true;
        return this;
    }

    public boolean isExited()
    {
        return exited;
    }

    public int getExitNumber()
    {
        if (exited && exitNumber == 0)
        {
            throw new IllegalStateException("RoundaboutInstruction must contain exitNumber>0");
        }
        return exitNumber;
    }

    /**
     * @return radian of angle -2PI < x < 2PI between roundabout entrance and exit
     *         values > 0 are clockwise rotation, <0 counterclockwise, NaN if direction of rotation unclear
     */
    public double getRadian()
    {
        if (Math.abs(clockwise) != 1)
        {
            return Double.NaN;
        } else
        {
            double tmpRadian = Math.PI - clockwise * radian;
            tmpRadian *= clockwise;
            return tmpRadian;
        }
    }

    public RoundaboutInstruction setRadian( double radian )
    {
        this.radian = radian;
        return this;
    }

    @Override
    public Map<String, Object> getExtraInfoJSON()
    {
        Map<String, Object> tmpMap = new HashMap<String, Object>(2);
        tmpMap.put("exit_number", getExitNumber());
        double radian = getRadian();
        if (!Double.isNaN(radian))
        {
            tmpMap.put("turn_angle", Helper.round(radian, 2));
        }

        return tmpMap;

    }

    @Override
    public String getTurnDescription( Translation tr )
    {
        if (rawName)
            return getName();

        String str;
        String streetName = getName();
        int indi = getSign();
        if (indi == Instruction.USE_ROUNDABOUT)
        {
            if (!exited)
            {
                str = tr.tr("roundaboutEnter");
            } else
            {
                str = Helper.isEmpty(streetName) ? tr.tr("roundaboutExit", getExitNumber())
                        : tr.tr("roundaboutExitOnto", getExitNumber(), streetName);
            }
        } else
        {
            throw new IllegalStateException(indi + "no Roundabout indication");
        }
        return str;
    }
}
