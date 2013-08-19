# either plugin your device and copy the files via USB-detected-device or use the following method
# when starting an ssh server like sshdroid

URL=192.168.0.102
GH=/sdcard/graphhopper/maps/

# if you install sshdroid you can scp your files to your android device

# wget http://mapsforge.googlecode.com/files/berlin.map
# alternatives: http://download.mapsforge.org/maps/
scp -P 2222 berlin.map root@$URL:$GH

# wget http://download.geofabrik.de/osm/europe/germany/berlin.osm.bz2
# bunzip2 berlin.osm.bz2
# cd ../graphhopper
# ./run.sh /media/SAMSUNG/maps/berlin.osm
scp -r -P 2222 berlin-gh/ root@$URL:$GH