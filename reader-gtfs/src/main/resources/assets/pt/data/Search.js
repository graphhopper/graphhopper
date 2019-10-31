const SearchActionType = {
  FROM: "SearchActionType_FROM",
  TO: "SearchActionType_TO",
  WEIGHTING: "SearchActionType_WEIGHTING",
  DEPARTURE_TIME: "SearchActionType_DEPARTURE_TIME",
  DEPARTURE_DATE: "SearchActionType_DEPARTURE_DATE",
  LIMIT_SOLUTIONS: "SearchActionType_LIMIT_SOLUTIONS",
  SEARCH_URL_CHANGED: "SearchActionType_SEARCH_URL_CHANGED",
  TIME_OPTION: "SearchActionType_TIME_OPTION",
  IS_SHOWING_OPTIONS: "SearchActionType_IS_SHOWING_OPTIONS"
};

const TimeOption = {
  NOW: 0,
  ARRIVAL: 1,
  DEPARTURE: 2
};
export { SearchActionType, TimeOption };
