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
 * Accepts an OSMWay according to the given conditional tags.
 *
 * @author Robin Boldt
 */
public class ConditionalTagWayAcceptor implements WayAcceptor
{

    private static final Logger logger = LoggerFactory.getLogger(ConditionalTagWayAcceptor.class);

    private final Calendar calendar;
    private final List<String> tagsToCheck;
    private final ConditionalParser parser;

    /**
     * Create with todays date
     */
    public ConditionalTagWayAcceptor( List<String> tagsToCheck, Set<String> restricedValues )
    {
        this(Calendar.getInstance(), tagsToCheck, restricedValues);
    }

    /**
     * Create with given date
     */
    public ConditionalTagWayAcceptor( Calendar date, List<String> tagsToCheck, Set<String> restricedValues )
    {
        this.calendar = date;
        this.tagsToCheck = tagsToCheck;
        this.parser = new ConditionalParser(restricedValues);
    }

    @Override
    public boolean accept( OSMWay way )
    {
        for (String tagToCheck : tagsToCheck)
        {
            tagToCheck = tagToCheck + ":conditional";
            String val = way.getTag(tagToCheck);
            if (val != null && !val.isEmpty())
            {
                try
                {
                    DateRange dateRange = parser.getRestrictiveDateRange(val);
                    if (dateRange != null && dateRange.isInRange(calendar))
                        return false;
                } catch (Exception e)
                {
                    logger.info("Could not parse the value:" + val + " of tag:" + tagToCheck + ". The Exception Message is:" + e.getMessage());
                }
            }
        }

        return true;
    }
}
