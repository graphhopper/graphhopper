package com.graphhopper.reader.osgb.dpn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.Test;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import com.graphhopper.reader.osgb.dpn.potentialHazards.InvalidPotentialHazardException;

public class OsDpnWayTest {

    public static final String aboveSurfaceWay = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#AboveSurfaceLevelOnStructure\">Above Surface Level On Structure</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";
    public static final String surfaceWay = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#SurfaceLevel\">Surface Level</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";
    public static final String tunnelWay = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String bridleWay = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Bridleway</dpn:rightOfUse>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";
    public static final String restrictedByWay = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Restricted Byway</dpn:rightOfUse>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";
    public static final String hazardMud = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Restricted Byway</dpn:rightOfUse>\n"
            + "      <dpn:potentialHazardCrossed>Mud</dpn:potentialHazardCrossed>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";
    public static final String hazardInvalid = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Restricted Byway</dpn:rightOfUse>\n"
            + "      <dpn:potentialHazardCrossed>Mud, Unknown Hazard</dpn:potentialHazardCrossed>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";
    public static final String hazardMultipleOneLine = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Restricted Byway</dpn:rightOfUse>\n"
            + "      <dpn:potentialHazardCrossed>Mud, Scree</dpn:potentialHazardCrossed>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";
    public static final String hazardMultiple = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Restricted Byway</dpn:rightOfUse>\n"
            + "      <dpn:potentialHazardCrossed>Mud</dpn:potentialHazardCrossed>\n"
            + "      <dpn:potentialHazardCrossed>Scree</dpn:potentialHazardCrossed>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String madeSealed = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#MadeSealed\">Made Sealed</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Restricted Byway</dpn:rightOfUse>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String madeUnsealed = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#MadeUnsealed\">Made Unsealed</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Restricted Byway</dpn:rightOfUse>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String madeUnknown = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#MadeUnknown\">Made Unknown</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:rightOfUse>Restricted Byway</dpn:rightOfUse>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String unmadeNoPhysicalButWithinAccessLand = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String adoptedNationalCycleRoute = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>true</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String adoptedOtherCycleRoute = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#NoPhysicalManifestation\">No Physical Manifestation</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>true</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>true</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String aRoad = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#ARoad\">A Road</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String bRoad = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#BRoad\">B Road</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String alley = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#Alley\">Alley</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String privateRoad = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#PrivateRoad\">Private Road</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    public static final String path = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gml:FeatureCollection xmlns:gml=\"http://www.opengis.net/gml/3.2\" xsi:schemaLocation=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0 detailedPathNetwork.xsd http://www.opengis.net/gml/3.2 gml/3.2.1/gml.xsd\" gml:id=\"DPN\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:dpn=\"http://namespaces.ordnancesurvey.co.uk/networks/detailedPathNetwork/1.0\" xmlns:gmd=\"http://www.isotc211.org/2005/gmd\" xmlns:gco=\"http://www.isotc211.org/2005/gco\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns:gss=\"http://www.isotc211.org/2005/gss\" xmlns:gts=\"http://www.isotc211.org/2005/gts\" xmlns:gsr=\"http://www.isotc211.org/2005/gsr\" xmlns:gmlxbt=\"http://www.opengis.net/gml/3.3/xbt\">"
            + "  <gml:featureMember>\n"
            + "    <dpn:RouteLink gml:id=\"osgb35cff694-c2a8-461e-9540-730e3ae11a7a\">"
            + "      <dpn:featureID>35cff694-c2a8-461e-9540-730e3ae11a7a</dpn:featureID>\n"
            + "      <dpn:versionID>1</dpn:versionID>\n"
            + "      <dpn:versionDate>2014-12-12</dpn:versionDate>\n"
            + "      <dpn:startNode xlink:href=\"#df162dd8-c284-469b-81d6-d63105a39c7f\"/>\n"
            + "      <dpn:endNode xlink:href=\"#56dc4c0b-0586-4849-b3e2-1e00ee149429\"/>\n"
            + "      <dpn:descriptiveGroup codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteDescriptiveGroupValue#NonMotorisedVehicularRouteNetwork\">Non Motorised Vehicular Route Network</dpn:descriptiveGroup>\n"
            + "      <dpn:descriptiveTerm codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/RouteLinkDescriptiveTermValue#Path\">Path</dpn:descriptiveTerm>\n"
            + "      <dpn:surfaceType codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/SurfaceTypeValue#Unmade\">Unmade</dpn:surfaceType>\n"
            + "      <dpn:physicalLevel codeSpace=\"http://www.ordnancesurvey.co.uk/xml/codelists/LevelCodeValue#BelowSurfaceLevelTunnel\">Below Surface Level Tunnel</dpn:physicalLevel>\n"
            + "      <dpn:name>Named Road</dpn:name>\n"
            + "      <dpn:adoptedByNationalCycleRoute>false</dpn:adoptedByNationalCycleRoute>\n"
            + "      <dpn:adoptedByRecreationalRoute>false</dpn:adoptedByRecreationalRoute>\n"
            + "      <dpn:adoptedByOtherCycleRoute>false</dpn:adoptedByOtherCycleRoute>\n"
            + "      <dpn:withinAccessLand>true</dpn:withinAccessLand>\n"
            + "      <dpn:crossesDangerArea>false</dpn:crossesDangerArea>\n"
            + "      <dpn:verticalGain>\n"
            + "        <dpn:VerticalGainType>\n"
            + "          <dpn:inDirection uom=\"m\">9</dpn:inDirection>\n"
            + "          <dpn:againstDirection uom=\"m\">2</dpn:againstDirection>\n"
            + "        </dpn:VerticalGainType>\n"
            + "      </dpn:verticalGain>\n"
            + "      <dpn:planimetricLength uom=\"m\">85</dpn:planimetricLength>\n"
            + "      <dpn:surfaceLength uom=\"m\">86</dpn:surfaceLength>\n"
            + "      <dpn:geometry>\n"
            + "        <gml:LineString srsName=\"urn:ogc:def:crs:EPSG::7405\" gml:id=\"LOCAL_ID_29519\">\n"
            + "          <gml:posList srsDimension=\"3\" count=\"10\">428672.241 380372.608999999 299.7 428653.822 380372.229 302.5 428647.49 380373.239 303.29 428645.296 380374.958000001 303.36 428638.156 380386.505000001 302.3 428635.625 380389.073999999 301.89 428631.214 380390.913000001 301.39 428628.625 380390.854 301.44 428622.598 380388.684 302.44 428598.489 380377.006999999 306.36</gml:posList>\n"
            + "        </gml:LineString>\n" + "      </dpn:geometry>\n" + "    </dpn:RouteLink>";

    @Test
    public void testSurface() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(surfaceWay);
        assertFalse("Way should not have a tunnel", way.hasTag("tunnel", "yes"));
        assertFalse("Way should not have a bridge", way.hasTag("bridge", "yes"));
    }

    @Test
    public void testWithinAccessLand() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(unmadeNoPhysicalButWithinAccessLand);
        assertTrue("Should allow walking as within access land even though no other right of way declared",
                way.hasTag("foot", "yes"));
    }

    @Test
    public void testBelowSurfaceTunnel() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(tunnelWay);
        assertTrue("Way should have a tunnel", way.hasTag("tunnel", "yes"));
        assertFalse("Way should not have a bridge", way.hasTag("bridge", "yes"));
    }

    @Test
    public void testAboveSurface() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(aboveSurfaceWay);
        assertFalse("Way should not have a tunnel", way.hasTag("tunnel", "yes"));
        assertTrue("Way should have a bridge", way.hasTag("bridge", "yes"));
    }

    @Test
    public void testRightOfWayBridleWay() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(bridleWay);
        assertTrue("Way should be designation public_bridleway", way.hasTag("designation", "public_bridleway"));
    }

    @Test
    public void testRightOfWayRestrictedByway() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(restrictedByWay);
        assertTrue("Way should be designation bridleway", way.hasTag("designation", "restricted_byway"));
    }

    @Test
    public void testPotentialHazardMud() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(hazardMud);
        assertTrue("Way should be tagged natural=mud", way.hasTag("natural", "mud"));
    }

    @Test(expected = InvalidPotentialHazardException.class)
    public void testPotentialHazardInvalid() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay.THROW_EXCEPTION_ON_INVALID_HAZARD = true;
        getOsDpnWay(hazardInvalid);
    }

    @Test
    public void testPotentialHazardMultipleOneLine() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(hazardMultipleOneLine);
        assertTrue("Mud and Scree", way.hasTag("natural", "mud,scree"));
    }

    @Test
    public void testPotentialHazardMultiple() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(hazardMultiple);
        assertTrue("Mud", way.hasTag("natural", "mud,scree"));
    }

    @Test
    public void testMadeSealed() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(madeSealed);
        assertTrue("Way surface paved", way.hasTag("surface", "paved"));
    }

    @Test
    public void testMadeUnsealed() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(madeUnsealed);
        assertTrue("Way surface paved", way.hasTag("surface", "unpaved"));
    }

    /**
     * With no better information to go on this will need to be classified as
     * the same as Made Unsealed
     *
     */
    @Test
    public void testMadeUnknown() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(madeUnknown);
        assertTrue("Way surface paved", way.hasTag("surface", "unpaved"));
    }

    @Test
    public void testUnmade() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(unmadeNoPhysicalButWithinAccessLand);
        assertTrue("Way surface paved", way.hasTag("surface", "ground"));
    }

    @Test
    public void testAdoptedNationalCycleRoute() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(adoptedNationalCycleRoute);
        assertTrue("Way bicycle accessible", way.hasTag("bicycle", "yes"));
    }

    @Test
    public void testAdoptedOtherCycleRoute() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(adoptedOtherCycleRoute);
        assertTrue("Way bicycle accessible", way.hasTag("bicycle", "yes"));
    }

    @Test
    public void testARoad() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(aRoad);
        assertTrue("A Roads are primary", way.hasTag("highway", "primary"));
    }

    @Test
    public void testBRoad() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(bRoad);
        assertTrue("B Roads are secondary", way.hasTag("highway", "secondary"));
    }

    @Test
    public void testAlley() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(alley);
        assertTrue("Alleys are service roads", way.hasTag("highway", "service"));
        assertTrue("Alleys are service roads", way.hasTag("service", "alley"));
    }

    @Test
    public void testPrivate() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(privateRoad);
        assertTrue(way.hasTag("highway", "private"));
    }

    @Test
    public void testPath() throws XMLStreamException, FactoryException, TransformException {
        OsDpnWay way = getOsDpnWay(path);
        assertTrue(way.hasTag("highway", "path"));
    }

    private OsDpnWay getOsDpnWay(String way) throws XMLStreamException, FactoryException, TransformException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        StringReader stringReader = new StringReader(way);
        XMLStreamReader parser = factory.createXMLStreamReader(stringReader);

        OsDpnWay dpnWay = new OsDpnWay("1");
        dpnWay.readTags(parser);
        return dpnWay;
    }

}