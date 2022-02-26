# GraphHopper - Quick Start Guide for Developers

[Watch this video](https://www.youtube.com/watch?v=HBVe_E5j0TM) for a simple introduction.

## Try out

For a start which requires only the JRE have a look [here](../web/quickstart.md). 
Windows users will need Cygwin - find more details [here](./windows-setup.md).

To proceed install `git` and `openjdk8` or `openjdk11`. Get the a jdk from your package manager, 
[AdoptOpenJDK](https://adoptopenjdk.net/) or [Red Hat](https://github.com/ojdkbuild/ojdkbuild/releases).

Then create the jar from sources via:

```bash
# first download some road data
wget http://download.geofabrik.de/europe/germany/berlin-latest.osm.pbf
# now get the source code and create the jar in web/target
git clone git://github.com/graphhopper/graphhopper.git
cd graphhopper
git checkout master # if you prefer a less moving branch you can use e.g. 4.x
mvn clean install -DskipTests
# start GraphHopper
java -Ddw.graphhopper.datareader.file=berlin-latest.osm.pbf -jar web/target/graphhopper-web-*.jar server config-example.yml
# This does mainly 3 things:
# - it creates routable files for graphhopper in the folder graph-data (see the config.yml)
# - it creates data for a special routing algorithm to dramatically improve query speed. It skips this and the previous step if these files are already present.
# - it starts the web service to service the UI and also the many endpoints like /route
# After 'Server - Started' appears go to http://localhost:8989/ and you should see something similar to GraphHopper Maps: https://graphhopper.com/maps/
```

To use a different area make sure you use a different folder instead of graph-data or remove this.

For larger maps you need to add some parameters to the JVM: `java -Xmx10g -Xms10g ...`.
See [the deployment section](deploy.md) for more details.

## Start Development

First you need to run the commands given in section [Try out](#try-out), if you have not done so yet.

Then open the project in your IDE, first class IDEs are NetBeans and IntelliJ where no further setup is required.

### Running & Debbuging with IntelliJ

![intelliJ run config](./images/intellij-run-config.png)

Go to `Run->Edit Configurations...` and set the following to run GraphHopper from within IntelliJ:
```
Main class: com.graphhopper.application.GraphHopperApplication
VM options: -Xms1g -Xmx1g -server -Ddw.graphhopper.datareader.file=[your-area].osm.pbf -Ddw.graphhopper.graph.location=./[your-area].osm-gh
Program arguments: server config.yml
```

If IntelliJ shows an error like: 
```
Error:(46, 56) java: package sun.misc does not exist
```
go to `Settings -> Build,Execution,Deployment -> Compiler -> Java Compiler` and disable: 
`Use '--release' option for cross compilation (java 9 and later)`. c.f. #1854

### Contribute

See this [guide](../../CONTRIBUTING.md) on how to contribute.

### Java, Embedded Usage

Have a look into the [Java API documentation](../index.md#developer) for further details e.g. how [GraphHopper can
be embedded](./routing.md) into your application and how you create a [custom weighting](./weighting.md).

Look [here](https://github.com/graphhopper/graphhopper#maven) for the maven snippet to use GraphHopper in your
application.

To use an **unreleased** snapshot version of GraphHopper you need the following snippet in your pom.xml
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

### Web UI (JavaScript)

Running `mvn package` from the root folder will install a local copy of node/npm and build the javascript bundle for GH
maps. You just need to start the server and GH maps and if you use the default port GH maps will be visible at
`http://localhost:8989/`.

To develop the web UI running the whole maven build usually takes too long so here are the separate steps that you need
to perform when you make changes to the JavaScript code:

1. install the [node package manager](https://github.com/nvm-sh/nvm#install--update-script). For windows
   use [nvm-windows](https://github.com/coreybutler/nvm-windows).
2. Build the Web UI: `cd web-bundle && npm install && npm run bundle` which results in the `main.js` file
3. Restart the GH server so it picks up the latest version of the UI bundle

You can achieve an even faster development cycle by running `npm run watch` which will update `main.js` whenever you
make changes to one of the .js files. To hot-reload your changes in the browser the best option is to serve GH maps from
a separate server like live-server. You can do this by running `npm run serve` from a separate terminal and pointing the
routing.host property in src/main/resources/com/graphhopper/maps/js/config/options.js to your GH server:

```js
...
routing: {
   host: 'http://localhost:8989', api_key
:
   ''
}
...
```

Re-building `main.js` on every change might cause your IDE (like IntelliJ) to re-index the file all the time. Therefore
it is a good idea to remove `main.js` from your editor's index. For example in IntelliJ right-click the file and choose
`Mark as plain text`.

The following npm commands are available in the `web-bundle` directory:

```bash
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

### Swing and Desktop Usage

You can use Graphhopper on the Desktop with the help of mapsforge too. No example code is given yet.

For smallish graph (e.g. size of Berlin) use a RAMDataAccess driven GraphStorage (loads all into memory).
For larger ones use the ContractionHierarchies preparation class and MMapDataAccess to avoid OutOfMemoryErrors if you have only few RAM. 
