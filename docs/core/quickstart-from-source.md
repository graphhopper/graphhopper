# GraphHopper - Quick Start Guide for Developers

[Watch this video](https://www.youtube.com/watch?v=HBVe_E5j0TM) for a simple introduction.

## Try out

For a start which requires only the JRE have a look [here](../web/quickstart.md). 
Windows user can find a quick guide [here](./windows-setup.md).
People with IDE knowledge can directly jump to [Start Development](#start-development)

Now, before you proceed install git and jdk8, then do:

```bash
$ git clone git://github.com/graphhopper/graphhopper.git
$ cd graphhopper; git checkout master
$ ./graphhopper.sh -a web -i europe_germany_berlin.pbf
now go to http://localhost:8989/
```

  1. These steps make the Berlin area routable. It'll download and unzip the osm file for you.
  2. It builds the graphhopper jars. If Maven is not available it will automatically download it.
  3. Then it creates routable files for graphhopper in the folder europe_germany_berlin-gh. It'll skip this step if files are already present.
  4. Also check the instructions for [Android](../android/index.md)

For you favourite area do

```bash
$ ./graphhopper.sh -a web -i europe_france.pbf -o france-gh
$ ./graphhopper.sh -a web -i north-america_us_new-york.pbf -o new-york-gh
# the format follows the link structure at http://download.geofabrik.de
```

For larger maps you might need to allow the JVM to access more memory. For example for 2GB you can do this using:
```bash
$ export JAVA_OPTS="-Xmx2g -Xms2g"
```
before running `graphhopper.sh`.

## Start Development

Open the project in your IDE, first class IDEs are NetBeans and IntelliJ where no further setup is required.

### Running / Debbuging with IntelliJ

![intelliJ run config](./images/intellij-run-config.png)

Go to `Run->Edit Configurations...` and set the following to run GraphHopper from within IntelliJ:
```
Main class: com.graphhopper.http.GraphHopperApplication
VM options: -Xms1000m -Xmx1000m -server -Dgraphhopper.datareader.file=[your-area].osm.pbf -Dgraphhopper.graph.location=./[your-area].osm-gh
Program arguments: server config.yml
```

Note:  you might need to modify Xms and Xmx values, for larger areas you might want to use higher values.


### Contribute

See this [guide](https://github.com/graphhopper/graphhopper/blob/master/.github/CONTRIBUTING.md) on how to contribute.

### Java, Embedded Usage

Have a look into the [Java API documentation](../index.md#developer) for further details e.g. how [GraphHopper can
be embedded](./routing.md) into your application and how you create a [custom weighting](./weighting.md).

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

### JavaScript

When developing the UI for GraphHopper you need to enable serving files
directly from local disc via your config.yml:

```yml
assets:
  overrides:
    /maps: web/src/main/resources/assets/
```

The run the graphhopper.sh script with the web action and open the browser at
`localhost:8989`. You should see something like [GraphHopper Maps](https://graphhopper.com/maps/).

To setup the JavaScript environment install the node package manager (npm):

For linux do
```bash
curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.33.9/install.sh | bash
# close and reopen terminal now
nvm install --lts
nvm use --lts
```

For windows download either [nvm](https://github.com/coreybutler/nvm-windows) or [node](https://nodejs.org/en/download/) directly.

Then generate the main.js
```bash
# git clone https://github.com/graphhopper/graphhopper.git
cd graphhopper/web
# download required packages:
npm install
npm test
# overwrites main.js
npm run bundle
```

There are more npm commands e.g. to change the main.js on the fly or create an uglified main.js for
production.

```bash
cd web

# For development just use watchify and all changes will be available on refresh:
npm run watch

# bundle creates the main file
npm run bundle

# create main.js for debugging
npm run bundleDebug

# create main.js for production and specify as CLI parameter `export NODE_ENV=development` which `options_*.js` file should be selected
npm run bundleProduction

# Forcing consistent code style with jshint:
npm run lint

# see the package.json where more scripts are defined
```

### Experimental

If you need **offline** routing in the browser like for smaller areas or hybrid routing solution
then there is a highly experimental version of GraphHopper using TeaVM. 
Have a look into this [blog post](http://karussell.wordpress.com/2014/05/04/graphhopper-in-the-browser-teavm-makes-offline-routing-via-openstreetmap-possible-in-javascript/) 
for a demo and more information.

### Android Usage
 
For details on Android-usage have a look into this [Android site](../android/index.md)

### Swing and Desktop Usage

You can use Graphhopper on the Desktop with the help of mapsforge too. No example code is given yet 
but with the Android example combined with the Desktop example of the mapsforge project it should not be hard.

For smallish graph (e.g. size of Berlin) use a RAMDataAccess driven GraphStorage (loads all into memory).
For larger ones use the ContractionHierarchies preparation class and MMapDataAccess to avoid OutOfMemoryErrors if you have only few RAM. 

Raspberry Pi usage is also possible. Have a look into this [blog post](https://karussell.wordpress.com/2014/01/09/road-routing-on-raspberry-pi-with-graphhopper/).
