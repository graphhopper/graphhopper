package com.graphhopper.reader.osm;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.reader.osm.conditional.ConditionalParser;
import com.graphhopper.reader.osm.conditional.DateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * Inspects the conditional tags of an OSMWay according to the given conditional tags.
 *
 * @author Robin Boldt
 */
public class ConditionalTagsInspector
{

    private static final Logger logger = LoggerFactory.getLogger(ConditionalTagsInspector.class);

    private final Calendar calendar;
    private final List<String> tagsToCheck;
    private final ConditionalParser restrictiveParser;
    private final ConditionalParser permitParser;

    /**
     * Create with todays date
     */
    public ConditionalTagsInspector( List<String> tagsToCheck, Set<String> restrictiveValues, Set<String> permittedValues )
    {
        this(Calendar.getInstance(), tagsToCheck, restrictiveValues, permittedValues);
    }

    /**
     * Create with given date
     */
    public ConditionalTagsInspector( Calendar date, List<String> tagsToCheck, Set<String> restrictiveValues, Set<String> permittedValues )
    {
        this.calendar = date;
        this.tagsToCheck = tagsToCheck;
        this.restrictiveParser = new ConditionalParser(restrictiveValues);
        this.permitParser = new ConditionalParser(permittedValues);
    }

    public boolean isRestrictedWayConditionallyPermissed( OSMWay way){
        return applies(way, true);
    }

    public boolean isPermittedWayConditionallyRestriced( OSMWay way){
        return applies(way, false);
    }

    protected boolean applies(OSMWay way, boolean checkPermissiveValues)
    {
        for (String tagToCheck : tagsToCheck)
        {
            tagToCheck = tagToCheck + ":conditional";
            String val = way.getTag(tagToCheck);
            if (val != null && !val.isEmpty())
            {
                try
                {
                    DateRange dateRange;
                    if(checkPermissiveValues)
                        dateRange = permitParser.getRestrictiveDateRange(val);
                    else
                        dateRange = restrictiveParser.getRestrictiveDateRange(val);

                    if (dateRange != null && dateRange.isInRange(calendar))
                        return true;
                } catch (Exception e)
                {
                    logger.info("Could not parse the value:" + val + " of tag:" + tagToCheck + ". The Exception Message is:" + e.getMessage());
                }
            }
        }
        return false;
    }
}
