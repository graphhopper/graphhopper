# GraphHopper GTFS


# Graph schema

![Graph schema](pt-model.png)

We see three trips of two routes (top to bottom: route 1, route 1, route 2) and three stations (colored groups).
The ENTER_TEN and LEAVE_TEN edges are the entry and exit to the time expanded network proper. They enforce that
you are put on the correct node in the timeline when searching forward and backward, respectively. The STOP_ENTER
and STOP_EXIT nodes are regular, spatial nodes which can be connected to the road network.

The BOARD edge checks if the trip is valid on the requested day: Our graph is "modulo operating day", but
a pure time expanded graph whic is fully unrolled is also possible. Then that check could go away. It also
counts the number of boardings.

The TRANSFER edge ensures that the third departure is only reachable from the first arrival but not from the second one.

# Attribution

* GraphHopper GTFS uses [GTFS Feed](https://opendata.rnv-online.de/datensaetze/gtfs-general-transit-feed-specification/resource/rnv-gtfs) of [Rhein-Neckar-Verkehr GmbH (rnv)](http://www.rnv-online.de), licensed under [dl-de/by-2-0](https://www.govdata.de/dl-de/by-2-0). This GTFS feed is used as a test dataset.