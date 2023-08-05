Submit a new issue only if you are sure it is a missing feature or a bug. For questions or if you are unsure [discuss the topic in our forum](https://discuss.graphhopper.com/c/graphhopper). 

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
4. Make sure to create a new branch whenever you are contributing to the isuues and make pull request 

5. Make the test(s) pass.

6. Push to your fork and [submit a pull request](https://help.github.com/articles/using-pull-requests). A button should
appear on your fork its github page afterwards.

## License Agreement

All contributions like pull requests, bug fixes, documentation changes and translations fall under the Apache License and contributors agree to our
<a href="https://www.graphhopper.com/code-of-conduct/">contributor covenant code of conduct</a>.

## Code formatting

We use IntelliJ defaults. For eclipse there is this [configuration](https://github.com/graphhopper/graphhopper/files/481920/GraphHopper.Formatter.zip). 
For other IDEs we use [editorconfig](https://github.com/graphhopper/graphhopper/pull/2791) or the following rules:

 * Java indent is 4 spaces
 * Line width is 100 characters
 * The rest is left to Java coding standards but disable "auto-format on save" to prevent unnecessary format changes. 
 * Currently we do not care about import section that much, avoid changing it
 * Unix line endings (should be handled via git)
 * See discussion in [#770](https://github.com/graphhopper/graphhopper/issues/770)

And in case we didn't emphasize it enough: we love tests!
