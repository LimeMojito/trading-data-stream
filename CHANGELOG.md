## 1.2.0

- FX2-87: Updated CSV framework with a base class so easier to extend.  Cleaned up Tick and Bar versions.  Updated Readme file and source documentation.  Added some log output in tests so can click through to generated CSV in Intellij
- FX2-234: Remove snapshot from example doco.
- FX2-234: Corrected more javadoc
- FX2-234: Corrected javadoc and reverted bad release.
- FX2-234: update doco version
- FX2-234: Cache primer.  Reassessed dukascopy, and it appears that rate limit has been greatly improved.  The 500 on a zero length is intermittent.  Updated documentation, added example for using cache primer.  Lowered the logging for zero length files.
- FX2-234: Add retry ability to cache searches.  Noticed that occasional 500 occurs on empty files.  Building a cache primer to test limit of rates again.
- FX2-234: Revert version change due to failed release build II the revenge.  Added lock to teamcity so damn release plugin doesn't cause concurrent builds.
- FX2-234: Revert version change due to failed release build.

## 1.1.0

- Merged in feature/bar-by-count (pull request #1)
- FX2-234: Readme.md changelog.
- FX2-234: Fixed cache stats print if 0 accesses.  Updated Readme.md with next version.
- FX2-234: Bar search backwards.  Made it in memory with a resort to solve the search dates and gaps problem.  Added boundary tests, updated documentation.
- FX2-234: Bar search forward.  Search through gaps, confirm gap handling.  Updated some logging statements as they were misleading.  More documentation.
- FX2-234: Better grammar.
- FX2-234: Exposed the default beginning of time in DukascopySearch.java
- FX2-234: Add beginning of time to stop endless searching.
- FX2-234: Correct javadoc warning.
- FX2-234: Fix javadoc from failed release.

## 1.0.0

- FX2-234: Clean up readme format.
- FX2-234: Fix for cache size test out of order.
- FX2-234: License updates.
- FX2-234: Cache statistics, CSV format with better time header for bars.  Example CLI shook out.  Readme.md on how to use.
- FX2-234: Refactor naming of modules
- FX2-234: Tests completed!
- FX2-234: Tightened unit tests.  Reduced permit rate as dukascopy threw a 500 on a loop test.
- FX2-234: Tightened unit tests.  Removed sneaky throws where we have NoSuchElementException in the API.
- FX2-234: Documentation update and move of interfaces into owning classes.
- FX2-234: Refactored packaging to be more explicit that this is model space.  Generalized dukascopy utilities.  Switched from LocalDate to instant based times for ease of matching UTC.  Updated logic in stream combiner.
- FX2-234: Reconfigured logback
- FX2-234: Port from trading-model.

