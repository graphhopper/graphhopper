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

    MISSING("missing", "---", "--", true),
    AFG("Afghanistan", "AFG", "AF", true),
    AGO("Angola", "AGO", "AO", true),
    AIA("Anguilla", "AIA", "AI", false),
    ALB("Albania", "ALB", "AL", true),
    AND("Andorra", "AND", "AD", true),
    ARE("United Arab Emirates", "ARE", "AE", true),
    ARG("Argentina", "ARG", "AR", true),
    ARM("Armenia", "ARM", "AM", true),
    ATG("Antigua and Barbuda", "ATG", "AG", false),
    AUS("Australia", "AUS", "AU", false, AU_ACT, AU_NSW, AU_NT, AU_QLD, AU_SA, AU_TAS, AU_VIC, AU_WA),
    AUT("Austria", "AUT", "AT", true),
    AZE("Azerbaijan", "AZE", "AZ", true),
    BDI("Burundi", "BDI", "BI", true),
    BEL("Belgium", "BEL", "BE", true),
    BEN("Benin", "BEN", "BJ", true),
    BFA("Burkina Faso", "BFA", "BF", true),
    BGD("Bangladesh", "BGD", "BD", true),
    BGR("Bulgaria", "BGR", "BG", true),
    BHR("Bahrain", "BHR", "BH", true),
    BHS("The Bahamas", "BHS", "BS", false),
    BIH("Bosnia and Herzegovina", "BIH", "BA", true),
    BLR("Belarus", "BLR", "BY", true),
    BLZ("Belize", "BLZ", "BZ", true),
    BMU("Bermuda", "BMU", "BM", true),
    BOL("Bolivia", "BOL", "BO", true),
    BRA("Brazil", "BRA", "BR", true),
    BRB("Barbados", "BRB", "BB", false),
    BRN("Brunei", "BRN", "BN", true),
    BTN("Bhutan", "BTN", "BT", false),
    BWA("Botswana", "BWA", "BW", false),
    CAF("Central African Republic", "CAF", "CF", true),
    CAN("Canada", "CAN", "CA", true, CA_AB, CA_BC, CA_MB, CA_NB, CA_NL, CA_NS, CA_NT, CA_NU, CA_ON, CA_PE, CA_QC, CA_SK, CA_YT),
    CHE("Switzerland", "CHE", "CH", true),
    CHL("Chile", "CHL", "CL", true),
    CHN("China", "CHN", "CN", true),
    CIV("Côte d'Ivoire", "CIV", "CI", true),
    CMR("Cameroon", "CMR", "CM", true),
    COD("Democratic Republic of the Congo", "COD", "CD", true),
    COG("Congo-Brazzaville", "COG", "CG", true),
    COK("Cook Islands", "COK", "CK", true),
    COL("Colombia", "COL", "CO", true),
    COM("Comoros", "COM", "KM", true),
    CPV("Cape Verde", "CPV", "CV", true),
    CRI("Costa Rica", "CRI", "CR", true),
    CUB("Cuba", "CUB", "CU", true),
    CYM("Cayman Islands", "CYM", "KY", false),
    CYP("Cyprus", "CYP", "CY", false),
    CZE("Czechia", "CZE", "CZ", true),
    DEU("Germany", "DEU", "DE", true),
    DJI("Djibouti", "DJI", "DJ", true),
    DMA("Dominica", "DMA", "DM", false),
    DNK("Denmark", "DNK", "DK", true),
    DOM("Dominican Republic", "DOM", "DO", true),
    DZA("Algeria", "DZA", "DZ", true),
    ECU("Ecuador", "ECU", "EC", true),
    EGY("Egypt", "EGY", "EG", true),
    ERI("Eritrea", "ERI", "ER", true),
    ESP("Spain", "ESP", "ES", true),
    EST("Estonia", "EST", "EE", true),
    ETH("Ethiopia", "ETH", "ET", true),
    FIN("Finland", "FIN", "FI", true),
    FJI("Fiji", "FJI", "FJ", false),
    FLK("Falkland Islands", "FLK", "FK", false),
    FRA("France", "FRA", "FR", true),
    FRO("Faroe Islands", "FRO", "FO", true),
    FSM("Federated States of Micronesia", "FSM", "FM", true, FM_KSA, FM_PNI, FM_TRK, FM_YAP),
    GAB("Gabon", "GAB", "GA", true),
    GBR("United Kingdom", "GBR", "GB", false),
    GEO("Georgia", "GEO", "GE", true),
    GGY("Guernsey", "GGY", "GG", false),
    GHA("Ghana", "GHA", "GH", true),
    GIB("Gibraltar", "GIB", "GI", true),
    GIN("Guinea", "GIN", "GN", true),
    GMB("The Gambia", "GMB", "GM", true),
    GNB("Guinea-Bissau", "GNB", "GW", true),
    GNQ("Equatorial Guinea", "GNQ", "GQ", true),
    GRC("Greece", "GRC", "GR", true),
    GRD("Grenada", "GRD", "GD", false),
    GRL("Greenland", "GRL", "GL", true),
    GTM("Guatemala", "GTM", "GT", true),
    GUY("Guyana", "GUY", "GY", false),
    HND("Honduras", "HND", "HN", true),
    HKG("Hong Kong", "HKG", "HK", false),
    HRV("Croatia", "HRV", "HR", true),
    HTI("Haiti", "HTI", "HT", true),
    HUN("Hungary", "HUN", "HU", true),
    IDN("Indonesia", "IDN", "ID", false),
    IMN("Isle of Man", "IMN", "IM", false),
    IND("India", "IND", "IN", false),
    IOT("British Indian Ocean Territory", "IOT", "IO", true),
    IRL("Ireland", "IRL", "IE", false),
    IRN("Iran", "IRN", "IR", true),
    IRQ("Iraq", "IRQ", "IQ", true),
    ISL("Iceland", "ISL", "IS", true),
    ISR("Israel", "ISR", "IL", true),
    ITA("Italy", "ITA", "IT", true),
    JAM("Jamaica", "JAM", "JM", false),
    JEY("Jersey", "JEY", "JE", false),
    JOR("Jordan", "JOR", "JO", true),
    JPN("Japan", "JPN", "JP", false),
    KAZ("Kazakhstan", "KAZ", "KZ", true),
    KEN("Kenya", "KEN", "KE", false),
    KGZ("Kyrgyzstan", "KGZ", "KG", true),
    KHM("Cambodia", "KHM", "KH", true),
    KIR("Kiribati", "KIR", "KI", false),
    KNA("Saint Kitts and Nevis", "KNA", "KN", false),
    KOR("South Korea", "KOR", "KR", true),
    KWT("Kuwait", "KWT", "KW", true),
    LAO("Laos", "LAO", "LA", true),
    LBN("Lebanon", "LBN", "LB", true),
    LBR("Liberia", "LBR", "LR", true),
    LBY("Libya", "LBY", "LY", true),
    LCA("Saint Lucia", "LCA", "LC", false),
    LIE("Liechtenstein", "LIE", "LI", true),
    LKA("Sri Lanka", "LKA", "LK", false),
    LSO("Lesotho", "LSO", "LS", false),
    LTU("Lithuania", "LTU", "LT", true),
    LUX("Luxembourg", "LUX", "LU", true),
    LVA("Latvia", "LVA", "LV", true),
    MAC("Macao", "MAC", "MO", false),
    MAR("Morocco", "MAR", "MA", true),
    MCO("Monaco", "MCO", "MC", true),
    MDA("Moldova", "MDA", "MD", true),
    MDG("Madagascar", "MDG", "MG", true),
    MDV("Maldives", "MDV", "MV", false),
    MEX("Mexico", "MEX", "MX", true),
    MHL("Marshall Islands", "MHL", "MH", true),
    MKD("North Macedonia", "MKD", "MK", true),
    MLI("Mali", "MLI", "ML", true),
    MLT("Malta", "MLT", "MT", false),
    MMR("Myanmar", "MMR", "MM", true),
    MNE("Montenegro", "MNE", "ME", true),
    MNG("Mongolia", "MNG", "MN", true),
    MOZ("Mozambique", "MOZ", "MZ", false),
    MRT("Mauritania", "MRT", "MR", true),
    MSR("Montserrat", "MSR", "MS", true),
    MUS("Mauritius", "MUS", "MU", false),
    MWI("Malawi", "MWI", "MW", false),
    MYS("Malaysia", "MYS", "MY", false),
    NAM("Namibia", "NAM", "NA", false),
    NER("Niger", "NER", "NE", true),
    NGA("Nigeria", "NGA", "NG", true),
    NIC("Nicaragua", "NIC", "NI", true),
    NIU("Niue", "NIU", "NU", true),
    NLD("Netherlands", "NLD", "NL", true),
    NOR("Norway", "NOR", "NO", true),
    NPL("Nepal", "NPL", "NP", false),
    NRU("Nauru", "NRU", "NR", false),
    NZL("New Zealand", "NZL", "NZ", false),
    OMN("Oman", "OMN", "OM", true),
    PAK("Pakistan", "PAK", "PK", false),
    PAN("Panama", "PAN", "PA", true),
    PCN("Pitcairn Islands", "PCN", "PN", false),
    PER("Peru", "PER", "PE", true),
    PHL("Philippines", "PHL", "PH", true),
    PLW("Palau", "PLW", "PW", true),
    PNG("Papua New Guinea", "PNG", "PG", false),
    POL("Poland", "POL", "PL", true),
    PRK("North Korea", "PRK", "KP", true),
    PRT("Portugal", "PRT", "PT", true),
    PRY("Paraguay", "PRY", "PY", true),
    PSE("Palestinian Territories", "PSE", "PS", true),
    QAT("Qatar", "QAT", "QA", true),
    ROU("Romania", "ROU", "RO", true),
    RUS("Russia", "RUS", "RU", true),
    RWA("Rwanda", "RWA", "RW", true),
    SAU("Saudi Arabia", "SAU", "SA", true),
    SDN("Sudan", "SDN", "SD", true),
    SEN("Senegal", "SEN", "SN", true),
    SGP("Singapore", "SGP", "SG", false),
    SGS("South Georgia and the South Sandwich Islands", "SGS", "GS", true),
    SHN("Saint Helena, Ascension and Tristan da Cunha", "SHN", "SH", true),
    SLB("Solomon Islands", "SLB", "SB", false),
    SLE("Sierra Leone", "SLE", "SL", true),
    SLV("El Salvador", "SLV", "SV", true),
    SMR("San Marino", "SMR", "SM", true),
    SOM("Somalia", "SOM", "SO", true),
    SRB("Serbia", "SRB", "RS", true),
    SSD("South Sudan", "SSD", "SS", true),
    STP("São Tomé and Príncipe", "STP", "ST", true),
    SUR("Suriname", "SUR", "SR", false),
    SVK("Slovakia", "SVK", "SK", true),
    SVN("Slovenia", "SVN", "SI", true),
    SWE("Sweden", "SWE", "SE", true),
    SWZ("Eswatini", "SWZ", "SZ", false),
    SYC("Seychelles", "SYC", "SC", false),
    SYR("Syria", "SYR", "SY", true),
    TCA("Turks and Caicos Islands", "TCA", "TC", false),
    TCD("Chad", "TCD", "TD", true),
    TGO("Togo", "TGO", "TG", true),
    THA("Thailand", "THA", "TH", false),
    TJK("Tajikistan", "TJK", "TJ", true),
    TKL("Tokelau", "TKL", "TK", true),
    TKM("Turkmenistan", "TKM", "TM", true),
    TLS("Timor-Leste", "TLS", "TL", false), // East Timor
    TON("Tonga", "TON", "TO", false),
    TTO("Trinidad and Tobago", "TTO", "TT", false),
    TUN("Tunisia", "TUN", "TN", true),
    TUR("Turkey", "TUR", "TR", true),
    TUV("Tuvalu", "TUV", "TV", false),
    TWN("Taiwan", "TWN", "TW", true),
    TZA("Tanzania", "TZA", "TZ", false),
    UGA("Uganda", "UGA", "UG", false),
    UKR("Ukraine", "UKR", "UA", true),
    URY("Uruguay", "URY", "UY", true),
    USA("United States", "USA", "US",
            true, US_AL, US_AK, US_AZ, US_AR, US_CA, US_CO, US_CT, US_DE, US_DC, US_FL,
            US_GA, US_HI, US_ID, US_IL, US_IN, US_IA, US_KS, US_KY, US_LA, US_ME,
            US_MD, US_MA, US_MI, US_MN, US_MS, US_MO, US_MT, US_NE, US_NV, US_NH,
            US_NJ, US_NM, US_NY, US_NC, US_ND, US_OH, US_OK, US_OR, US_PA, US_RI,
            US_SC, US_SD, US_TN, US_TX, US_UT, US_VT, US_VA, US_WA, US_WV, US_WI, US_WY),
    UZB("Uzbekistan", "UZB", "UZ", true),
    VAT("Vatican City", "VAT", "VA", true),
    VCT("Saint Vincent and the Grenadines", "VCT", "VC", false),
    VEN("Venezuela", "VEN", "VE", true),
    VGB("British Virgin Islands", "VGB", "VG", false),
    VNM("Vietnam", "VNM", "VN", true),
    VUT("Vanuatu", "VUT", "VU", true),
    WSM("Samoa", "WSM", "WS", false),
    XKX("Kosovo", "XKX", "XK", true),
    YEM("Yemen", "YEM", "YE", true),
    ZAF("South Africa", "ZAF", "ZA", false),
    ZMB("Zambia", "ZMB", "ZM", false),
    ZWE("Zimbabwe", "ZWE", "ZW", false);

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
    private final boolean isRightHandTraffic;

    Country(String countryName, String alpha3, String alpha2, boolean isRightHandTraffic, State... states) {
        this.countryName = countryName;
        this.alpha2 = alpha2;
        this.alpha3 = alpha3;
        this.isRightHandTraffic = isRightHandTraffic;
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

    public boolean isRightHandTraffic() {
        return isRightHandTraffic;
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
