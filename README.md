## Farmy Vehicle Route Problem Solution (Farmy VRP Solution)
https://farmyag.atlassian.net/wiki/spaces/IT/pages/2109866034/Farmy+Vehicle+Route+Solution+FarmyVRP
### Requirements
* Java 1.8
    * Recommendation use jabba(java version manager https://github.com/shyiko/jabba)
* MAVEN
### Installing instructions

* Clone github repo (https://github.com/mtoribio/graphhopper.git)
* Switch branch to `test-mte-1.0` or `mte-add-depot-to-pointlist`
* Now runs: `./graphhopper.sh -a web -i switzerland-latest.osm.pbf -o eu_sw/ --host 0.0.0.0 --port 8089`
    * If you get 404 error, download map manually and move it to root project path: http://download.geofabrik.de/europe/switzerland-latest.osm.pbf
* It should be running in 8089

### For Farmy

If you want that routes works you need a new db and remove

`
          latitude = 47.3775499,
          longitude = 8.4666755
`

from `lib/tasks/development.rake`

