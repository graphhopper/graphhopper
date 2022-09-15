package com.graphhopper.reader.osm;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.ev.VehicleSpeed;
import com.graphhopper.routing.util.AccessFilter;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeExplorer;

public class OSMWayRestrictionWIPTest {
    private final String dir = "./target/tmp/test-db";
    private BooleanEncodedValue carAccessEnc;
    private DecimalEncodedValue carSpeedEnc;
    private BooleanEncodedValue footAccessEnc;
    private EdgeExplorer carOutExplorer;
    private EdgeExplorer carAllExplorer;


    class TmpGraphHopperFacade extends GraphHopper {
        public TmpGraphHopperFacade(String osmFile) {
            this(osmFile, false, "");
        }

        public TmpGraphHopperFacade(String osmFile, boolean turnCosts, String prefLang) {
            setStoreOnFlush(false);
            setOSMFile(osmFile);
            setGraphHopperLocation(dir);
            setProfiles(
                    new Profile("foot").setVehicle("foot").setWeighting("fastest"),
                    new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(turnCosts),
                    new Profile("bike").setVehicle("bike").setWeighting("fastest").setTurnCosts(turnCosts)
            );
            getReaderConfig().setPreferredLanguage(prefLang);
        }

        @Override
        protected void importOSM() {
            BaseGraph baseGraph = new BaseGraph.Builder(getEncodingManager()).set3D(hasElevation()).withTurnCosts(getEncodingManager().needsTurnCostsSupport()).build();
            setBaseGraph(baseGraph);
            super.importOSM();
            carAccessEnc = getEncodingManager().getBooleanEncodedValue(VehicleAccess.key("car"));
            carSpeedEnc = getEncodingManager().getDecimalEncodedValue(VehicleSpeed.key("car"));
            carOutExplorer = getBaseGraph().createEdgeExplorer(AccessFilter.outEdges(carAccessEnc));
            carAllExplorer = getBaseGraph().createEdgeExplorer(AccessFilter.allEdges(carAccessEnc));
            footAccessEnc = getEncodingManager().getBooleanEncodedValue(VehicleAccess.key("foot"));
        }

        @Override
        protected File _getOSMFile() {
            return new File(getClass().getResource(getOSMFile()).getFile());
        }
    }


    @Test
    void testViaWayTurnRestrictionsFromFile() {
        String fileTurnRestrictions = "test-way-restrictions.xml";
        GraphHopper hopper = new TmpGraphHopperFacade(fileTurnRestrictions, true, "").
                importOrLoad();
    }

}
