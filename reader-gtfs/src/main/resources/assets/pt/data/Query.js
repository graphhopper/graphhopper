import Point from "./Point.js";

const TimeOption = {
    ARRIVAL: 1,
    DEPARTURE: 2
};

const CreateQuery = (baseUrl, search) => {
    let url = new URL(baseUrl);
    url.searchParams.delete("point");
    url.searchParams.append("point", [search.from.lat, search.from.long]);
    url.searchParams.append("point", [search.to.lat, search.to.long]);
    let time = search.departureDateTime
            .clone()     //otherwise the UI also displays utc time.
            .utc()
            .format();
    url.searchParams.set("pt.earliest_departure_time", time);
    if (search.timeOption === TimeOption.ARRIVAL) {
        url.searchParams.set("pt.arrive_by", true);
    } else {
        url.searchParams.set("pt.arrive_by", false);
    }
    url.searchParams.set("locale", "en-US");
    url.searchParams.set("profile", "pt");
    url.searchParams.set("pt.profile", search.rangeQuery);
    url.searchParams.set("pt.profile_duration", search.rangeQueryDuration);
    url.searchParams.set("pt.limit_street_time", search.limitStreetTime);
    url.searchParams.set("pt.ignore_transfers", search.ignoreTransfers);
    return url.toString();
};

const ParseQuery = (search, searchParams) => {
    function parsePoints(searchParams) {
        const points = searchParams.getAll("point");
        if (points.length == 2) {
            search.from = Point.createFromString(points[0]);
            search.to = Point.createFromString(points[1]);
        }
    }

    function parseDepartureTime(searchParams) {
        const departureDateTime = searchParams.get("pt.earliest_departure_time");
        if (departureDateTime) {
            search.departureDateTime = moment(departureDateTime);

            const arriveBy = searchParams.get("pt.arrive_by");
            if (arriveBy && arriveBy == "true") {
                search.timeOption = TimeOption.ARRIVAL;
            } else {
                search.timeOption = TimeOption.DEPARTURE;
            }
        }
    }

    function parse(urlKey, searchKey, searchParams) {
        const value = searchParams.get(urlKey);
        if (value) {
            search[searchKey] = value;
        }
    }

    parsePoints(searchParams);
    parseDepartureTime(searchParams);
    parse("pt.profile", "rangeQuery", searchParams);
    parse("pt.profile_duration", "rangeQueryDuration", searchParams);
    parse("pt.limit_street_time", "limitStreetTime", searchParams);
    parse("pt.ignore_transfers", "ignoreTransfers", searchParams);
    return search;
};

export {CreateQuery, ParseQuery, TimeOption};
