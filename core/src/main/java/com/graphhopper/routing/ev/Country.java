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

/**
 * The enum constants correspond to the the ISO3166-1:alpha3 code of the corresponding country
 */
public enum Country {

    // ISO3166-1:alpha3(name:en, ISO3166-1:alpha2)
    MISSING("missing", "--"),
    AFG("Afghanistan", "AF"),
    AGO("Angola", "AO"),
    AIA("Anguilla", "AI"),
    ALB("Albania", "AL"),
    AND("Andorra", "AD"),
    ARE("United Arab Emirates", "AE"),
    ARG("Argentina", "AR"),
    ARM("Armenia", "AM"),
    ATG("Antigua and Barbuda", "AG"),
    AUS("Australia", "AU"),
    AUT("Austria", "AT"),
    AZE("Azerbaijan", "AZ"),
    BDI("Burundi", "BI"),
    BEL("Belgium", "BE"),
    BEN("Benin", "BJ"),
    BFA("Burkina Faso", "BF"),
    BGD("Bangladesh", "BD"),
    BGR("Bulgaria", "BG"),
    BHR("Bahrain", "BH"),
    BHS("The Bahamas", "BS"),
    BIH("Bosnia and Herzegovina", "BA"),
    BLR("Belarus", "BY"),
    BLZ("Belize", "BZ"),
    BMU("Bermuda", "BM"),
    BOL("Bolivia", "BO"),
    BRA("Brazil", "BR"),
    BRB("Barbados", "BB"),
    BRN("Brunei", "BN"),
    BTN("Bhutan", "BT"),
    BWA("Botswana", "BW"),
    CAF("Central African Republic", "CF"),
    CAN("Canada", "CA"),
    CHE("Switzerland", "CH"),
    CHL("Chile", "CL"),
    CHN("China", "CN"),
    CIV("Côte d'Ivoire", "CI"),
    CMR("Cameroon", "CM"),
    COD("Democratic Republic of the Congo", "CD"),
    COG("Congo-Brazzaville", "CG"),
    COK("Cook Islands", "CK"),
    COL("Colombia", "CO"),
    COM("Comoros", "KM"),
    CPV("Cape Verde", "CV"),
    CRI("Costa Rica", "CR"),
    CUB("Cuba", "CU"),
    CYM("Cayman Islands", "KY"),
    CYP("Cyprus", "CY"),
    CZE("Czechia", "CZ"),
    DEU("Germany", "DE"),
    DJI("Djibouti", "DJ"),
    DMA("Dominica", "DM"),
    DNK("Denmark", "DK"),
    DOM("Dominican Republic", "DO"),
    DZA("Algeria", "DZ"),
    ECU("Ecuador", "EC"),
    EGY("Egypt", "EG"),
    ERI("Eritrea", "ER"),
    ESP("Spain", "ES"),
    EST("Estonia", "EE"),
    ETH("Ethiopia", "ET"),
    FIN("Finland", "FI"),
    FJI("Fiji", "FJ"),
    FLK("Falkland Islands", "FK"),
    FRA("France", "FR"),
    FRO("Faroe Islands", "FO"),
    FSM("Federated States of Micronesia", "FM"),
    GAB("Gabon", "GA"),
    GBR("United Kingdom", "GB"),
    GEO("Georgia", "GE"),
    GGY("Guernsey", "GG"),
    GHA("Ghana", "GH"),
    GIB("Gibraltar", "GI"),
    GIN("Guinea", "GN"),
    GMB("The Gambia", "GM"),
    GNB("Guinea-Bissau", "GW"),
    GNQ("Equatorial Guinea", "GQ"),
    GRC("Greece", "GR"),
    GRD("Grenada", "GD"),
    GRL("Greenland", "GL"),
    GTM("Guatemala", "GT"),
    GUY("Guyana", "GY"),
    HND("Honduras", "HN"),
    HRV("Croatia", "HR"),
    HTI("Haiti", "HT"),
    HUN("Hungary", "HU"),
    IDN("Indonesia", "ID"),
    IMN("Isle of Man", "IM"),
    IND("India", "IN"),
    IOT("British Indian Ocean Territory", "IO"),
    IRL("Ireland", "IE"),
    IRN("Iran", "IR"),
    IRQ("Iraq", "IQ"),
    ISL("Iceland", "IS"),
    ISR("Israel", "IL"),
    ITA("Italy", "IT"),
    JAM("Jamaica", "JM"),
    JEY("Jersey", "JE"),
    JOR("Jordan", "JO"),
    JPN("Japan", "JP"),
    KAZ("Kazakhstan", "KZ"),
    KEN("Kenya", "KE"),
    KGZ("Kyrgyzstan", "KG"),
    KHM("Cambodia", "KH"),
    KIR("Kiribati", "KI"),
    KNA("Saint Kitts and Nevis", "KN"),
    KOR("South Korea", "KR"),
    KWT("Kuwait", "KW"),
    LAO("Laos", "LA"),
    LBN("Lebanon", "LB"),
    LBR("Liberia", "LR"),
    LBY("Libya", "LY"),
    LCA("Saint Lucia", "LC"),
    LIE("Liechtenstein", "LI"),
    LKA("Sri Lanka", "LK"),
    LSO("Lesotho", "LS"),
    LTU("Lithuania", "LT"),
    LUX("Luxembourg", "LU"),
    LVA("Latvia", "LV"),
    MAR("Morocco", "MA"),
    MCO("Monaco", "MC"),
    MDA("Moldova", "MD"),
    MDG("Madagascar", "MG"),
    MDV("Maldives", "MV"),
    MEX("Mexico", "MX"),
    MHL("Marshall Islands", "MH"),
    MKD("North Macedonia", "MK"),
    MLI("Mali", "ML"),
    MLT("Malta", "MT"),
    MMR("Myanmar", "MM"),
    MNE("Montenegro", "ME"),
    MNG("Mongolia", "MN"),
    MOZ("Mozambique", "MZ"),
    MRT("Mauritania", "MR"),
    MSR("Montserrat", "MS"),
    MUS("Mauritius", "MU"),
    MWI("Malawi", "MW"),
    MYS("Malaysia", "MY"),
    NAM("Namibia", "NA"),
    NER("Niger", "NE"),
    NGA("Nigeria", "NG"),
    NIC("Nicaragua", "NI"),
    NIU("Niue", "NU"),
    NLD("Netherlands", "NL"),
    NOR("Norway", "NO"),
    NPL("Nepal", "NP"),
    NRU("Nauru", "NR"),
    NZL("New Zealand", "NZ"),
    OMN("Oman", "OM"),
    PAK("Pakistan", "PK"),
    PAN("Panama", "PA"),
    PCN("Pitcairn Islands", "PN"),
    PER("Peru", "PE"),
    PHL("Philippines", "PH"),
    PLW("Palau", "PW"),
    PNG("Papua New Guinea", "PG"),
    POL("Poland", "PL"),
    PRK("North Korea", "KP"),
    PRT("Portugal", "PT"),
    PRY("Paraguay", "PY"),
    PSE("Palestinian Territories", "PS"),
    QAT("Qatar", "QA"),
    ROU("Romania", "RO"),
    RUS("Russia", "RU"),
    RWA("Rwanda", "RW"),
    SAU("Saudi Arabia", "SA"),
    SDN("Sudan", "SD"),
    SEN("Senegal", "SN"),
    SGP("Singapore", "SG"),
    SGS("South Georgia and the South Sandwich Islands", "GS"),
    SHN("Saint Helena, Ascension and Tristan da Cunha", "SH"),
    SLB("Solomon Islands", "SB"),
    SLE("Sierra Leone", "SL"),
    SLV("El Salvador", "SV"),
    SMR("San Marino", "SM"),
    SOM("Somalia", "SO"),
    SRB("Serbia", "RS"),
    SSD("South Sudan", "SS"),
    STP("São Tomé and Príncipe", "ST"),
    SUR("Suriname", "SR"),
    SVK("Slovakia", "SK"),
    SVN("Slovenia", "SI"),
    SWE("Sweden", "SE"),
    SWZ("Eswatini", "SZ"),
    SYC("Seychelles", "SC"),
    SYR("Syria", "SY"),
    TCA("Turks and Caicos Islands", "TC"),
    TCD("Chad", "TD"),
    TGO("Togo", "TG"),
    THA("Thailand", "TH"),
    TJK("Tajikistan", "TJ"),
    TKL("Tokelau", "TK"),
    TKM("Turkmenistan", "TM"),
    TLS("East Timor", "TL"),
    TON("Tonga", "TO"),
    TTO("Trinidad and Tobago", "TT"),
    TUN("Tunisia", "TN"),
    TUR("Turkey", "TR"),
    TUV("Tuvalu", "TV"),
    TWN("Taiwan", "TW"),
    TZA("Tanzania", "TZ"),
    UGA("Uganda", "UG"),
    UKR("Ukraine", "UA"),
    URY("Uruguay", "UY"),
    USA("United States", "US"),
    UZB("Uzbekistan", "UZ"),
    VAT("Vatican City", "VA"),
    VCT("Saint Vincent and the Grenadines", "VC"),
    VEN("Venezuela", "VE"),
    VGB("British Virgin Islands", "VG"),
    VNM("Vietnam", "VN"),
    VUT("Vanuatu", "VU"),
    WSM("Samoa", "WS"),
    XKX("Kosovo", "XK"),
    YEM("Yemen", "YE"),
    ZAF("South Africa", "ZA"),
    ZMB("Zambia", "ZM"),
    ZWE("Zimbabwe", "ZW");

    public static final String KEY = "country", ISO_ALPHA3 = "ISO3166-1:alpha3";

    private final String countryName;
    private final String alpha2;

    Country(String countryName, String alpha2) {
        this.countryName = countryName;
        this.alpha2 = alpha2;
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

    public static EnumEncodedValue<Country> create() {
        return new EnumEncodedValue<>(Country.KEY, Country.class);
    }

    // for backward compatibility: no custom toString()
}
