package com.graphhopper.routing.util.spatialrules;

import com.graphhopper.routing.util.spatialrules.countries.AndorraSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.AustriaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.BelgiumSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.CroatiaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.CzechiaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.DenmarkSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.FranceSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.GermanySpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.HungarySpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.IcelandSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.IrelandSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.ItalySpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.LiechtensteinSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.LuxembourgSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.MonacoSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.NetherlandsSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.NorwaySpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.PolandSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.PortugalSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.SanMarinoSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.SlovakiaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.SloveniaSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.SpainSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.SwedenSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.SwitzerlandSpatialRule;
import com.graphhopper.routing.util.spatialrules.countries.UnitedKingdomSpatialRule;
import com.graphhopper.util.shapes.Polygon;

import java.util.List;

public class CountriesSpatialRuleFactory implements SpatialRuleLookupBuilder.SpatialRuleFactory {
    @Override
    public SpatialRule createSpatialRule(String id, List<Polygon> polygons) {
        switch (id) {
            case "AND":
                AndorraSpatialRule andorraSpatialRule = new AndorraSpatialRule();
                andorraSpatialRule.setBorders(polygons);
                return andorraSpatialRule;
            case "AUT":
                AustriaSpatialRule austriaSpatialRule = new AustriaSpatialRule();
                austriaSpatialRule.setBorders(polygons);
                return austriaSpatialRule;
            case "BEL":
                BelgiumSpatialRule belgiumSpatialRule = new BelgiumSpatialRule();
                belgiumSpatialRule.setBorders(polygons);
                return belgiumSpatialRule;
            case "CHE":
                SwitzerlandSpatialRule switzerlandSpatialRule = new SwitzerlandSpatialRule();
                switzerlandSpatialRule.setBorders(polygons);
                return switzerlandSpatialRule;
            case "CZE":
                CzechiaSpatialRule czechiaSpatialRule = new CzechiaSpatialRule();
                czechiaSpatialRule.setBorders(polygons);
                return czechiaSpatialRule;
            case "DEU":
                GermanySpatialRule germanySpatialRule = new GermanySpatialRule();
                germanySpatialRule.setBorders(polygons);
                return germanySpatialRule;
            case "DNK":
                DenmarkSpatialRule denmarkSpatialRule = new DenmarkSpatialRule();
                denmarkSpatialRule.setBorders(polygons);
                return denmarkSpatialRule;
            case "ESP":
                SpainSpatialRule spainSpatialRule = new SpainSpatialRule();
                spainSpatialRule.setBorders(polygons);
                return spainSpatialRule;
            case "FRA":
                FranceSpatialRule franceSpatialRule = new FranceSpatialRule();
                franceSpatialRule.setBorders(polygons);
                return franceSpatialRule;
            case "GBR":
                UnitedKingdomSpatialRule unitedKingdomSpatialRule = new UnitedKingdomSpatialRule();
                unitedKingdomSpatialRule.setBorders(polygons);
                return unitedKingdomSpatialRule;
            case "HRV":
                CroatiaSpatialRule croatiaSpatialRule = new CroatiaSpatialRule();
                croatiaSpatialRule.setBorders(polygons);
                return croatiaSpatialRule;
            case "HUN":
                HungarySpatialRule hungarySpatialRule = new HungarySpatialRule();
                hungarySpatialRule.setBorders(polygons);
                return hungarySpatialRule;
            case "IRL":
                IrelandSpatialRule irelandSpatialRule = new IrelandSpatialRule();
                irelandSpatialRule.setBorders(polygons);
                return irelandSpatialRule;
            case "ISL":
                IcelandSpatialRule icelandSpatialRule = new IcelandSpatialRule();
                icelandSpatialRule.setBorders(polygons);
                return icelandSpatialRule;
            case "ITA":
                ItalySpatialRule italySpatialRule = new ItalySpatialRule();
                italySpatialRule.setBorders(polygons);
                return italySpatialRule;
            case "LIE":
                LiechtensteinSpatialRule liechtensteinSpatialRule = new LiechtensteinSpatialRule();
                liechtensteinSpatialRule.setBorders(polygons);
                return liechtensteinSpatialRule;
            case "LUX":
                LuxembourgSpatialRule luxembourgSpatialRule = new LuxembourgSpatialRule();
                luxembourgSpatialRule.setBorders(polygons);
                return luxembourgSpatialRule;
            case "MCO":
                MonacoSpatialRule monacoSpatialRule = new MonacoSpatialRule();
                monacoSpatialRule.setBorders(polygons);
                return monacoSpatialRule;
            case "NLD":
                NetherlandsSpatialRule netherlandsSpatialRule = new NetherlandsSpatialRule();
                netherlandsSpatialRule.setBorders(polygons);
                return netherlandsSpatialRule;
            case "NOR":
                NorwaySpatialRule norwaySpatialRule = new NorwaySpatialRule();
                norwaySpatialRule.setBorders(polygons);
                return norwaySpatialRule;
            case "POL":
                PolandSpatialRule polandSpatialRule = new PolandSpatialRule();
                polandSpatialRule.setBorders(polygons);
                return polandSpatialRule;
            case "PRT":
                PortugalSpatialRule portugalSpatialRule = new PortugalSpatialRule();
                portugalSpatialRule.setBorders(polygons);
                return portugalSpatialRule;
            case "SMR":
                SanMarinoSpatialRule sanMarinoSpatialRule = new SanMarinoSpatialRule();
                sanMarinoSpatialRule.setBorders(polygons);
                return sanMarinoSpatialRule;
            case "SVK":
                SlovakiaSpatialRule slovakiaSpatialRule = new SlovakiaSpatialRule();
                slovakiaSpatialRule.setBorders(polygons);
                return slovakiaSpatialRule;
            case "SVN":
                SloveniaSpatialRule sloveniaSpatialRule = new SloveniaSpatialRule();
                sloveniaSpatialRule.setBorders(polygons);
                return sloveniaSpatialRule;
            case "SWE":
                SwedenSpatialRule swedenSpatialRule = new SwedenSpatialRule();
                swedenSpatialRule.setBorders(polygons);
                return swedenSpatialRule;
            default:
                return SpatialRule.EMPTY;
        }
    }
}
