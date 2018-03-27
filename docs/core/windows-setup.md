# Windows Setup from Source

Either get [Babun](http://babun.github.io/) which comes preinstalled with git and more. Or download [cygwin](http://www.cygwin.com/) and click on the setup where you need to select wget, git and unzip.

```bash
# go to your development area
$ cd /cygdrive/c/Dokumente und Einstellungen/peter/dev

# get the sources
$ git clone https://github.com/graphhopper/graphhopper.git

# go into graphhopper root
$ cd graphhopper

# and execute
$ ./graphhopper.sh -a web -i europe_germany_berlin.pbf
```

Now graphhopper web should start. After this open [http://localhost:8989/](http://localhost:8989/) in your browser.

### Troubleshooting
 * Make sure you have the latest JDK installed and not only the JRE
 * For me JAVA_HOME was not correct so I had to overwrite it before calling
   the `graphhopper.sh` script:
   ```bash
   export JAVA_HOME=/cygdrive/c/Programme/Java/jdk1.8.0_77
   ```
