# Level

When routing indoors or in any place with several levels, you can now force a route to start or end at a given level. It also works for any number of waypoints.

A level with the value 'NaN' won't be enforced.

**You must have added the "level" encoded value to your configuration in order for this feature to work.**

Levels are encoded on 11 bit decimals (factor 1), giving values ranging from -40.0 to 164.7.

In practice, the graph's encoded values might sometimes contain "half-floors". E.g. Ways tagged `level=1;2` will have the encoded value `level=1.5` in graphhopper, allowing you to use them in custom models or post-processing.

## API usage

TODO :

Two examples, one with regular GET and another with POST.