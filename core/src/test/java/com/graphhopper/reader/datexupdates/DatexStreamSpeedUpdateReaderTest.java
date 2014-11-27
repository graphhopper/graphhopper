package com.graphhopper.reader.datexupdates;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.xml.sax.SAXException;

import uk.co.ordnancesurvey.api.srs.LatLong;

public class DatexStreamSpeedUpdateReaderTest {

	private static final String SPEED_TAG = "speed";
	private static final String LONGITUDE = "-3.558952191414606";
	private static final String LATITUDE = "50.69919585809061";
	private static final String SPEED = "103.0";

	@Test
	public void testSimpleSpeedNode() throws XMLStreamException, ParserConfigurationException, SAXException, IOException {
		String datexStream = "<?xml version=\"1.0\"?>"
				+ "<siteMeasurements>"
				+ "<measurementSiteReference version=\"13.0\" id=\"435D4B1B134C41C1B00A78BA233A82E0\" targetClass=\"MeasurementSiteRecord\"/>"
				+ "<measurementTimeDefault>2013-09-24T15:15:00.000+01:00</measurementTimeDefault>"
				+ "<measuredValue index=\"0\">"
				+ "<measuredValue>"
				+ "	<basicData xsi:type=\"TrafficSpeed\">"
				+ "		<averageVehicleSpeed>"
				+ "			<dataError>false</dataError>"
				+ "			<speed>"
				+ SPEED
				+ "</speed>"
				+ "		</averageVehicleSpeed>"
				+ "	</basicData>"
				+ "</measuredValue></measuredValue><measuredValue index=\"1\"><measuredValue><basicData xsi:type=\"TrafficHeadway\"><averageTimeHeadway><dataError>false</dataError><duration>4.9</duration></averageTimeHeadway></basicData></measuredValue></measuredValue><measuredValue index=\"2\"><measuredValue><basicData xsi:type=\"TrafficConcentration\"><occupancy><dataError>false</dataError><percentage>7.0</percentage></occupancy></basicData></measuredValue></measuredValue><measuredValue index=\"3\"><measuredValue><basicData xsi:type=\"TrafficFlow\"><vehicleFlow><dataError>false</dataError><vehicleFlowRate>300</vehicleFlowRate></vehicleFlow></basicData>NIS P TIH 008 NTIS DATEX II Service Version 4.0Page 36</measuredValue></measuredValue><measuredValue index=\"4\"><measuredValue><basicData xsi:type=\"TrafficFlow\"><vehicleFlow><dataError>false</dataError><vehicleFlowRate>120</vehicleFlowRate></vehicleFlow></basicData></measuredValue></measuredValue><measuredValue index=\"5\"><measuredValue><basicData xsi:type=\"TrafficFlow\"><vehicleFlow><dataError>false</dataError><vehicleFlowRate>180</vehicleFlowRate></vehicleFlow></basicData></measuredValue></measuredValue><measuredValue index=\"6\"><measuredValue><basicData xsi:type=\"TrafficFlow\"><vehicleFlow><dataError>false</dataError><vehicleFlowRate>120</vehicleFlowRate></vehicleFlow></basicData></measuredValue></measuredValue>"
				+ "</siteMeasurements>";
		
		String datexModelStream = "<?xml version=\"1.0\"?>"
				+ "<d2lm:measurementSiteTable version=\"[NTIS Model version]\" id=\"NTIS_TMU_Measurement_Sites\">\n" + 
				"<d2lm:measurementSiteRecord version=\"[NTIS Model version]\" id=\"435D4B1B134C41C1B00A78BA233A82E0\">\n" + 
				"<d2lm:measurementEquipmentTypeUsed>\n" + 
				"<d2lm:values>\n" + 
				"<d2lm:value>loop</d2lm:value>\n" + 
				"</d2lm:values>\n" + 
				"</d2lm:measurementEquipmentTypeUsed>\n" + 
				"<d2lm:measurementSiteIdentification>[TMU site ref]</d2lm:measurementSiteIdentification>\n" + 
				"<d2lm:measurementSpecificCharacteristics index=\"0\">\n" + 
				"<d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:specificLane>allLanesCompleteCarriageway</d2lm:specificLane>\n" + 
				"<d2lm:specificMeasurementValueType>trafficSpeed</d2lm:specificMeasurementValueType>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:measurementSpecificCharacteristics index=\"1\">\n" + 
				"<d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:specificLane>allLanesCompleteCarriageway</d2lm:specificLane>\n" + 
				"<d2lm:specificMeasurementValueType>trafficHeadway</d2lm:specificMeasurementValueType>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:measurementSpecificCharacteristics index=\"2\">\n" + 
				"<d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:specificLane>allLanesCompleteCarriageway</d2lm:specificLane>\n" + 
				"<d2lm:specificMeasurementValueType>trafficConcentration</d2lm:specificMeasurementValueType>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:measurementSpecificCharacteristics index=\"3\">\n" + 
				"<d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:specificLane>allLanesCompleteCarriageway</d2lm:specificLane>\n" + 
				"<d2lm:specificMeasurementValueType>trafficFlow</d2lm:specificMeasurementValueType>\n" + 
				"<d2lm:specificVehicleCharacteristics>\n" + 
				"<d2lm:lengthCharacteristic>\n" + 
				"<d2lm:comparisonOperator>lessThanOrEqualTo</d2lm:comparisonOperator>\n" + 
				"<d2lm:vehicleLength>5.2</d2lm:vehicleLength>\n" + 
				"</d2lm:lengthCharacteristic>\n" + 
				"</d2lm:specificVehicleCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:measurementSpecificCharacteristics index=\"4\">\n" + 
				"<d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:specificLane>allLanesCompleteCarriageway</d2lm:specificLane>\n" + 
				"<d2lm:specificMeasurementValueType>trafficFlow</d2lm:specificMeasurementValueType>\n" + 
				"<d2lm:specificVehicleCharacteristics>\n" + 
				"<d2lm:lengthCharacteristic>\n" + 
				"<d2lm:comparisonOperator>greaterThan</d2lm:comparisonOperator>\n" + 
				"<d2lm:vehicleLength>5.2</d2lm:vehicleLength>\n" + 
				"</d2lm:lengthCharacteristic>\n" + 
				"<d2lm:lengthCharacteristic>\n" + 
				"<d2lm:comparisonOperator>lessThanOrEqualTo</d2lm:comparisonOperator>\n" + 
				"<d2lm:vehicleLength>6.6</d2lm:vehicleLength>\n" + 
				"</d2lm:lengthCharacteristic>\n" + 
				"</d2lm:specificVehicleCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:measurementSpecificCharacteristics index=\"5\">\n" + 
				"<d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:specificLane>allLanesCompleteCarriageway</d2lm:specificLane>\n" + 
				"<d2lm:specificMeasurementValueType>trafficFlow</d2lm:specificMeasurementValueType>\n" + 
				"<d2lm:specificVehicleCharacteristics>\n" + 
				"<d2lm:lengthCharacteristic>\n" + 
				"<d2lm:comparisonOperator>greaterThan</d2lm:comparisonOperator>\n" + 
				"<d2lm:vehicleLength>6.6</d2lm:vehicleLength>\n" + 
				"</d2lm:lengthCharacteristic>\n" + 
				"<d2lm:lengthCharacteristic>\n" + 
				"NIS P TIH 008 NTIS DATEX II Service Version 4.0\n" + 
				"Page 24\n" + 
				"<d2lm:comparisonOperator>lessThanOrEqualTo</d2lm:comparisonOperator>\n" + 
				"<d2lm:vehicleLength>11.6</d2lm:vehicleLength>\n" + 
				"</d2lm:lengthCharacteristic>\n" + 
				"</d2lm:specificVehicleCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:measurementSpecificCharacteristics index=\"6\">\n" + 
				"<d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:specificLane>allLanesCompleteCarriageway</d2lm:specificLane>\n" + 
				"<d2lm:specificMeasurementValueType>trafficFlow</d2lm:specificMeasurementValueType>\n" + 
				"<d2lm:specificVehicleCharacteristics>\n" + 
				"<d2lm:lengthCharacteristic>\n" + 
				"<d2lm:comparisonOperator>greaterThan</d2lm:comparisonOperator>\n" + 
				"<d2lm:vehicleLength>11.6</d2lm:vehicleLength>\n" + 
				"</d2lm:lengthCharacteristic>\n" + 
				"</d2lm:specificVehicleCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"<!-- Note: if only one Flow value is supplied (this can occur if not\n" + 
				"enough loops are available to determine vehicle-specific measurements)\n" + 
				"the following is used in place of the previous vehicle-specific values: -->\n" + 
				"<d2lm:measurementSpecificCharacteristics index=\"7\">\n" + 
				"<d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:specificLane>allLanesCompleteCarriageway</d2lm:specificLane>\n" + 
				"<d2lm:specificMeasurementValueType>trafficFlow</d2lm:specificMeasurementValueType>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"</d2lm:measurementSpecificCharacteristics>\n" + 
				"<d2lm:measurementSiteLocation xsi:type=\"d2lm:Point\">\n" + 
				"<d2lm:locationForDisplay>\n" + 
				"<d2lm:latitude>"
				+ LATITUDE
				+ "</d2lm:latitude>\n" + 
				"<d2lm:longitude>"
				+ LONGITUDE
				+ "</d2lm:longitude>\n" + 
				"</d2lm:locationForDisplay>\n" + 
				"<d2lm:pointAlongLinearElement>\n" + 
				"<d2lm:linearElement xsi:type=\"d2lm:LinearElementByCode\">\n" + 
				"<d2lm:linearElementReferenceModel>NTIS_Network_Links</d2lm:linearElementReferenceModel>\n" + 
				"<d2lm:linearElementReferenceModelVersion>[NTIS Model version]\n" + 
				"</d2lm:linearElementReferenceModelVersion>\n" + 
				"<d2lm:linearElementIdentifier>[NTIS Link ID]</d2lm:linearElementIdentifier>\n" + 
				"</d2lm:linearElement>\n" + 
				"<d2lm:distanceAlongLinearElement xsi:type=\"d2lm:DistanceFromLinearElementStart\">\n" + 
				"<d2lm:distanceAlong>[chainage]</d2lm:distanceAlong>\n" + 
				"</d2lm:distanceAlongLinearElement>\n" + 
				"</d2lm:pointAlongLinearElement>\n" + 
				"</d2lm:measurementSiteLocation>\n" + 
				"</d2lm:measurementSiteRecord>\n" + 
				"</d2lm:measurementSiteTable>";
		
		DatexReader reader = new DatexReader();
		List<LatLongMetaData> readData = reader.read(datexModelStream, datexStream);
		assertEquals(SPEED, readData.get(0).getMetaData(SPEED_TAG));
		LatLong location = new SimpleLatLong(LATITUDE, LONGITUDE);
		assertEquals(location , readData.get(0).getLocation());
	}

}
