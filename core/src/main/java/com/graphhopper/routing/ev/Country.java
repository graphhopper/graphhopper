/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"),; you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.ev;

/**
 * The enum constants correspond to the ISO3166-1 code (alpha2) of the corresponding country.
 * For a few countries like the USA there are separate instances for each subdivision and their
 * enum ends with their ISO 3166-2 code like US_WY. The toString returns the alpha3 string like "USA".
 * To get the alpha2 code with the subdivision combined with a minus you use getAlpha2AndSubdivision().
 */
public enum Country {

    MISSING("missing", "---", "--"),
    AF("Afghanistan", "AFG", "AF"),
    AO("Angola", "AGO", "AO"),
    AI("Anguilla", "AIA", "AI"),
    AL("Albania", "ALB", "AL"),
    AD("Andorra", "AND", "AD"),
    AE("United Arab Emirates", "ARE", "AE"),
    AR("Argentina", "ARG", "AR"),
    AM("Armenia", "ARM", "AM"),
    AG("Antigua and Barbuda", "ATG", "AG"),
    AU("Australia", "AUS", "AU"),
    AU_ACT("Australia", "AUS", "AU", "Australian Capital Territory", "ACT"),
    AU_NSW("Australia", "AUS", "AU", "New South Wales", "NSW"),
    AU_NT("Australia", "AUS", "AU", "Northern Territory", "NT"),
    AU_QLD("Australia", "AUS", "AU", "Queensland", "QLD"),
    AU_SA("Australia", "AUS", "AU", "South Australia", "SA"),
    AU_TAS("Australia", "AUS", "AU", "Tasmania", "TAS"),
    AU_VIC("Australia", "AUS", "AU", "Victoria", "VIC"),
    AU_WA("Australia", "AUS", "AU", "Western Australia", "WA"),
    AT("Austria", "AUT", "AT"),
    AZ("Azerbaijan", "AZE", "AZ"),
    BI("Burundi", "BDI", "BI"),
    BE("Belgium", "BEL", "BE"),
    BE_BRU("Belgium", "BEL", "BE", "Brussels-Capital Region", "BRU"),
    BE_VLG("Belgium", "BEL", "BE", "Flemish Region", "VLG"),
    BE_WAL("Belgium", "BEL", "BE", "Walloon Region", "WAL"),
    BJ("Benin", "BEN", "BJ"),
    BF("Burkina Faso", "BFA", "BF"),
    BD("Bangladesh", "BGD", "BD"),
    BG("Bulgaria", "BGR", "BG"),
    BH("Bahrain", "BHR", "BH"),
    BS("The Bahamas", "BHS", "BS"),
    BA("Bosnia and Herzegovina", "BIH", "BA"),
    BY("Belarus", "BLR", "BY"),
    BZ("Belize", "BLZ", "BZ"),
    BM("Bermuda", "BMU", "BM"),
    BO("Bolivia", "BOL", "BO"),
    BR("Brazil", "BRA", "BR"),
    BB("Barbados", "BRB", "BB"),
    BN("Brunei", "BRN", "BN"),
    BT("Bhutan", "BTN", "BT"),
    BW("Botswana", "BWA", "BW"),
    CF("Central African Republic", "CAF", "CF"),
    CA("Canada", "CAN", "CA"),
    CA_AB("Canada", "CAN", "CA", "Alberta", "AB"),
    CA_BC("Canada", "CAN", "CA", "British Columbia", "BC"),
    CA_MB("Canada", "CAN", "CA", "Manitoba", "MB"),
    CA_NB("Canada", "CAN", "CA", "New Brunswick", "NB"),
    CA_NL("Canada", "CAN", "CA", "Newfoundland and Labrador", "NL"),
    CA_NS("Canada", "CAN", "CA", "Nova Scotia", "NS"),
    CA_NT("Canada", "CAN", "CA", "Northwest Territories", "NT"),
    CA_NU("Canada", "CAN", "CA", "Nunavut", "NU"),
    CA_ON("Canada", "CAN", "CA", "Ontario", "ON"),
    CA_PE("Canada", "CAN", "CA", "Prince Edward Island", "PE"),
    CA_QC("Canada", "CAN", "CA", "Quebec", "QC"),
    CA_SK("Canada", "CAN", "CA", "Saskatchewan", "SK"),
    CA_YT("Canada", "CAN", "CA", "Yukon Territory", "YT"),
    CH("Switzerland", "CHE", "CH"),
    CL("Chile", "CHL", "CL"),
    CN("China", "CHN", "CN"),
    CI("Côte d'Ivoire", "CIV", "CI"),
    CM("Cameroon", "CMR", "CM"),
    CD("Democratic Republic of the Congo", "COD", "CD"),
    CG("Congo-Brazzaville", "COG", "CG"),
    CK("Cook Islands", "COK", "CK"),
    CO("Colombia", "COL", "CO"),
    KM("Comoros", "COM", "KM"),
    CV("Cape Verde", "CPV", "CV"),
    CR("Costa Rica", "CRI", "CR"),
    CU("Cuba", "CUB", "CU"),
    KY("Cayman Islands", "CYM", "KY"),
    CY("Cyprus", "CYP", "CY"),
    CZ("Czechia", "CZE", "CZ"),
    DE("Germany", "DEU", "DE"),
    DJ("Djibouti", "DJI", "DJ"),
    DM("Dominica", "DMA", "DM"),
    DK("Denmark", "DNK", "DK"),
    DO("Dominican Republic", "DOM", "DO"),
    DZ("Algeria", "DZA", "DZ"),
    EC("Ecuador", "ECU", "EC"),
    EG("Egypt", "EGY", "EG"),
    ER("Eritrea", "ERI", "ER"),
    ES("Spain", "ESP", "ES"),
    EE("Estonia", "EST", "EE"),
    ET("Ethiopia", "ETH", "ET"),
    FI("Finland", "FIN", "FI"),
    FJ("Fiji", "FJI", "FJ"),
    FK("Falkland Islands", "FLK", "FK"),
    FR("France", "FRA", "FR"),
    FO("Faroe Islands", "FRO", "FO"),
    FM("Federated States of Micronesia", "FSM", "FM"),
    FM_KSA("Federated States of Micronesia", "FSM", "FM", "Kosrae", "KSA"),
    FM_PNI("Federated States of Micronesia", "FSM", "FM", "Pohnpei", "PNI"),
    FM_TRK("Federated States of Micronesia", "FSM", "FM", "Chuuk", "TRK"),
    FM_YAP("Federated States of Micronesia", "FSM", "FM", "Yap", "YAP"),
    GA("Gabon", "GAB", "GA"),
    GB("United Kingdom", "GBR", "GB"),
    GB_SCT("United Kingdom", "GBR", "GB", "Scotland", "SCT"),
    GE("Georgia", "GEO", "GE"),
    GG("Guernsey", "GGY", "GG"),
    GH("Ghana", "GHA", "GH"),
    GI("Gibraltar", "GIB", "GI"),
    GN("Guinea", "GIN", "GN"),
    GM("The Gambia", "GMB", "GM"),
    GW("Guinea-Bissau", "GNB", "GW"),
    GQ("Equatorial Guinea", "GNQ", "GQ"),
    GR("Greece", "GRC", "GR"),
    GD("Grenada", "GRD", "GD"),
    GL("Greenland", "GRL", "GL"),
    GT("Guatemala", "GTM", "GT"),
    GY("Guyana", "GUY", "GY"),
    HN("Honduras", "HND", "HN"),
    HR("Croatia", "HRV", "HR"),
    HT("Haiti", "HTI", "HT"),
    HU("Hungary", "HUN", "HU"),
    ID("Indonesia", "IDN", "ID"),
    IM("Isle of Man", "IMN", "IM"),
    IN("India", "IND", "IN"),
    IO("British Indian Ocean Territory", "IOT", "IO"),
    IE("Ireland", "IRL", "IE"),
    IR("Iran", "IRN", "IR"),
    IQ("Iraq", "IRQ", "IQ"),
    IS("Iceland", "ISL", "IS"),
    IL("Israel", "ISR", "IL"),
    IT("Italy", "ITA", "IT"),
    JM("Jamaica", "JAM", "JM"),
    JE("Jersey", "JEY", "JE"),
    JO("Jordan", "JOR", "JO"),
    JP("Japan", "JPN", "JP"),
    KZ("Kazakhstan", "KAZ", "KZ"),
    KE("Kenya", "KEN", "KE"),
    KG("Kyrgyzstan", "KGZ", "KG"),
    KH("Cambodia", "KHM", "KH"),
    KI("Kiribati", "KIR", "KI"),
    KN("Saint Kitts and Nevis", "KNA", "KN"),
    KR("South Korea", "KOR", "KR"),
    KW("Kuwait", "KWT", "KW"),
    LA("Laos", "LAO", "LA"),
    LB("Lebanon", "LBN", "LB"),
    LR("Liberia", "LBR", "LR"),
    LY("Libya", "LBY", "LY"),
    LC("Saint Lucia", "LCA", "LC"),
    LI("Liechtenstein", "LIE", "LI"),
    LK("Sri Lanka", "LKA", "LK"),
    LS("Lesotho", "LSO", "LS"),
    LT("Lithuania", "LTU", "LT"),
    LU("Luxembourg", "LUX", "LU"),
    LV("Latvia", "LVA", "LV"),
    MA("Morocco", "MAR", "MA"),
    MC("Monaco", "MCO", "MC"),
    MD("Moldova", "MDA", "MD"),
    MG("Madagascar", "MDG", "MG"),
    MV("Maldives", "MDV", "MV"),
    MX("Mexico", "MEX", "MX"),
    MH("Marshall Islands", "MHL", "MH"),
    MK("North Macedonia", "MKD", "MK"),
    ML("Mali", "MLI", "ML"),
    MT("Malta", "MLT", "MT"),
    MM("Myanmar", "MMR", "MM"),
    ME("Montenegro", "MNE", "ME"),
    MN("Mongolia", "MNG", "MN"),
    MZ("Mozambique", "MOZ", "MZ"),
    MR("Mauritania", "MRT", "MR"),
    MS("Montserrat", "MSR", "MS"),
    MU("Mauritius", "MUS", "MU"),
    MW("Malawi", "MWI", "MW"),
    MY("Malaysia", "MYS", "MY"),
    NA("Namibia", "NAM", "NA"),
    NE("Niger", "NER", "NE"),
    NG("Nigeria", "NGA", "NG"),
    NI("Nicaragua", "NIC", "NI"),
    NU("Niue", "NIU", "NU"),
    NL("Netherlands", "NLD", "NL"),
    NL_BQ1("Netherlands", "NLD", "NL", "Bonaire", "BQ1"),
    NL_BQ2("Netherlands", "NLD", "NL", "Saba", "BQ2"),
    NL_BQ3("Netherlands", "NLD", "NL", "Sint Eustatius", "BQ3"),
    NO("Norway", "NOR", "NO"),
    NP("Nepal", "NPL", "NP"),
    NR("Nauru", "NRU", "NR"),
    NZ("New Zealand", "NZL", "NZ"),
    OM("Oman", "OMN", "OM"),
    PK("Pakistan", "PAK", "PK"),
    PA("Panama", "PAN", "PA"),
    PN("Pitcairn Islands", "PCN", "PN"),
    PE("Peru", "PER", "PE"),
    PH("Philippines", "PHL", "PH"),
    PW("Palau", "PLW", "PW"),
    PG("Papua New Guinea", "PNG", "PG"),
    PL("Poland", "POL", "PL"),
    KP("North Korea", "PRK", "KP"),
    PT("Portugal", "PRT", "PT"),
    PY("Paraguay", "PRY", "PY"),
    PS("Palestinian Territories", "PSE", "PS"),
    QA("Qatar", "QAT", "QA"),
    RO("Romania", "ROU", "RO"),
    RU("Russia", "RUS", "RU"),
    RW("Rwanda", "RWA", "RW"),
    SA("Saudi Arabia", "SAU", "SA"),
    SD("Sudan", "SDN", "SD"),
    SN("Senegal", "SEN", "SN"),
    SG("Singapore", "SGP", "SG"),
    GS("South Georgia and the South Sandwich Islands", "SGS", "GS"),
    SH("Saint Helena, Ascension and Tristan da Cunha", "SHN", "SH"),
    SB("Solomon Islands", "SLB", "SB"),
    SL("Sierra Leone", "SLE", "SL"),
    SV("El Salvador", "SLV", "SV"),
    SM("San Marino", "SMR", "SM"),
    SO("Somalia", "SOM", "SO"),
    RS("Serbia", "SRB", "RS"),
    SS("South Sudan", "SSD", "SS"),
    ST("São Tomé and Príncipe", "STP", "ST"),
    SR("Suriname", "SUR", "SR"),
    SK("Slovakia", "SVK", "SK"),
    SI("Slovenia", "SVN", "SI"),
    SE("Sweden", "SWE", "SE"),
    SZ("Eswatini", "SWZ", "SZ"),
    SC("Seychelles", "SYC", "SC"),
    SY("Syria", "SYR", "SY"),
    TC("Turks and Caicos Islands", "TCA", "TC"),
    TD("Chad", "TCD", "TD"),
    TG("Togo", "TGO", "TG"),
    TH("Thailand", "THA", "TH"),
    TJ("Tajikistan", "TJK", "TJ"),
    TK("Tokelau", "TKL", "TK"),
    TM("Turkmenistan", "TKM", "TM"),
    TL("East Timor", "TLS", "TL"),
    TO("Tonga", "TON", "TO"),
    TT("Trinidad and Tobago", "TTO", "TT"),
    TN("Tunisia", "TUN", "TN"),
    TR("Turkey", "TUR", "TR"),
    TV("Tuvalu", "TUV", "TV"),
    TW("Taiwan", "TWN", "TW"),
    TZ("Tanzania", "TZA", "TZ"),
    UG("Uganda", "UGA", "UG"),
    UA("Ukraine", "UKR", "UA"),
    UY("Uruguay", "URY", "UY"),
    US("United States", "USA", "US"),
    US_AL("United States", "USA", "US", "Alabama", "AL"),
    US_AK("United States", "USA", "US", "Alaska", "AK"),
    US_AZ("United States", "USA", "US", "Arizona", "AZ"),
    US_AR("United States", "USA", "US", "Arkansas", "AR"),
    US_CA("United States", "USA", "US", "California", "CA"),
    US_CO("United States", "USA", "US", "Colorado", "CO"),
    US_CT("United States", "USA", "US", "Connecticut", "CT"),
    US_DE("United States", "USA", "US", "Delaware", "DE"),
    US_DC("United States", "USA", "US", "District of Columbia", "DC"), // is a federal district not a state
    US_FL("United States", "USA", "US", "Floria", "FL"),
    US_GA("United States", "USA", "US", "Georgia", "GA"),
    US_HI("United States", "USA", "US", "Hawaii", "HI"),
    US_ID("United States", "USA", "US", "Idaho", "ID"),
    US_IL("United States", "USA", "US", "Illinois", "IL"),
    US_IN("United States", "USA", "US", "Indiana", "IN"),
    US_IA("United States", "USA", "US", "Iowa", "IA"),
    US_KS("United States", "USA", "US", "Kansas", "KS"),
    US_KY("United States", "USA", "US", "Kentucky", "KY"),
    US_LA("United States", "USA", "US", "Louisiana", "LA"),
    US_ME("United States", "USA", "US", "Maine", "ME"),
    US_MD("United States", "USA", "US", "Maryland", "MD"),
    US_MA("United States", "USA", "US", "Massachusetts", "MA"),
    US_MI("United States", "USA", "US", "Michigan", "MI"),
    US_MN("United States", "USA", "US", "Minnesota", "MN"),
    US_MS("United States", "USA", "US", "Mississippi", "MS"),
    US_MO("United States", "USA", "US", "Missouri", "MO"),
    US_MT("United States", "USA", "US", "Montana", "MT"),
    US_NE("United States", "USA", "US", "Nebraska", "NE"),
    US_NV("United States", "USA", "US", "Nevada", "NV"),
    US_NH("United States", "USA", "US", "New Hampshire", "NH"),
    US_NJ("United States", "USA", "US", "New Jersey", "NJ"),
    US_NM("United States", "USA", "US", "New Mexico", "NM"),
    US_NY("United States", "USA", "US", "New York", "NY"),
    US_NC("United States", "USA", "US", "North Carolina", "NC"),
    US_ND("United States", "USA", "US", "North Dakota", "ND"),
    US_OH("United States", "USA", "US", "Ohio", "OH"),
    US_OK("United States", "USA", "US", "Oklahoma", "OK"),
    US_OR("United States", "USA", "US", "Oregon", "OR"),
    US_PA("United States", "USA", "US", "Pennsylvania", "PA"),
    US_RI("United States", "USA", "US", "Rhode Island", "RI"),
    US_SC("United States", "USA", "US", "South Carolina", "SC"),
    US_SD("United States", "USA", "US", "South Dakota", "SD"),
    US_TN("United States", "USA", "US", "Tennessee", "TN"),
    US_TX("United States", "USA", "US", "Texas", "TX"),
    US_UT("United States", "USA", "US", "Utah", "UT"),
    US_VT("United States", "USA", "US", "Vermont", "VT"),
    US_VA("United States", "USA", "US", "Virginia", "VA"),
    US_WA("United States", "USA", "US", "Washington", "WA"),
    US_WV("United States", "USA", "US", "West Virginia", "WV"),
    US_WI("United States", "USA", "US", "Wisconsin", "WI"),
    US_WY("United States", "USA", "US", "Wyoming", "WY"),
    UZ("Uzbekistan", "UZB", "UZ"),
    VA("Vatican City", "VAT", "VA"),
    VC("Saint Vincent and the Grenadines", "VCT", "VC"),
    VE("Venezuela", "VEN", "VE"),
    VG("British Virgin Islands", "VGB", "VG"),
    VN("Vietnam", "VNM", "VN"),
    VU("Vanuatu", "VUT", "VU"),
    WS("Samoa", "WSM", "WS"),
    XK("Kosovo", "XKX", "XK"),
    YE("Yemen", "YEM", "YE"),
    ZA("South Africa", "ZAF", "ZA"),
    ZM("Zambia", "ZMB", "ZM"),
    ZW("Zimbabwe", "ZWE", "ZW");

    public static final String KEY = "country", ISO_3166_2 = "ISO3166-2";

    private final String countryName;
    // ISO 3166-1
    private final String alpha2;
    // ISO 3166-1 alpha3
    private final String alpha3;
    // ISO 3166-2 code
    private final String subdivisionCode;
    private final String subdivisionName;

    Country(String countryName, String alpha3, String alpha2) {
        this.countryName = countryName;
        this.alpha2 = alpha2;
        this.alpha3 = alpha3;
        this.subdivisionCode = "";
        this.subdivisionName = "";
    }

    Country(String countryName, String alpha3, String alpha2, String subdivisionName, String subdivisionCode) {
        this.countryName = countryName;
        this.alpha2 = alpha2;
        this.alpha3 = alpha3;
        this.subdivisionCode = subdivisionCode;
        this.subdivisionName = subdivisionName;
    }

    /**
     * @return the name of this country. Avoids clash with name() method of this enum.
     */
    public String getCountryName() {
        return countryName;
    }

    /**
     * @return the ISO3166-1:alpha2 code of this country
     */
    public String getAlpha2() {
        return alpha2;
    }

    /**
     * e.g. US-WY
     */
    public String getAlpha2AndSubdivision() {
        return alpha2 + (hasSubdivision() ? "-" + getSubdivisionCode() : "");
    }

    public boolean hasSubdivision() {
        return !subdivisionCode.isEmpty();
    }

    public String getSubdivisionCode() {
        return subdivisionCode;
    }

    public String getSubdivisionName() {
        return subdivisionName;
    }

    public static EnumEncodedValue<Country> create() {
        return new EnumEncodedValue<>(Country.KEY, Country.class);
    }

    @Override
    public String toString() {
        return alpha3; // for background compatibility
    }
}
