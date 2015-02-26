# Get Demo

[Download GraphHopper Demo APK](http://graphhopper.com/#download)

# Set-up Development

As starting point you can use [the demo project](https://github.com/graphhopper/graphhopper/tree/master/android) 
which can be used from Android Studio, NetBeans, gradle or maven.

Before the installation fetch the source, the OpenStreetMap data and the dependencies:

```bash
$ git clone git://github.com/graphhopper/graphhopper.git graphhopper
$ cd graphhopper
$ ./graphhopper.sh import your-area.pbf
```

## Android Studio

Please read [here](./android-studio-setup.md) for a detailed instruction.

## None-Android Studio

Download the [Android SDK](http://developer.android.com/sdk/installing/index.html?pkg=tools) and 
go to the Android SDK Manager and install at least 2.3 (API 9).

### Maven or NetBeans
 1. Download [Maven SDK Deployer](https://github.com/mosabua/maven-android-sdk-deployer) and execute `mvn install -P 2.3` - it uses [Android Maven Plugin](http://code.google.com/p/maven-android-plugin/wiki/GettingStarted) under the hood where you need to set up ANDROID_HOME
 2. Now do `./graphhopper.sh android`

### Gradle

```bash
$ cd graphhopper/android
$ ./gradlew clean build
# push to device, start manually
$ gradle installDebug
```

## Maps

Now that you have a running android app you need to copy somehow the routing and maps data. 

 1. [Download the raw openstreetmap file](http://download.geofabrik.de/openstreetmap/) - you'll need that only for the next step to create the routing data
 2. Execute `./graphhopper.sh import <your-osm-file>`. This creates the routing data
 3. [Download a map](http://download.mapsforge.org/maps/) e.g. berlin.map
 4. Copy berlin.map into the created berlin-gh folder
 5. Optional Compression Step: Bundle a graphhopper zip file via `cd berlin-gh; zip -r berlin.ghz *`
 6. Now copy the berlin-gh folder from step 4 (or the .ghz file from step 5) to your Android device. /[download-folder]/graphhopper/maps, where the download-folder can e.g. be /mnt/sdcard/download or /storage/sdcard/Download/ - e.g. use [SSHDroid](https://play.google.com/store/apps/details?id=berserker.android.apps.sshdroid): `scp -P 2222 berlin.ghz root@$URL:/mnt/sdcard/download/graphhopper/maps/`

## Limitations

 * You have to create the graphhopper folder on your desktop and copy it to the Android storage.

 * [A memory bound a* algoritm](http://en.wikipedia.org/wiki/SMA*) is not yet implemented so you can use disableShortcuts only for small routes.

## Problems

If you encounter problems like 'trouble writing output: Too many methods: 72332; max is 65536.' or you 
want to reduce the size of the jar/apk size you can try to apply autojar on trove4j:

```bash
java -jar autojar-2.1/autojar.jar -o trove4j-stripped.jar -c $TROVE_SRC/target/classes @trove-class.list
```

where trove-class.list is a file with the required classes for GraphHopper as content:

```text
gnu.trove.list.TDoubleList.class
gnu.trove.list.TIntList.class
gnu.trove.list.array.TDoubleArrayList.class
gnu.trove.list.array.TIntArrayList.class
gnu.trove.map.TIntObjectMap.class
gnu.trove.map.hash.TIntObjectHashMap.class
gnu.trove.map.hash.TIntIntHashMap.class
gnu.trove.set.hash.TIntHashSet.class
gnu.trove.iterator.TIntIterator.class
gnu.trove.procedure.TIntProcedure.class
gnu.trove.procedure.TObjectProcedure.class
gnu.trove.stack.array.TIntArrayStack.class
```


## Example

Routes for areas of up to 500km^2 are calculated in under 5s with the help of Contraction Hierarchies

![simple routing](http://karussell.files.wordpress.com/2012/09/graphhopper-android.png)