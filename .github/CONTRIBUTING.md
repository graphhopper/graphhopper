Submit a new issue only if you are sure it is a missing feature or a bug. Otherwise, [discuss the topic](http://graphhopper.com/#developers) first. 
For new translations or fixes to existing translations,
please refer to [this documentation](https://github.com/graphhopper/graphhopper/blob/master/docs/core/translations.md).

Issues for newcomers are tagged with 
['good first issue'](https://github.com/graphhopper/graphhopper/labels/good%20first%20issue) 
and documentation issues are tagged with 
['documentation'](https://github.com/graphhopper/graphhopper/labels/documentation).

## We love pull requests. Here's a quick guide:

1. [Fork the repo](https://help.github.com/articles/fork-a-repo) and create a branch for your new feature or bug fix.

2. Run the tests. We only take pull requests with passing tests: `mvn clean test verify`

3. Add at least one test for your change. Only refactoring and documentation changes
require no new tests. Also make sure you submit a change specific to exactly one issue. If you have ideas for multiple 
changes please create separate pull requests.

4. Make the test(s) pass.

5. Push to your fork and [submit a pull request](https://help.github.com/articles/using-pull-requests). A button should
appear on your fork its github page afterwards.

## License Agreement

For contributions like pull requests, bug fixes and translations please read 
the <a href="https://graphhopper.com/agreements/individual-cla.html">GraphHopper License Agreement</a>, which includes our
<a href="https://graphhopper.com/agreements/cccoc.html">contributor covenant code of conduct</a>.
<a href="https://graphhopper.com/#contact">Send us</a> an email with the signed print out of this CLA. Or, if you prefer
the faster electronically method via signaturit.com, please send us an email with a request for this - 
keep in mind that this requires storing your Email there. The same applies if you want to sign a CLA for your whole company.

Note, our CLA does not influence your rights on your contribution but it makes sure for others that you agree to the Apache License, Version 2.
After this you'll appear in the <a href="CONTRIBUTORS.md">contributors list</a> and your pull request can also be discussed technically.

## Syntax:

* Tell this your IDE or just use NetBeans which picks the format from pom.xml. E.g. no tabs - use 4 spaces instead!
* Follow the conventions you see used in the source already.

And in case we didn't emphasize it enough: we love tests!
