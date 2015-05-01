package uk.co.ordnancesurvey.gpx.beans;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import org.alternativevision.gpx.beans.Waypoint;
import org.eclipse.jetty.util.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.graphhopper.GraphHopperGPXParserRouteTest;

public class RouteWayPoint {

	private static final Logger LOG = LoggerFactory
			.getLogger(RouteWayPoint.class);

	public Waypoint waypoint;

	public RouteWayPoint(Waypoint wp) {
		this.waypoint = wp;
	}

	public boolean equals(RouteWayPoint routeWayPoint) {

		if (routeWayPoint == this)
			return true;
		if (routeWayPoint == null)
			return false;

		boolean isEqual = false;
		Field[] fields = waypoint.getClass().getDeclaredFields();
		for (Field f : fields) {

			if (!f.getName().equalsIgnoreCase("time")) {

				try {
					String first = f.getName().substring(0, 1);
					String getter = "get"
							+ f.getName().replaceFirst(first,
									first.toUpperCase());
					Method m = waypoint.getClass().getMethod(getter,
							new Class[] {});

					Object thisValue = m.invoke(waypoint);
					Object thatValue = m.invoke(routeWayPoint.waypoint);

					if (f.getName() == "description") {
						// description string comparison ignoring case and
						// special char "'"

						try {
							String thisValueAsString = ((String) thisValue)
									.replaceAll("[^\\w]", "");
							String thatValueAsString = ((String) thatValue)
									.replaceAll("[^\\w]", "");
							isEqual = thisValueAsString
									.equalsIgnoreCase((thatValueAsString));
							
							if (!isEqual)
							{
								LOG.info("Way point Description "+thisValueAsString+" is not matching with "+thatValueAsString);
							}
						}

						catch (Exception e) {
							e.printStackTrace();
						}
					}
					if (thisValue instanceof String) {

					} else {
						isEqual = Objects.equals(thisValue, thatValue);
					}

					if (!isEqual) {
						break;
					}
				} catch (NoSuchMethodException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		if (isEqual) {
			if (null != routeWayPoint.waypoint.getExtensionData()) {
				for (String anExtensionDataKey : waypoint.getExtensionData()
						.keySet()) {

					isEqual = Objects.equals(waypoint
							.getExtensionData(anExtensionDataKey),
							routeWayPoint.waypoint
									.getExtensionData(anExtensionDataKey));

					if (!isEqual) {

						LOG.info("********Way point attribute "
								+ anExtensionDataKey
								+ " "
								+ waypoint.getExtensionData(anExtensionDataKey)
								+ " is not matching with expected value "
								+ routeWayPoint.waypoint
										.getExtensionData(anExtensionDataKey));

						break;
					}
				}
			}
		}

		return isEqual;
	}
}
