# Measurement

These tools make the output files of `./graphhopper.sh measurement your.pbf` visible in the browser.

## Installation

npm install http-server -g
cd graphhopper-root-where-measurement-files-are
http-server --cors

Now open measurement.html in your browser to see the raw graphs comparing the measurement files.
The files have a date and time assigned:

measurement2016-11-20_00_20_36.properties

this is parsed and used for the X-axis, so if no date can be found a fixed counter is used instead,
so make sure the lexicographical order is what you would expect.