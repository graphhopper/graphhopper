## Get Demo

[Download GraphHopper Demo APK](http://graphhopper.com/#download)

## Set-up Development
As starting point you can use [the demo project](https://github.com/graphhopper/graphhopper/tree/master/android) which can be used from Eclipse or NetBeans via maven command line.

### Before installation

```bash
$ git clone git://github.com/graphhopper/graphhopper.git graphhopper
$ cd graphhopper
$ ./graphhopper.sh import your-area.pbf
```

And go to the Android SDK Manager and install at least 2.2 (API 8)

**Either via Maven and Command line -> use this for NetBeans**
 1. Download [Maven SDK Deployer](https://github.com/mosabua/maven-android-sdk-deployer) and execute `mvn install -P 2.2` - it uses [Android Maven Plugin](http://code.google.com/p/maven-android-plugin/wiki/GettingStarted) under the hood where you need to set up ANDROID_HOME
 2. Install Mapsforge in your local repository via the provided script `scripts/maven-install-mapsforge.sh` - see some [explanations/issues](https://github.com/graphhopper/graphhopper/issues/122)
 3. Now do `./graphhopper.sh android`

**Or Eclipse**

Import Sources as Android project. If you want to customize graphhopper itself do:
 1. `cd graphhopper; ./graphhopper.sh eclipse`
 2. Refresh your Eclipse project and use it.

See [this](https://lists.openstreetmap.org/pipermail/graphhopper/2013-November/000501.html) for the discussion.

**Maps**

Now that you have a running android app you need to copy somehow the routing and maps data. 

 1. [Download the raw openstreetmap file](http://download.geofabrik.de/openstreetmap/) - you'll need that only for the next step to create the routing data
 2. Execute `./graphhopper.sh import <your-osm-file>`. This creates the routing data
 3. [Download a map](http://download.mapsforge.org/maps/) e.g. berlin.map
 4. Copy berlin.map into the created berlin-gh folder
 5. Optional Compression Step: Bundle a graphhopper zip file via cd berlin-gh;zip -r berlin.ghz *
 6. Now copy the berlin-gh folder from step 4 (or the .ghz file from step 5) to android /sdcard/graphhopper/maps - e.g. use [SSHDroid](https://play.google.com/store/apps/details?id=berserker.android.apps.sshdroid): scp -P 2222 berlin.ghz  root@$URL:/sdcard/graphhopper/maps/

## Limitations

 * For now OSMReader does not work on Android due to some javax.xml dependencies. But you can simply create the graphhopper folder on your desktop and copy them to the Android storage.

 * [A memory bound a* algoritm](http://en.wikipedia.org/wiki/SMA*) is not yet implemented so you can use disableShortcuts only for small routes. Let me know if you need this!

## Example

Routes for areas of up to 500km^2 are calculated in under 5s with the help of Contraction Hierarchies

![simple routing](http://karussell.files.wordpress.com/2012/09/graphhopper-android.png)