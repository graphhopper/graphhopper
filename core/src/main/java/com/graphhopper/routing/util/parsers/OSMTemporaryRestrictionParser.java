package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.ConditionalValueParser;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.*;

public class OSMTemporaryRestrictionParser implements TagParser {

    private static final Logger logger = LoggerFactory.getLogger(OSMTemporaryRestrictionParser.class);
    private static final Collection<String> CONDITIONALS = new HashSet<>(Arrays.asList("access:conditional",
            "vehicle:conditional", "motor_vehicle:conditional", "motorcar:conditional"));
    private final BooleanEncodedValue temporaryRestriction;
    private final DateRangeParser parser;
    private final boolean enabledLogs = false;

    public OSMTemporaryRestrictionParser(BooleanEncodedValue temporaryRestriction, String dateRangeParserDate) {
        this.temporaryRestriction = temporaryRestriction;
        if (dateRangeParserDate.isEmpty())
            dateRangeParserDate = Helper.createFormatter("yyyy-MM-dd").format(new Date().getTime());

        this.parser = DateRangeParser.createInstance(dateRangeParserDate);
    }

    @Override
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay way, IntsRef relationFlags) {
        // TODO for now the node tag overhead is not worth the effort due to very few data points
        // List<Map<String, Object>> nodeTags = way.getTag("node_tags", null);

        if (hasConstructionInTags(way.getTags()))
            temporaryRestriction.setBool(false, edgeId, edgeIntAccess, true);
    }

    boolean hasConstructionInTags(Map<String, Object> tags) {
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (!CONDITIONALS.contains(entry.getKey())) continue;

            String value = (String) entry.getValue();
            String[] strs = value.split("@");
            if (strs.length == 2 && strs[0].trim().equals("no") && isInRange(strs[1].trim()))
                return true;
        }
        return false;
    }

    private boolean isInRange(final String value) {
        if (value.isEmpty())
            return false;

        if (value.contains(";")) {
            if (enabledLogs)
                logger.warn("We do not support multiple conditions yet: " + value);
            return false;
        }

        String conditionalValue = value.replace('(', ' ').replace(')', ' ').trim();
        try {
            ConditionalValueParser.ConditionState res = parser.checkCondition(conditionalValue);
            if (res.isValid())
                return res.isCheckPassed();
            if (enabledLogs)
                logger.warn("Invalid date to parse " + conditionalValue);
        } catch (ParseException ex) {
            if (enabledLogs)
                logger.warn("Cannot parse " + conditionalValue);
        }
        return false;
    }
}
