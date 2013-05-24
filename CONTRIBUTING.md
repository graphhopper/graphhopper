Before submitting a new issue discuss the topic on the mailing list. If you are sure
it is a missing feature or a bug you can skip this.

We love pull requests. Here's a quick guide:

1. [Fork the repo](https://help.github.com/articles/fork-a-repo), optionally create a feature branch

2. Run the tests. We only take pull requests with passing tests: `mvn test`

3. Add a test for your change. Only refactoring and documentation changes
require no new tests. If you are adding functionality or fixing a bug, we need
a test!

4. Make the test pass.

5. Push to your fork and [submit a pull request](https://help.github.com/articles/using-pull-requests)


Syntax:

* Tell this your IDE or just use NetBeans:
    * No tabs - use 4 spaces instead!
    * One line if statements shouldn't get brackets
* Use [Java conventions](http://www.oracle.com/technetwork/java/codeconv-138413.html), but instead of setters we use `property(Object value)`, instead getters we use `Object property()`
* Follow the conventions you see used in the source already.
And in case we didn't emphasize it enough: we love tests!
