Submit a new issue only if you are sure it is a missing feature or a bug. Otherwise discuss the topic on the 
[mailing list](http://graphhopper.com/#developers) first. For new translations or fixes to existing translations
please refer to [this documentation](https://github.com/graphhopper/graphhopper/blob/master/docs/core/translations.md).

Issues for newcomers are tagged with 
['good first issue'](https://github.com/graphhopper/graphhopper/labels/good%20first%20issue) 
and documentation issues are taged with 
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

For contributions like pull requests, bug fixes and translations please read and electronically sign 
the <a href="http://www.clahub.com/agreements/graphhopper/graphhopper">GraphHopper License Agreement</a>,
which gives not away your rights but it will make sure for others that you agree to the Apache License, Version 2.

## Syntax:

* Tell this your IDE or just use NetBeans which picks the format from pom.xml. E.g. no tabs - use 4 spaces instead!
* Follow the conventions you see used in the source already.

And in case we didn't emphasize it enough: we love tests!
