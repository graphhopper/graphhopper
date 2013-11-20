GraphHopper for iPhone and iPad
=================

It looks like we can make an offline road routing engine for iPhone etc!
Currently I have not tested it on a real device but it works on my linux box (except the slf4j logging)!
Thanks to [RoboVM](http://www.robovm.org/) ! 
Read from [others](http://jaxenter.com/one-to-watch-robovm-cracking-the-java-ios-development-conundrum-48406.html) about RoboVM.

Current discussion is [here](https://groups.google.com/forum/#!topic/robovm/cfCEITXgqLo)


Make it working
=========

 1. create GraphHopper files via GraphHopper-java: `./graphhopper.sh import <your-osm-file>`
 2. create the jar necessary for ios: `cd ios; mvn -DskipTests=true install assembly:single`  
 3. Download [RoboVM](http://www.robovm.org/docs.html)
 4. run compiled project on linux or change os setting: `export ROBO=/pathto/robo; run.sh`

The result on the command line should be:
graph:59105 MMAP_STORE
explored: 320 nodes

Include in your iOS project and report any problems or success stories!

License
=======

GraphHopper is still Apache but also the runtime necessary for RoboVM is under the Apache License v2.0!