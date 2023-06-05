package com.graphhopper.routing.ev;

import static com.graphhopper.routing.ev.Country.*;

/**
 * The country subdivision is stored in this EncodedValue. E.g. US-CA is the enum US_CA.
 */
public enum State {
    MISSING("-", Country.MISSING),

    // Australia
    AU_ACT("AU-ACT", AUS),
    AU_NSW("AU-NSW", AUS),
    AU_NT("AU-NT", AUS),
    AU_QLD("AU-QLD", AUS),
    AU_SA("AU-SA", AUS),
    AU_TAS("AU-TAS", AUS),
    AU_VIC("AU-VIC", AUS),
    AU_WA("AU-WA", AUS),

    // Belgium
    BE_BRU("BE-BRU", BEL),
    BE_VLG("BE-VLG", BEL),
    BE_WAL("BE-WAL", BEL),

    // Canada
    CA_AB("CA-AB", CAN),
    CA_BC("CA-BC", CAN),
    CA_MB("CA-MB", CAN),
    CA_NB("CA-NB", CAN),
    CA_NL("CA-NL", CAN),
    CA_NS("CA-NS", CAN),
    CA_NT("CA-NT", CAN),
    CA_NU("CA-NU", CAN),
    CA_ON("CA-ON", CAN),
    CA_PE("CA-PE", CAN),
    CA_QC("CA-QC", CAN),
    CA_SK("CA-SK", CAN),
    CA_YT("CA-YT", CAN),

    // Federated States of Micronesia
    FM_KSA("FM-KSA", FSM),
    FM_PNI("FM-PNI", FSM),
    FM_TRK("FM-TRK", FSM),
    FM_YAP("FM-YAP", FSM),

    // United Kingdom
    GB_SCT("GB-SCT", GBR),

    // Netherlands
    NL_BQ1("NL-BQ1", NLD),
    NL_BQ2("NL-BQ2", NLD),
    NL_BQ3("NL-BQ3", NLD),

    // United States
    US_AL("US-AL", USA),
    US_AK("US-AK", USA),
    US_AZ("US-AZ", USA),
    US_AR("US-AR", USA),
    US_CA("US-CA", USA),
    US_CO("US-CO", USA),
    US_CT("US-CT", USA),
    US_DE("US-DE", USA),
    US_DC("US-DC", USA), // is a federal district not a state
    US_FL("US-FL", USA),
    US_GA("US-GA", USA),
    US_HI("US-HI", USA),
    US_ID("US-ID", USA),
    US_IL("US-IL", USA),
    US_IN("US-IN", USA),
    US_IA("US-IA", USA),
    US_KS("US-KS", USA),
    US_KY("US-KY", USA),
    US_LA("US-LA", USA),
    US_ME("US-ME", USA),
    US_MD("US-MD", USA),
    US_MA("US-MA", USA),
    US_MI("US-MI", USA),
    US_MN("US-MN", USA),
    US_MS("US-MS", USA),
    US_MO("US-MO", USA),
    US_MT("US-MT", USA),
    US_NE("US-NE", USA),
    US_NV("US-NV", USA),
    US_NH("US-NH", USA),
    US_NJ("US-NJ", USA),
    US_NM("US-NM", USA),
    US_NY("US-NY", USA),
    US_NC("US-NC", USA),
    US_ND("US-ND", USA),
    US_OH("US-OH", USA),
    US_OK("US-OK", USA),
    US_OR("US-OR", USA),
    US_PA("US-PA", USA),
    US_RI("US-RI", USA),
    US_SC("US-SC", USA),
    US_SD("US-SD", USA),
    US_TN("US-TN", USA),
    US_TX("US-TX", USA),
    US_UT("US-UT", USA),
    US_VT("US-VT", USA),
    US_VA("US-VA", USA),
    US_WA("US-WA", USA),
    US_WV("US-WV", USA),
    US_WI("US-WI", USA),
    US_WY("US-WY", USA);

    public static final String KEY = "state", ISO_3166_2 = "ISO3166-2";

    private final Country country;
    private final String stateCode;

    /**
     * @param isoCodeOfSubdivision should be ISO 3166-2 but with hyphen like US-CA
     */
    State(String isoCodeOfSubdivision, Country country) {
        this.stateCode = isoCodeOfSubdivision;
        this.country = country;
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

    public Country getCountry() {
        return country;
    }

    @Override
    public String toString() {
        return stateCode;
    }

    public static EnumEncodedValue<State> create() {
        return new EnumEncodedValue<>(State.KEY, State.class);
    }
}
