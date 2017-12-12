# Elevation

Per default elevation is disabled. But you can easily enable it e.g. via
`graph.elevation.provider=cgiar`. Or use other possibilities `srtm`, `gmted`
or `multi` (combined cgiar and gmted).

Then GraphHopper will automatically download the necessary data for the area and include elevation 
for all vehicles - making also the distances a bit more precise. 

The default cache directory `/tmp/<provider name>` will be used. For large areas it is highly recommended to 
use a SSD disc, thus you need to specify the cache directory:
`graph.elevation.cache_dir=/myssd/ele_cache/`

## What to download and where to store it? 

All should work automatically but you can tune certain settings like the location where the files are 
downloaded and e.g. if the servers are not reachable, then you set:
`graph.elevation.base_url`

For CGIAR there are two URLs you can use: `http://droppr.org/srtm/v4.1/6_5x5_TIFs` and
`http://srtm.csi.cgiar.org/SRT-ZIP/SRTM_V41/SRTM_Data_GeoTiff/`
where the last one is only accessibly if you specify the 
[full zip file](http://srtm.csi.cgiar.org/SRT-ZIP/SRTM_V41/SRTM_Data_GeoTiff/srtm_01_02.zip)

If the geographical area is small and you need a faster import you can change the default MMAP setting to:
`graph.elevation.dataaccess=RAM_STORE`

## CGIAR vs. SRTM

The CGIAR data is preferred because of the quality but is in general not public domain. 
But we got a license for our and our users' usage: https://graphhopper.com/public/license/CGIAR.txt

Using SRTM instead CGIAR has the minor advantage of a faster download, especially for smaller areas.

## Custom Elevation Data

Integrating your own elevation data is easy and just requires you to implement the
ElevationProvider interface and then specify it via GraphHopper.setElevationProvider.
Have a look in the existing implementations for a simple overview of caching and DataAccess usage.