package com.graphhopper.routing.ev;

import static com.graphhopper.routing.ev.Country.*;

public enum State {
    MISSING("-", Country.MISSING),

    // Australia
    AU_ACT("ACT", AUS),
    AU_NSW("NSW", AUS),
    AU_NT("NT", AUS),
    AU_QLD("QLD", AUS),
    AU_SA("SA", AUS),
    AU_TAS("TAS", AUS),
    AU_VIC("VIC", AUS),
    AU_WA("WA", AUS),

    // Belgium
    BE_BRU("BRU", BEL),
    BE_VLG("VLG", BEL),
    BE_WAL("WAL", BEL),

    // Canada
    CA_AB("AB", CAN),
    CA_BC("BC", CAN),
    CA_MB("MB", CAN),
    CA_NB("NB", CAN),
    CA_NL("NL", CAN),
    CA_NS("NS", CAN),
    CA_NT("NT", CAN),
    CA_NU("NU", CAN),
    CA_ON("ON", CAN),
    CA_PE("PE", CAN),
    CA_QC("QC", CAN),
    CA_SK("SK", CAN),
    CA_YT("YT", CAN),

    // Federated States of Micronesia
    FM_KSA("KSA", FSM),
    FM_PNI("PNI", FSM),
    FM_TRK("TRK", FSM),
    FM_YAP("YAP", FSM),

    // United Kingdom
    GB_SCT("SCT", GBR),

    // Netherlands
    NL_BQ1("BQ1", NLD),
    NL_BQ2("BQ2", NLD),
    NL_BQ3("BQ3", NLD),

    // United States
    US_AL("AL", USA),
    US_AK("AK", USA),
    US_AZ("AZ", USA),
    US_AR("AR", USA),
    US_CA("CA", USA),
    US_CO("CO", USA),
    US_CT("CT", USA),
    US_DE("DE", USA),
    US_DC("DC", USA), // is a federal district not a state
    US_FL("FL", USA),
    US_GA("GA", USA),
    US_HI("HI", USA),
    US_ID("ID", USA),
    US_IL("IL", USA),
    US_IN("IN", USA),
    US_IA("IA", USA),
    US_KS("KS", USA),
    US_KY("KY", USA),
    US_LA("LA", USA),
    US_ME("ME", USA),
    US_MD("MD", USA),
    US_MA("MA", USA),
    US_MI("MI", USA),
    US_MN("MN", USA),
    US_MS("MS", USA),
    US_MO("MO", USA),
    US_MT("MT", USA),
    US_NE("NE", USA),
    US_NV("NV", USA),
    US_NH("NH", USA),
    US_NJ("NJ", USA),
    US_NM("NM", USA),
    US_NY("NY", USA),
    US_NC("NC", USA),
    US_ND("ND", USA),
    US_OH("OH", USA),
    US_OK("OK", USA),
    US_OR("OR", USA),
    US_PA("PA", USA),
    US_RI("RI", USA),
    US_SC("SC", USA),
    US_SD("SD", USA),
    US_TN("TN", USA),
    US_TX("TX", USA),
    US_UT("UT", USA),
    US_VT("VT", USA),
    US_VA("VA", USA),
    US_WA("WA", USA),
    US_WV("WV", USA),
    US_WI("WI", USA),
    US_WY("WY", USA);

    public static final String KEY = "state", ISO_3166_2 = "ISO3166-2";

    private final Country country;
    private final String subdivisionCode;

    State(String subdivisionCode, Country country) {
        this.subdivisionCode = subdivisionCode;
        this.country = country;
    }

    /**
     * @param iso should be ISO 3166-2 but with hyphen like US-CA
     */
    public static State find(String iso) {
        try {
            return State.valueOf(iso.replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex); // TODO NOW REMOVE
            // return State.MISSING;
        }
    }


    public String getStateCode() {
        return subdivisionCode;
    }

    public Country getCountry() {
        return country;
    }

    @Override
    public String toString() {
        return subdivisionCode;
    }

    public static EnumEncodedValue<State> create() {
        return new EnumEncodedValue<>(State.KEY, State.class);
    }
}
