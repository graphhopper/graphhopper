package com.graphhopper.routing.util;

import ch.poole.conditionalrestrictionparser.Condition;
import ch.poole.openinghoursparser.*;
import com.graphhopper.reader.osm.conditional.ConditionalValueParser;
import com.graphhopper.reader.osm.conditional.DateRangeParser;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * @author Andrzej Oles
 */
public class TimeDependentConditionalEvaluator {

    public static boolean match(List<Condition> conditions, ZonedDateTime zonedDateTime) {
        for (Condition condition : conditions) {
            try {
                boolean matched;
                if (zonedDateTime==null) {
                    DateRangeParser dateRangeParser = new DateRangeParser();
                    matched = dateRangeParser.checkCondition(condition)==ConditionalValueParser.ConditionState.TRUE;
                }
                else {
                    OpeningHoursParser parser = new OpeningHoursParser(new ByteArrayInputStream(condition.toString().getBytes()));
                    List<Rule> rules = parser.rules(false);
                    matched = matchRules(rules, zonedDateTime);
                }
                // failed to match any of the rules
                if (!matched)
                    return false;
            } catch (Exception e) {
                return false;
            }
        }
        // all of the conditions successfully matched
        return true;
    }

    private static boolean hasExtendedTime(Rule rule) {
        List<TimeSpan> times = rule.getTimes();
        if (times==null || times.isEmpty())
            return false;
        for (TimeSpan timeSpan: times) {
            // only the end time can exceed 24h
            int end = timeSpan.getEnd();
            if (end != TimeSpan.UNDEFINED_TIME && end > TimeSpan.MAX_TIME)
                return true;
        }
        return false;
    }

    private static boolean matchRules(List<Rule> rules, ZonedDateTime zonedDateTime) {
        TimePoint timePoint = new TimePoint(zonedDateTime, false);
        TimePoint timePointExtended = new TimePoint(zonedDateTime, true);
        for (Rule rule: rules) {
            if (matches(timePoint, rule))
                return true;
            if (hasExtendedTime(rule) && matches(timePointExtended, rule))
                return true;
        }
        // no matching rule found
        return false;
    }

    private static boolean inYearRange(TimePoint timePoint, List<YearRange> years) {
        for (YearRange yearRange: years)
            if (inRange(timePoint.getYear(), yearRange.getStartYear(), yearRange.getEndYear(), YearRange.UNDEFINED_YEAR))
                return true;
        return false;
    }

    private static boolean inDateRange(TimePoint timePoint, List<DateRange> dates) {
        for (DateRange dateRange: dates) {
            DateWithOffset startDate = dateRange.getStartDate();
            DateWithOffset endDate = dateRange.getEndDate();

            if (endDate == null) {
                if (timePoint.atDate(startDate))
                    return true;
            } else {
                if (timePoint.inRange(startDate, endDate))
                    return true;
            }
        }
        return false;
    }

    private static boolean inWeekRange(TimePoint timePoint, List<WeekRange> weeks) {
        for (WeekRange weekRange : weeks)
            if (inRange(timePoint.getWeek(), weekRange.getStartWeek(), weekRange.getEndWeek(), WeekRange.UNDEFINED_WEEK))
                return true;
        return false;
    }

    private static boolean inWeekdayRange(TimePoint timePoint, List<WeekDayRange> days) {
        for (WeekDayRange weekDayRange: days)
            if (inRange(timePoint.getWeekday(), weekDayRange.getStartDay(), weekDayRange.getEndDay()))
                return true;
        return false;
    }

    private static boolean inRange(int value, Enum start, Enum end) {
        if (start == null)
            return true; // unspecified range matches to any value
        if (value >= start.ordinal()) {
            if (end == null)
                return value == start.ordinal();
            else
                return value <= end.ordinal();
        }
        else
            return false;
    }

    private static boolean inTimeRange(TimePoint timePoint, List<TimeSpan> times) {
        for (TimeSpan timeSpan: times)
            if (inRange(timePoint.getMinutes(), timeSpan.getStart(), timeSpan.getEnd(), TimeSpan.UNDEFINED_TIME))
                return true;
        return false;
    }

    private static boolean inRange(int value, int start, int end, int undefined) {
        if (start == undefined)
            return true; // unspecified range matches to any value
        if (end == undefined)
            return value == start;
        if (start > end)// might happen for week ranges
            return (value >= start || value <= end);
        else
            return (value >= start && value <= end);
    }

    private static boolean matches(TimePoint timePoint, Rule rule) {

        List<YearRange> years = rule.getYears();
        if (years!=null && !years.isEmpty())
            if (!inYearRange(timePoint, years))
                return false;

        List<DateRange> dates = rule.getDates();
        if (dates!=null && !dates.isEmpty())
            if (!inDateRange(timePoint, dates))
                return false;

        List<WeekRange> weeks = rule.getWeeks();
        if (weeks!=null && !weeks.isEmpty())
            if (!inWeekRange(timePoint, weeks))
                return false;

        List<WeekDayRange> days = rule.getDays();
        if (days!=null && !days.isEmpty())
            if (!inWeekdayRange(timePoint, days))
                return false;

        List<TimeSpan> times = rule.getTimes();
        if (times!=null && !times.isEmpty())
            if (!inTimeRange(timePoint, times))
                return false;

        return true;
    }
}
