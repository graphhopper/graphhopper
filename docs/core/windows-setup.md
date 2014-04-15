# Windows Setup from Source

Download [cygwin](http://www.cygwin.com/) to execute bash scripts. Click on the setup and select wget, git and unzip

```bash
# go to your development area
$ cd /cygdrive/c/Dokumente und Einstellungen/peter/dev

# get the sources
$ git clone https://github.com/graphhopper/graphhopper.git

# go into graphhopper root
$ cd graphhopper

# and execute
$ ./graphhopper.sh web europe_germany_berlin.osm
```

Now graphhopper web should start. After this open [http://localhost:8989/](http://localhost:8989/) in your browser.

### Troubleshooting
 * Make sure you have the JDK installed (6,7 or 8) and not only the JRE
 * For me JAVA_HOME was not correct so I had to overwrite it:
   ```bash
   $ export JAVA_HOME=/cygdrive/c/Programme/Java/jdk1.7.0_17
   $ ./graphhopper.sh web europe_germany_berlin.osm
   ```