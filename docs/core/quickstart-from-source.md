# GraphHopper - Quick Start Guide for Developers

## Try out

For a start which requires only the JRE have a look [here](../web/quickstart.md). 
Windows user can find a quick guide [here](./windows-setup.md). 

Now, before you proceed install git and jdk7 or 8, then do:

```bash
$ git clone git://github.com/graphhopper/graphhopper.git
$ cd graphhopper; git checkout master
$ ./graphhopper.sh web europe_germany_berlin.pbf
now go to http://localhost:8989/
```

  1. These steps make the Berlin area routable. It'll download and unzip the osm file for you.
  2. It builds the graphhopper jars. If Maven is not available it will automatically download it.
  3. Then it creates routable files for graphhopper in the folder europe_germany_berlin-gh. It'll skip this step if files are already present.
  4. Also check the instructions for [Android](../android/index.md)

For you favourite area do

```bash
$ ./graphhopper.sh web europe_france.pbf
$ ./graphhopper.sh web north-america_us_new-york.pbf
# the format follows the link structure at http://download.geofabrik.de
```

## Start Development

Open the project with NetBeans, IntelliJ or enable Maven in your IDE. 
[Maven](http://maven.apache.org/download.cgi) is downloaded to ```graphhopper/maven``` if not 
installed when executing graphhopper.sh.

### NetBeans, IntelliJ, Eclipse

 * For IntelliJ you need to import the code formatting settings (File|Import Settings). Load them from `core/files/intellij-settings.jar`. See the [special setup](../android/android-studio-setup.md) for Android if using Android studio.
   * For IntelliJ15 there is also a formatting description, that should be imported using (Preferences|Editor|Code Style|Java|Manage|Import) from `core/files/IntelliJ15GraphHopperFormatting.xml`. 
 * For Eclipse please refer to [this separate setup documentation](./eclipse-setup.md).
 * Nothing special needs to be done for NetBeans.

### Java, Embedded Usage

Have a look into the [Java API documentation](./) for further details e.g. how [GraphHopper can
be embedded](./routing.md) into your application and like a [custom weighting](./weighting.md) 
can be implemented.

Look [here](http://graphhopper.com/#community) for the maven snippet to use GraphHopper in your
application. To use an unreleased snapshot version of GraphHopper you need the following snippet in your pom.xml
as those versions are not in maven central:

```xml
    <repositories>
        <repository>
            <id>sonatype-oss-public</id>
            <url>https://oss.sonatype.org/content/groups/public/</url>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
```

### Java, Routing Server Usage

The Web API documentation is [here](../web). 
We provide Java client code [here](https://github.com/graphhopper/graphhopper/blob/d70b63660ac5200b03c38ba3406b8f93976628a6/web/src/main/java/com/graphhopper/http/GraphHopperWeb.java#L43)
to query the routing server.

The routing API (json,jsonp,gpx) is optimized regarding several aspects:
 * It tries to return a smallish data set (encoded polyline, gzip filter)
 * It enables cross-site scripting on the server- and client-site
 * The JavaScript client utilizes the jquery Deferred object to chain ajax requests and avoids browser UI blocking when resolving locations in parallel.

#### Routing Service Deployment

If you want to setup graphhopper on your own server, we have a separate information page [Deploy](./deploy.md).

### JavaScript Usage

For an example of how to use graphhopper in a web application see the 
[web subfolder](https://github.com/graphhopper/graphhopper/tree/master/web)

The routing server can be queried from [JavaScript](https://github.com/graphhopper/graphhopper/blob/d70b63660ac5200b03c38ba3406b8f93976628a6/web/src/main/webapp/js/ghrequest.js)
as well. You can see this in action at [GraphHopper Maps](https://graphhopper.com/maps/).

If you need **offline** routing in the browser like for smaller areas or hybrid routing solution
then there is a highly experimental version of GraphHopper using TeaVM. 
Have a look into this [blog post](http://karussell.wordpress.com/2014/05/04/graphhopper-in-the-browser-teavm-makes-offline-routing-via-openstreetmap-possible-in-javascript/) 
for a demo and more information.

If you want to change the JavaScript you have to setup the JavaScript environment - 
i.e. install the node package manager (npm):

For linux do
```bash
curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.29.0/install.sh | bash
# close and reopen terminal now
nvm install 4.2.2
nvm use 4.2.2
```

For windows download either [nvm](https://github.com/coreybutler/nvm-windows) or [node](https://nodejs.org/en/download/) directly.

Then create the main.js
```bash
# git clone https://github.com/graphhopper/graphhopper.git
cd graphhopper/web
# download required packages:
npm install
npm test
# create main.js
npm run bundle
```

Finally start GraphHopper e.g. via `graphhopper.sh` script and with the browser open `localhost:8989`.

There are more npm commands e.g. to change the main.js on the fly or create an uglified main.js for production:
```bash
# For development just use watchify:
npm run watch

# bundle creates the main file
npm run bundle

# create main.js for debugging
npm run bundleDebug

# create main.js for production and specify as CLI parameter `export NODE_ENV=development` which `options_*.js` should be selected
npm run bundleProduction

# Forcing consistent code style with jshint:
npm run jshint

# see the package.json where more scripts are defined
```

### Android Usage
 
For details on Android-usage have a look into this [Android site](../android/index.md) or at the
[Android example](https://github.com/graphhopper/graphhopper/tree/master/android)

### Swing and Desktop Usage

You can use graphhopper on the Desktop with the help of the latest mapsforge swing library too. No example code
yet but with the Android example combined with the Desktop example of the mapsforge project it should not be
that hard.

For smallish graph (e.g. size of Berlin) use a RAMDataAccess driven GraphStorage (loads all into memory).
For larger ones use the ContractionHierarchies preparation class and MMapDataAccess to avoid OutOfMemoryErrors. 

Raspberry Pi usage is also possible. Have a look into this [blog post](https://karussell.wordpress.com/2014/01/09/road-routing-on-raspberry-pi-with-graphhopper/).

## Technical Details

Have a look in the more [technical documentation](./technical.md) or the [low level API](./low-level-api.md).

Further Links
---------------
 * [Spatial Key](http://karussell.wordpress.com/2012/05/23/spatial-keys-memory-efficient-geohashes/)
 * [Author@Twitter](https://twitter.com/timetabling)
