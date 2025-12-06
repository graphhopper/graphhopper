# Elevation

Per default elevation is disabled. But you can easily enable it e.g. via
`graph.elevation.provider: cgiar`. Or use other possibilities `srtm`, `gmted`, `sonny`, 
`multi` (combined cgiar and gmted), or `multi3` (combined cgiar, gmted and sonny).

Then GraphHopper will automatically download the necessary data for the area and include elevation 
for all vehicles except when using `sonny` - making also the distances a bit more precise. 

The default cache directory `/tmp/<provider name>` will be used. For large areas it is highly recommended to 
use a SSD disc, thus you need to specify the cache directory:
`graph.elevation.cache_dir: /myssd/ele_cache/`

## Custom Models

The `average_slope` and `max_slope` attributes of a road segment can be used to make your routing
elevation-aware, i.e. to prefer or avoid, to speed up or slow down your vehicle based on the elevation
change. See the [custom model](custom-models.md) feature.

## What to download and where to store it?

Except when using `sonny` all should work automatically, but you can tune certain settings like the location where the files are 
downloaded and e.g. if the servers are not reachable, then you set:
`graph.elevation.base_url`

For CGIAR the default URL is `https://srtm.csi.cgiar.org/wp-content/uploads/files/srtm_5x5/TIFF/`
and this is only accessibly if you specify the [full zip file](https://srtm.csi.cgiar.org/wp-content/uploads/files/srtm_5x5/TIFF//srtm_01_02.zip).

If the geographical area is small and you need a faster import you can change the default MMAP setting to:
`graph.elevation.dataaccess: RAM_STORE`

## CGIAR vs. SRTM

The CGIAR data is preferred because of the quality but is in general not public domain. 
But we got a license for our and our users' usage: https://graphhopper.com/public/license/CGIAR.txt

Using SRTM instead CGIAR has the minor advantage of a faster download, especially for smaller areas.

## Sonny's LiDAR Digital Terrain Models
Sonny's LiDAR Digital Terrain Models are available for Europe only, see https://sonny.4lima.de/. 
It is a very high resolution elevation data set, but it is **not free** to use! See the discussion at
https://github.com/graphhopper/graphhopper/issues/2823.
The DTM 1" data is provided on a Google Drive https://drive.google.com/drive/folders/0BxphPoRgwhnoWkRoTFhMbTM3RDA?resourcekey=0-wRe5bWl96pwvQ9tAfI9cQg. 
From there it needs to be manually downloaded and extracted into a persistent cache directory. Automatic download
is not supported due to Google Drive not providing support for hyperlinks on to the DTM data files.
The cache directory is expected to contain the DTM data files with the naming convention like 
"N49E011.hgt" for the area around 49°N and 11°E.
Sonny's LiDAR Digital Terrain Model `sonny` only works for countries in Europe, which are fully covered 
by the Sonny DTM 1" data. See https://sonny.4lima.de/map.png for coverage. In case the covered sonny 
data area does not match your graphhopper coverage area, make sure to use the `multi3` elevation provider.

## Custom Elevation Data

Integrating your own elevation data is easy and just requires you to implement the
ElevationProvider interface and then specify it via GraphHopper.setElevationProvider.
Have a look in the existing implementations for a simple overview of caching and DataAccess usage.
