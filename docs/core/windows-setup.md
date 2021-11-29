# Windows Setup from Source

Download [cygwin](http://www.cygwin.com/) and click on the setup where you need to select wget, git and unzip.

Now you can choose to either [install GraphHopper](../web/quickstart.md) or if you plan to customize the source code [install it from source](./quickstart-from-source.md).

After that graphhopper web should start. After this open [http://localhost:8989/](http://localhost:8989/) in your browser.

### Troubleshooting
 * Make sure you have the latest JDK installed and not only the JRE
 * Using cygwin you might need to call java via /cygdrive/c/Programme/Java/jdk1.8.0_77/bin/java