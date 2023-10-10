/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.routing.ev.State.*;

/**
 * The enum constants correspond to the ISO3166-1:alpha3 code of the corresponding country.
 */
public enum Country {

    MISSING("missing", "---", "--"),
    AFG("Afghanistan", "AFG", "AF"),
    AGO("Angola", "AGO", "AO"),
    AIA("Anguilla", "AIA", "AI"),
    ALB("Albania", "ALB", "AL"),
    AND("Andorra", "AND", "AD"),
    ARE("United Arab Emirates", "ARE", "AE"),
    ARG("Argentina", "ARG", "AR"),
    ARM("Armenia", "ARM", "AM"),
    ATG("Antigua and Barbuda", "ATG", "AG"),
    AUS("Australia", "AUS", "AU", AU_ACT, AU_NSW, AU_NT, AU_QLD, AU_SA, AU_TAS, AU_VIC, AU_WA),
    AUT("Austria", "AUT", "AT"),
    AZE("Azerbaijan", "AZE", "AZ"),
    BDI("Burundi", "BDI", "BI"),
    BEL("Belgium", "BEL", "BE"),
    BEN("Benin", "BEN", "BJ"),
    BFA("Burkina Faso", "BFA", "BF"),
    BGD("Bangladesh", "BGD", "BD"),
    BGR("Bulgaria", "BGR", "BG"),
    BHR("Bahrain", "BHR", "BH"),
    BHS("The Bahamas", "BHS", "BS"),
    BIH("Bosnia and Herzegovina", "BIH", "BA"),
    BLR("Belarus", "BLR", "BY"),
    BLZ("Belize", "BLZ", "BZ"),
    BMU("Bermuda", "BMU", "BM"),
    BOL("Bolivia", "BOL", "BO"),
    BRA("Brazil", "BRA", "BR"),
    BRB("Barbados", "BRB", "BB"),
    BRN("Brunei", "BRN", "BN"),
    BTN("Bhutan", "BTN", "BT"),
    BWA("Botswana", "BWA", "BW"),
    CAF("Central African Republic", "CAF", "CF"),
    CAN("Canada", "CAN", "CA", CA_AB, CA_BC, CA_MB, CA_NB, CA_NL, CA_NS, CA_NT, CA_NU, CA_ON, CA_PE, CA_QC, CA_SK, CA_YT),
    CHE("Switzerland", "CHE", "CH"),
    CHL("Chile", "CHL", "CL"),
    CHN("China", "CHN", "CN"),
    CIV("Côte d'Ivoire", "CIV", "CI"),
    CMR("Cameroon", "CMR", "CM"),
    COD("Democratic Republic of the Congo", "COD", "CD"),
    COG("Congo-Brazzaville", "COG", "CG"),
    COK("Cook Islands", "COK", "CK"),
    COL("Colombia", "COL", "CO"),
    COM("Comoros", "COM", "KM"),
    CPV("Cape Verde", "CPV", "CV"),
    CRI("Costa Rica", "CRI", "CR"),
    CUB("Cuba", "CUB", "CU"),
    CYM("Cayman Islands", "CYM", "KY"),
    CYP("Cyprus", "CYP", "CY"),
    CZE("Czechia", "CZE", "CZ"),
    DEU("Germany", "DEU", "DE"),
    DJI("Djibouti", "DJI", "DJ"),
    DMA("Dominica", "DMA", "DM"),
    DNK("Denmark", "DNK", "DK"),
    DOM("Dominican Republic", "DOM", "DO"),
    DZA("Algeria", "DZA", "DZ"),
    ECU("Ecuador", "ECU", "EC"),
    EGY("Egypt", "EGY", "EG"),
    ERI("Eritrea", "ERI", "ER"),
    ESP("Spain", "ESP", "ES"),
    EST("Estonia", "EST", "EE"),
    ETH("Ethiopia", "ETH", "ET"),
    FIN("Finland", "FIN", "FI"),
    FJI("Fiji", "FJI", "FJ"),
    FLK("Falkland Islands", "FLK", "FK"),
    FRA("France", "FRA", "FR"),
    FRO("Faroe Islands", "FRO", "FO"),
    FSM("Federated States of Micronesia", "FSM", "FM", FM_KSA, FM_PNI, FM_TRK, FM_YAP),
    GAB("Gabon", "GAB", "GA"),
    GBR("United Kingdom", "GBR", "GB"),
    GEO("Georgia", "GEO", "GE"),
    GGY("Guernsey", "GGY", "GG"),
    GHA("Ghana", "GHA", "GH"),
    GIB("Gibraltar", "GIB", "GI"),
    GIN("Guinea", "GIN", "GN"),
    GMB("The Gambia", "GMB", "GM"),
    GNB("Guinea-Bissau", "GNB", "GW"),
    GNQ("Equatorial Guinea", "GNQ", "GQ"),
    GRC("Greece", "GRC", "GR"),
    GRD("Grenada", "GRD", "GD"),
    GRL("Greenland", "GRL", "GL"),
    GTM("Guatemala", "GTM", "GT"),
    GUY("Guyana", "GUY", "GY"),
    HND("Honduras", "HND", "HN"),
    HRV("Croatia", "HRV", "HR"),
    HTI("Haiti", "HTI", "HT"),
    HUN("Hungary", "HUN", "HU"),
    IDN("Indonesia", "IDN", "ID"),
    IMN("Isle of Man", "IMN", "IM"),
    IND("India", "IND", "IN"),
    IOT("British Indian Ocean Territory", "IOT", "IO"),
    IRL("Ireland", "IRL", "IE"),
    IRN("Iran", "IRN", "IR"),
    IRQ("Iraq", "IRQ", "IQ"),
    ISL("Iceland", "ISL", "IS"),
    ISR("Israel", "ISR", "IL"),
    ITA("Italy", "ITA", "IT"),
    JAM("Jamaica", "JAM", "JM"),
    JEY("Jersey", "JEY", "JE"),
    JOR("Jordan", "JOR", "JO"),
    JPN("Japan", "JPN", "JP"),
    KAZ("Kazakhstan", "KAZ", "KZ"),
    KEN("Kenya", "KEN", "KE"),
    KGZ("Kyrgyzstan", "KGZ", "KG"),
    KHM("Cambodia", "KHM", "KH"),
    KIR("Kiribati", "KIR", "KI"),
    KNA("Saint Kitts and Nevis", "KNA", "KN"),
    KOR("South Korea", "KOR", "KR"),
    KWT("Kuwait", "KWT", "KW"),
    LAO("Laos", "LAO", "LA"),
    LBN("Lebanon", "LBN", "LB"),
    LBR("Liberia", "LBR", "LR"),
    LBY("Libya", "LBY", "LY"),
    LCA("Saint Lucia", "LCA", "LC"),
    LIE("Liechtenstein", "LIE", "LI"),
    LKA("Sri Lanka", "LKA", "LK"),
    LSO("Lesotho", "LSO", "LS"),
    LTU("Lithuania", "LTU", "LT"),
    LUX("Luxembourg", "LUX", "LU"),
    LVA("Latvia", "LVA", "LV"),
    MAR("Morocco", "MAR", "MA"),
    MCO("Monaco", "MCO", "MC"),
    MDA("Moldova", "MDA", "MD"),
    MDG("Madagascar", "MDG", "MG"),
    MDV("Maldives", "MDV", "MV"),
    MEX("Mexico", "MEX", "MX"),
    MHL("Marshall Islands", "MHL", "MH"),
    MKD("North Macedonia", "MKD", "MK"),
    MLI("Mali", "MLI", "ML"),
    MLT("Malta", "MLT", "MT"),
    MMR("Myanmar", "MMR", "MM"),
    MNE("Montenegro", "MNE", "ME"),
    MNG("Mongolia", "MNG", "MN"),
    MOZ("Mozambique", "MOZ", "MZ"),
    MRT("Mauritania", "MRT", "MR"),
    MSR("Montserrat", "MSR", "MS"),
    MUS("Mauritius", "MUS", "MU"),
    MWI("Malawi", "MWI", "MW"),
    MYS("Malaysia", "MYS", "MY"),
    NAM("Namibia", "NAM", "NA"),
    NER("Niger", "NER", "NE"),
    NGA("Nigeria", "NGA", "NG"),
    NIC("Nicaragua", "NIC", "NI"),
    NIU("Niue", "NIU", "NU"),
    NLD("Netherlands", "NLD", "NL"),
    NOR("Norway", "NOR", "NO"),
    NPL("Nepal", "NPL", "NP"),
    NRU("Nauru", "NRU", "NR"),
    NZL("New Zealand", "NZL", "NZ"),
    OMN("Oman", "OMN", "OM"),
    PAK("Pakistan", "PAK", "PK"),
    PAN("Panama", "PAN", "PA"),
    PCN("Pitcairn Islands", "PCN", "PN"),
    PER("Peru", "PER", "PE"),
    PHL("Philippines", "PHL", "PH"),
    PLW("Palau", "PLW", "PW"),
    PNG("Papua New Guinea", "PNG", "PG"),
    POL("Poland", "POL", "PL"),
    PRK("North Korea", "PRK", "KP"),
    PRT("Portugal", "PRT", "PT"),
    PRY("Paraguay", "PRY", "PY"),
    PSE("Palestinian Territories", "PSE", "PS"),
    QAT("Qatar", "QAT", "QA"),
    ROU("Romania", "ROU", "RO"),
    RUS("Russia", "RUS", "RU"),
    RWA("Rwanda", "RWA", "RW"),
    SAU("Saudi Arabia", "SAU", "SA"),
    SDN("Sudan", "SDN", "SD"),
    SEN("Senegal", "SEN", "SN"),
    SGP("Singapore", "SGP", "SG"),
    SGS("South Georgia and the South Sandwich Islands", "SGS", "GS"),
    SHN("Saint Helena, Ascension and Tristan da Cunha", "SHN", "SH"),
    SLB("Solomon Islands", "SLB", "SB"),
    SLE("Sierra Leone", "SLE", "SL"),
    SLV("El Salvador", "SLV", "SV"),
    SMR("San Marino", "SMR", "SM"),
    SOM("Somalia", "SOM", "SO"),
    SRB("Serbia", "SRB", "RS"),
    SSD("South Sudan", "SSD", "SS"),
    STP("São Tomé and Príncipe", "STP", "ST"),
    SUR("Suriname", "SUR", "SR"),
    SVK("Slovakia", "SVK", "SK"),
    SVN("Slovenia", "SVN", "SI"),
    SWE("Sweden", "SWE", "SE"),
    SWZ("Eswatini", "SWZ", "SZ"),
    SYC("Seychelles", "SYC", "SC"),
    SYR("Syria", "SYR", "SY"),
    TCA("Turks and Caicos Islands", "TCA", "TC"),
    TCD("Chad", "TCD", "TD"),
    TGO("Togo", "TGO", "TG"),
    THA("Thailand", "THA", "TH"),
    TJK("Tajikistan", "TJK", "TJ"),
    TKL("Tokelau", "TKL", "TK"),
    TKM("Turkmenistan", "TKM", "TM"),
    TLS("East Timor", "TLS", "TL"),
    TON("Tonga", "TON", "TO"),
    TTO("Trinidad and Tobago", "TTO", "TT"),
    TUN("Tunisia", "TUN", "TN"),
    TUR("Turkey", "TUR", "TR"),
    TUV("Tuvalu", "TUV", "TV"),
    TWN("Taiwan", "TWN", "TW"),
    TZA("Tanzania", "TZA", "TZ"),
    UGA("Uganda", "UGA", "UG"),
    UKR("Ukraine", "UKR", "UA"),
    URY("Uruguay", "URY", "UY"),
    USA("United States", "USA", "US",
            US_AL, US_AK, US_AZ, US_AR, US_CA, US_CO, US_CT, US_DE, US_DC, US_FL,
            US_GA, US_HI, US_ID, US_IL, US_IN, US_IA, US_KS, US_KY, US_LA, US_ME,
            US_MD, US_MA, US_MI, US_MN, US_MS, US_MO, US_MT, US_NE, US_NV, US_NH,
            US_NJ, US_NM, US_NY, US_NC, US_ND, US_OH, US_OK, US_OR, US_PA, US_RI,
            US_SC, US_SD, US_TN, US_TX, US_UT, US_VT, US_VA, US_WA, US_WV, US_WI, US_WY),
    UZB("Uzbekistan", "UZB", "UZ"),
    VAT("Vatican City", "VAT", "VA"),
    VCT("Saint Vincent and the Grenadines", "VCT", "VC"),
    VEN("Venezuela", "VEN", "VE"),
    VGB("British Virgin Islands", "VGB", "VG"),
    VNM("Vietnam", "VNM", "VN"),
    VUT("Vanuatu", "VUT", "VU"),
    WSM("Samoa", "WSM", "WS"),
    XKX("Kosovo", "XKX", "XK"),
    YEM("Yemen", "YEM", "YE"),
    ZAF("South Africa", "ZAF", "ZA"),
    ZMB("Zambia", "ZMB", "ZM"),
    ZWE("Zimbabwe", "ZWE", "ZW");

    public static final String KEY = "country";

    private static final Map<String, Country> ALPHA2_MAP = new HashMap<>();

    static {
        for (Country country : values()) {
            if (country == MISSING)
                continue;

            ALPHA2_MAP.put(country.alpha2, country);
        }
    }

    private final String countryName;
    private final String alpha2;
    // ISO 3166-1 alpha3
    private final String alpha3;
    private final List<State> states;

    Country(String countryName, String alpha3, String alpha2, State... states) {
        this.countryName = countryName;
        this.alpha2 = alpha2;
        this.alpha3 = alpha3;
        this.states = Arrays.asList(states);
    }

    /**
     * @return the name of this country. Avoids clash with name() method of this enum.
     */
    public String getCountryName() {
        return countryName;
    }

    /**
     * @return the ISO 3166-1:alpha2 code of this country
     */
    public String getAlpha2() {
        return alpha2;
    }

    public String getAlpha3() {
        return alpha3;
    }

    public List<State> getStates() {
        return states;
    }

    public static EnumEncodedValue<Country> create() {
        return new EnumEncodedValue<>(Country.KEY, Country.class);
    }

    /**
     * @param iso should be ISO 3166-1 alpha-2
     */
    public static Country find(String iso) {
        return ALPHA2_MAP.get(iso);
    }

    @Override
    public String toString() {
        return alpha3; // for background compatibility
    }
}
