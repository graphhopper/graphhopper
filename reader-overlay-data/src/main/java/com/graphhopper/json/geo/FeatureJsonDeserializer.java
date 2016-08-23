package com.graphhopper.json.geo;

import com.google.gson.*;
import com.graphhopper.util.shapes.BBox;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;

/**
 * Instructions how to read the different geometry types.
 *
 * @author Peter Karich
 */
public class FeatureJsonDeserializer implements JsonDeserializer<JsonFeature>
{
    @Override
    public JsonFeature deserialize( JsonElement json, Type type, JsonDeserializationContext context ) throws JsonParseException
    {
        JsonFeature feature = new JsonFeature();
        JsonObject obj = json.getAsJsonObject();

        if (obj.has("id"))        
            feature.id = obj.getAsString();
        else        
            feature.id = UUID.randomUUID().toString();        

        if (obj.has("properties"))
        {
            Map<String, Object> map = context.deserialize(obj.get("properties"), Map.class);
            feature.properties = map;
        }

        if (obj.has("bbox"))
            feature.bbox = parseBBox(obj.get("bbox").getAsJsonArray());

        if (obj.has("geometry"))
        {
            JsonObject geometry = obj.get("geometry").getAsJsonObject();

            if (geometry.has("coordinates"))
            {
                if (!geometry.has("type"))
                    throw new IllegalArgumentException("No type for non-empty coordinates specified");

                String strType = context.deserialize(geometry.get("type"), String.class);
                feature.type = strType;
                if ("Point".equals(strType))
                {
                    JsonArray arr = geometry.get("coordinates").getAsJsonArray();
                    double lon = arr.get(0).getAsDouble();
                    double lat = arr.get(1).getAsDouble();
                    if (arr.size() == 3)
                        feature.geometry = new Point(lat, lon, arr.get(2).getAsDouble());
                    else
                        feature.geometry = new Point(lat, lon);

                } else if ("MultiPoint".equals(strType))
                {
                    feature.geometry = parseLineString(geometry);

                } else if ("LineString".equals(strType))
                {
                    feature.geometry = parseLineString(geometry);

                } else
                {
                    throw new IllegalArgumentException("Coordinates type " + strType + " not yet supported");
                }
            }
        }

        return feature;
    }

    LineString parseLineString( JsonObject geometry )
    {
        JsonArray arr = geometry.get("coordinates").getAsJsonArray();
        boolean is3D = arr.size() == 0 || arr.get(0).getAsJsonArray().size() == 3;
        LineString lineString = new LineString(arr.size(), is3D);

        for (int i = 0; i < arr.size(); i++)
        {
            JsonArray pointArr = arr.get(i).getAsJsonArray();
            double lon = pointArr.get(0).getAsDouble();
            double lat = pointArr.get(1).getAsDouble();
            if (pointArr.size() == 3)
                lineString.add(lat, lon, pointArr.get(2).getAsDouble());
            else
                lineString.add(lat, lon);
        }
        return lineString;
    }

    private BBox parseBBox( JsonArray arr )
    {
        // The value of the bbox member must be a 2*n array where n is the number of dimensions represented 
        // in the contained geometries, with the lowest values for all axes followed by the highest values. 
        // The axes order of a bbox follows the axes order of geometries => lon,lat,ele
        if (arr.size() == 6)
        {
            double minLon = arr.get(0).getAsDouble();
            double minLat = arr.get(1).getAsDouble();
            double minEle = arr.get(2).getAsDouble();

            double maxLon = arr.get(3).getAsDouble();
            double maxLat = arr.get(4).getAsDouble();
            double maxEle = arr.get(5).getAsDouble();

            return new BBox(minLon, maxLon, minLat, maxLat, minEle, maxEle);

        } else if (arr.size() == 4)
        {
            double minLon = arr.get(0).getAsDouble();
            double minLat = arr.get(1).getAsDouble();

            double maxLon = arr.get(2).getAsDouble();
            double maxLat = arr.get(3).getAsDouble();

            return new BBox(minLon, maxLon, minLat, maxLat);
        } else
        {
            throw new IllegalArgumentException("Illegal array dimension (" + arr.size() + ") of bbox " + arr.toString());
        }
    }
}
