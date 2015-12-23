package com.graphhopper.reader.osm.conditional;

import java.text.ParseException;
import java.util.Set;

/**
 * Parses a conditional tag according to http://wiki.openstreetmap.org/wiki/Conditional_restrictions.
 *
 * @author Robin Boldt
 */
public class ConditionalParser
{

    private final Set<String> restrictedTags;

    public ConditionalParser( Set<String> restrictedTags )
    {
        this.restrictedTags = restrictedTags;
    }

    public DateRange getRestrictiveDateRange( String conditionalTag ) throws ParseException
    {

        if (conditionalTag == null || !conditionalTag.contains("@"))
            return null;

        if (conditionalTag.contains(";"))
        {
            // TODO We should split these here

            throw new IllegalStateException("split conditional value not implemented yet");
        }


        String[] conditionalArr = conditionalTag.split("@");

        if (conditionalArr.length != 2)
            throw new IllegalStateException("could not split this condition: " + conditionalTag);

        String restrictiveValue = conditionalArr[0].trim();
        if (!restrictedTags.contains(restrictiveValue))
            return null;

        String conditional = conditionalArr[1];
        conditional = conditional.replace('(', ' ');
        conditional = conditional.replace(')', ' ');
        conditional = conditional.trim();

        return DateRangeParser.parseDateRange(conditional);
    }

}
