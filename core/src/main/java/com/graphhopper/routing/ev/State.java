package com.graphhopper.routing.ev;

/**
 * The country subdivision is stored in this EncodedValue. E.g. US-CA is the enum US_CA.
 */
public enum State {
    MISSING("-"),

    // Australia
    AU_ACT("AU-ACT"),
    AU_NSW("AU-NSW"),
    AU_NT("AU-NT"),
    AU_QLD("AU-QLD"),
    AU_SA("AU-SA"),
    AU_TAS("AU-TAS"),
    AU_VIC("AU-VIC"),
    AU_WA("AU-WA"),

    // the sub regions of Belgium have no data in countries.geojson
    // BE_BRU("BE-BRU", BEL),
    // BE_VLG("BE-VLG", BEL),
    // BE_WAL("BE-WAL", BEL),

    // Canada
    CA_AB("CA-AB"),
    CA_BC("CA-BC"),
    CA_MB("CA-MB"),
    CA_NB("CA-NB"),
    CA_NL("CA-NL"),
    CA_NS("CA-NS"),
    CA_NT("CA-NT"),
    CA_NU("CA-NU"),
    CA_ON("CA-ON"),
    CA_PE("CA-PE"),
    CA_QC("CA-QC"),
    CA_SK("CA-SK"),
    CA_YT("CA-YT"),

    // Federated States of Micronesia
    FM_KSA("FM-KSA"),
    FM_PNI("FM-PNI"),
    FM_TRK("FM-TRK"),
    FM_YAP("FM-YAP"),

    // United Kingdom
    // TODO currently it isn't supported when the states list does not cover the entire country
    //  furthermore the speed limits for Scotland are not different for car
    // GB_SCT("GB-SCT", GBR),

    // Netherlands
    // TODO same problem here
    // NL_BQ1("NL-BQ1", NLD),
    // NL_BQ2("NL-BQ2", NLD),
    // NL_BQ3("NL-BQ3", NLD),

    // United States
    US_AL("US-AL"),
    US_AK("US-AK"),
    US_AZ("US-AZ"),
    US_AR("US-AR"),
    US_CA("US-CA"),
    US_CO("US-CO"),
    US_CT("US-CT"),
    US_DE("US-DE"),
    US_DC("US-DC"), // is a federal district not a state
    US_FL("US-FL"),
    US_GA("US-GA"),
    US_HI("US-HI"),
    US_ID("US-ID"),
    US_IL("US-IL"),
    US_IN("US-IN"),
    US_IA("US-IA"),
    US_KS("US-KS"),
    US_KY("US-KY"),
    US_LA("US-LA"),
    US_ME("US-ME"),
    US_MD("US-MD"),
    US_MA("US-MA"),
    US_MI("US-MI"),
    US_MN("US-MN"),
    US_MS("US-MS"),
    US_MO("US-MO"),
    US_MT("US-MT"),
    US_NE("US-NE"),
    US_NV("US-NV"),
    US_NH("US-NH"),
    US_NJ("US-NJ"),
    US_NM("US-NM"),
    US_NY("US-NY"),
    US_NC("US-NC"),
    US_ND("US-ND"),
    US_OH("US-OH"),
    US_OK("US-OK"),
    US_OR("US-OR"),
    US_PA("US-PA"),
    US_RI("US-RI"),
    US_SC("US-SC"),
    US_SD("US-SD"),
    US_TN("US-TN"),
    US_TX("US-TX"),
    US_UT("US-UT"),
    US_VT("US-VT"),
    US_VA("US-VA"),
    US_WA("US-WA"),
    US_WV("US-WV"),
    US_WI("US-WI"),
    US_WY("US-WY");

    public static final String KEY = "state", ISO_3166_2 = "ISO3166-2";

    private final String stateCode;

    /**
     * @param isoCodeOfSubdivision should be ISO 3166-2 but with hyphen like US-CA
     */
    State(String isoCodeOfSubdivision) {
        this.stateCode = isoCodeOfSubdivision;
    }

    /**
     * @param iso should be ISO 3166-2 but with hyphen like US-CA
     */
    public static State find(String iso) {
        try {
            return State.valueOf(iso.replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return State.MISSING;
        }
    }

    /**
     * @return ISO 3166-2 code with hyphen. E.g. US-CA
     */
    public String getStateCode() {
        return stateCode;
    }

    @Override
    public String toString() {
        return stateCode;
    }

    public static EnumEncodedValue<State> create() {
        return new EnumEncodedValue<>(State.KEY, State.class);
    }
}
