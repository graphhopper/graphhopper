## PostGIS Reader
To create a GraphHopper Graph from a PostGIS table, do:

* Uncomment the PostGIS parameters in the config.yml and set the appropriate values. The PostGIS reader will only be used if all the PostGIS parameters are set.

 
* Create a Table or View in the specifed database and schema that has these necessary columns (refer to OSM documentation for the proper values of these columns) : 

``` 
osm_id, maxspeed, oneway, fclass, name, geom 
```

For example:

``` 
 create view philview 
   (osm_id,maxspeed,oneway,fclass,name,geom ) 
   as select id,0,oneway,'tertiary'::text,name,geom from phil;
```

* Start the GraphHopper server by adding the parameter ``/<path where graph will reside>/<table or view name>``. The example below will create a graph, philview-gh, in /Users/mbasa/t for the philveiw view of PostgreSQL:

``` 
./graphhopper.sh web /Users/mbasa/t/philview
```

* If the data in PostgreSQL changes and the graph has to be updated, just delete the created graph directory and restart GraphHopper using the above method.

