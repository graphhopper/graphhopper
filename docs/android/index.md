# Get Demo

[Download GraphHopper Demo APK](http://graphhopper.com/#community)

![simple routing](https://graphhopper.com/blog/wp-content/uploads/2016/10/android-demo-screenshot-2.png)

# Set-up Development

As starting point you can use [the demo project](https://github.com/graphhopper/graphhopper/tree/master/android) 
which can be used directly from Android Studio and NetBeans via gradle or maven.

Before the installation fetch the source, the OpenStreetMap data and the dependencies:

```bash
$ git clone git://github.com/graphhopper/graphhopper.git graphhopper
$ cd graphhopper
$ ./graphhopper.sh import your-area.pbf
```

## Android Studio

Please read [here](./android-studio-setup.md) for a detailed instruction.

## None-Android Studio

Download the [Android SDK](http://developer.android.com/sdk/installing/index.html?pkg=tools) and go to the Android SDK Manager and install the latest SDK.

### Maven or NetBeans
 1. Download [Maven Android SDK Deployer](https://github.com/simpligility/maven-android-sdk-deployer) and execute `mvn install -P 5.1` - it uses [Android Maven Plugin](http://simpligility.github.io/android-maven-plugin/) under the hood where you need to set up ANDROID_HOME
 2. Now do `./graphhopper.sh android`

### Gradle

```bash
$ cd graphhopper/android
$ ./gradlew clean build
# push to device, start manually
$ gradle installDebug
```

## Maps

Now that you have a running Android app you need to copy the routing and maps data to the device. 

 1. [Download the raw openstreetmap file](http://download.geofabrik.de/openstreetmap/) - you'll need that for the next step to create the routing data
 2. Execute `./graphhopper.sh import <your-osm-file>`. This creates the routing data
 3. [Download a map](http://download.mapsforge.org/maps/) e.g. berlin.map
 4. Copy berlin.map into the created berlin-gh folder
 5. Optional Compression Step: Bundle a graphhopper zip file via `cd berlin-gh; zip -r berlin.ghz *`
 6. Now copy the berlin-gh folder from step 4 (or the .ghz file from step 5) to your Android device. /[download-folder]/graphhopper/maps, where the download-folder can e.g. be /mnt/sdcard/download or /storage/sdcard/Download/ - e.g. use [SSHDroid](https://play.google.com/store/apps/details?id=berserker.android.apps.sshdroid): `scp -P 2222 berlin.ghz root@$URL:/mnt/sdcard/download/graphhopper/maps/`

## Apps

### Pocket Maps

The open source Android App [Pocket Maps](https://github.com/junjunguo/PocketMaps) using GraphHopper and Mapsforge. It stands under MIT

### Locus Add-On

The developer of Locus has create a routing plugin for [locus](http://www.locusmap.eu/) the source code for the add-on is available [here](https://bitbucket.org/asamm/locus-map-add-on-graphhopper) and could be useful for other Map-apps too. The discussion is [here](http://forum.locusmap.eu/index.php?topic=4036.0).

### Cruiser App

The free offline map app [Cruiser](http://wiki.openstreetmap.org/wiki/Cruiser) is using GraphHopper routing and allows also other things.

## Frameworks

### OSMBonusPack

The [OSMBonusPack](https://github.com/MKergall/osmbonuspack) supports the GraphHopper Routing API via a [GraphHopperRoadManager](https://github.com/MKergall/osmbonuspack/wiki/WhichRoutingService) and also provides map tile integration for various providers.

### GraphHopper Directions API

The [GraphHopper Directions API Java client](https://github.com/graphhopper/directions-api-java-client/blob/master/README.md) supports fetching the route and instructions from official and custom servers.

## Limitations

 * You have to create the graphhopper folder on your desktop and copy it to the Android storage.

 * [A memory bound a* algoritm](http://en.wikipedia.org/wiki/SMA*) is not yet implemented so you can use disableShortcuts only for small routes.

## Problems

If you encounter problems like 'trouble writing output: Too many methods: 72332; max is 65536.' or you 
want to reduce the size of the jar/apk size you can try to apply autojar on hppc:

```bash
java -jar autojar-2.1/autojar.jar -o trove4j-stripped.jar -c $TROVE_SRC/target/classes @class.list
```

where class.list is a file with the required classes for GraphHopper as content:

```text
com.carrotsearch.hppc.IntArrayList
com.carrotsearch.hppc.IntObjectHashMap.class
com.carrotsearch.hppc.IntHashSet
com.carrotsearch.hppc.IntLongHashMap
com.carrotsearch.hppc.IntContainer
com.carrotsearch.hppc.LongHashSet
com.carrotsearch.hppc.LongObjectHashMap
com.carrotsearch.hppc.ObjectIntAssociativeContainer
com.carrotsearch.hppc.ObjectIntHashMap
com.carrotsearch.hppc.HashOrderMixing
com.carrotsearch.hppc.HashOrderMixingStrategy
com.carrotsearch.hppc.cursors.IntCursor
...
```


## Example

Routes for areas of up to 500km^2 are calculated in under 5s with the help of Contraction Hierarchies

![simple routing](http://karussell.files.wordpress.com/2012/09/graphhopper-android.png)
