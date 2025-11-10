# Licensing

Please refer to LICENSE.txt and DATA_DISCLAIMER.txt. This software is supplied as-is, use at your own risk and
information from using this software does NOT constitute financial advice.

# Maven Dependency

Library

```xml

<dependency>
    <groupId>com.limemojito.oss.trading.trading-data-stream</groupId>
    <artifactId>model</artifactId>
    <version>4.0.1</version>
</dependency>
```

Check out the source to see a working example in example-cli (Spring Boot command line).

There is an example spring configuration in TradingDataStreamConfiguration suitable for @Import. We suggest the
following dependencies for spring
boot

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-json</artifactId>
</dependency>
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-validation</artifactId>
</dependency>
        <!-- For CSV usage -->
<dependency>
<groupId>org.apache.commons</groupId>
<artifactId>commons-csv</artifactId>
</dependency>
        <!-- for S3 caching -->
<dependency>
<groupId>com.amazonaws</groupId>
<artifactId>aws-java-sdk-s3</artifactId>
</dependency>
```

# Java Tick Search Example

This example is using the standalone configuration suitable for testing. For Spring container
usage, please refer to TradingDataStreamConfiguration. Note the standalone setup should **not** be called in a spring
container.

```java
TradingSearch search = TradingDataStreamConfiguration.standaloneSetup();
try(
TradingInputStream<Tick> ticks = search("EURUSD", "2020-01-02T00:00:00Z", "2020-01-02T00:59:59Z")){
        ticks.

stream()
         .

foreach(t ->log.

info("{} {} bid: {}}, t.getMillisecondsUtc(), t.getSymbol(), t.getBid());
             }
```

Further examples
at https://limemojito.com/reading-dukascopy-bi5-tick-history-with-the-tradingdata-stream-library-for-java/

---

# Quickstart

```shell
mvn clean install
```

this produces the model jar, example-cli and a cache primer application.

## NZDUSD M5 bars for 2018-01-02T00:00:00Z -> 2018-01-02T00:59:59Z as CSV.

*note* that files are cached locally in ~/.dukascopy-cache. See LocalDukascopyCache.java for details.

```shell
java -jar example-cli/target/example-cli-4.0.1.jar --symbol=NZDUSD --period=M5 \
  --start=2018-01-02T00:00:00Z --end=2018-01-02T00:59:59Z --output=test-nz.csv  
```

## AUDUSD M5 bars for 2018-01-02T00:00:00Z -> 2018-01-02T00:59:59Z as CSV with S3 cache.

*note* this application cache chain is local <- s3 <- direct - ie the S3 cache is only used if it is not cached
locally (~/.dukascopy-cache).
See S3DukascopyCache.java and the chain configuration in DataStreamCli.java for details.

```shell
aws s3 mb s3://test-tick-bucket
java -jar example-cli/target/example-cli-4.0.1.jar --spring.profiles.active=s3 \
  --bucket-name=test-tick-bucket --symbol=AUDUSD --period=M5 --start=2018-01-02T00:00:00Z \
  --end=2018-01-02T00:59:59Z --output=test-au.csv  
```

## Prime a local cache with AUDUSD and EURUSD 2 months

```shell
java -jar cache-primer/target/cache-primer-4.0.1.jar --symbol=AUDUSD --symbol EURUSD \
  --start=2018-01-01T00:00:00Z --end=2018-03-01T00:59:59Z  
```

## Prime a s3 cache with AUDUSD and EURUSD 2 months

*note* this application cache chain is s3 <- local <- direct.
See S3DukascopyCache.java and the chain configuration in CachePrimer.java for details.

```shell
aws s3 mb s3://test-tick-bucket
java -jar cache-primer/target/cache-primer-4.0.1.jar --spring.profiles.active=s3 \
  --bucket-name=test-tick-bucket --symbol=AUDUSD --symbol EURUSD \
  --start=2018-01-01T00:00:00Z --end=2018-03-01T00:59:59Z  
```

---

# Implementation notes

Times are supplied in UTC as this matches the Dukascopy epoch data.

Dukascopy file format was reverse engineered using internet forums, the wayback machine, and bit twiddling.

## Spring support

Model classes have annotations to support @Value configuration. See DataStreamCLi for an example
of configuring with a SpringBoot application.

## What is StreamData for?

Our models derive from "StreamData" which is an abstraction for having physical streams of these data records
that may exist together on a common transport. You may not need this approach for what you are building.

## Tick to bar aggregation

Price represent Bid tone.

See BarTickStreamAggregator.java for details.

## Cache speed

Run on an M1 Max with 100MB internet retrieving 559 M10 bars. Your performance may vary.

559 bars to CSV shows a 240X improvement on repeated bar aggregations versus 3.6% worst case performance increase.
Worst case is dependent on how many days of H1 tick files are required to answer query.

```shell
java -jar example-cli/target/example-cli-4.0.1.jar --symbol=EURUSD --period=M10 \
--start="2019-05-07T00:00:00Z" --end="2019-05-11T00:00:00Z" --output=./test.csv
```

| Version | 	Empty Cache Query Aggregation Time | Repeat Query Aggregation Time |
|---------|-------------------------------------|-------------------------------|
| 1.x     | 55s                                 | 12s                           |
| 2.0     | 57s                                 | 0.05s                         |

## Dukascopy Tick Data Exploration

Inspiration from C++ library:

https://github.com/ninety47/dukascopy

### Tick Dukascopy File Format

Note that dukascopy is a UTC+0 offset so no time adjustment is necessary.
Dukascopy historical data.

The files I downloaded are named something like '00h_ticks.bi5'. These 'bi5' files are LZMA compressed binary data
files. The binary data file are formatted into 20-byte rows.

32-bit integer: milliseconds since epoch

32-bit float: Ask price

32-bit float: Bid price

32-bit float: Ask volume

32-bit float: Bid volume

The ask and bid prices need to be multiplied by the point value for the symbol/currency pair.
The epoch is extracted from the URL (and the folder structure I've used to store the files on disk). It represents the
point in time that the file starts from e.g. 2013/01/14/00h_ticks.bi5 has the epoch of midnight on 14 January 2013.
Example using C++ to work file format, including format and computation of “epoch time”:

LZ compression/decompression can be done with apache commons compress:

https://commons.apache.org/proper/commons-compress/

This format is “valid” after experimentation.

```
[   TIME  ] [   ASKP  ] [   BIDP  ] [   ASKV  ] [   BIDV  ]
[0000 0800] [0002 2f51] [0002 2f47] [4096 6666] [4013 3333]
```

* TIME is a 32-bit big-endian integer representing the number of milliseconds that have passed since the beginning of
  this hour.
* ASKP is a 32-bit big-endian integer representing the asking price of the pair, multiplied by 100,000.
* BIDP is a 32-bit big-endian integer representing the bidding price of the pair, multiplied by 100,000.
* ASKV is a 32-bit big-endian floating point number representing the asking volume, divided by 1,000,000.
* BIDV is a 32-bit big-endian floating point number representing the bidding volume, divided by 1,000,000.

### Tick Data Format

Note that epoch milliseconds is relative to UTC timezone.
source is live | historical

```json
{
  "epochMilliseconds": 94875945798,
  "symbol": "EURUSD",
  "bid": 134567,
  "ask": 134520,
  "source": "live",
  "streamId": "00000000-0000-0000-0000-000000000000"
}
```

---

# Version Updates

* The plugin update requires manual checks as it is a report.
* Version updates automatic and are configured to skip alpha, beta, rc and old date format versions.
* maven-versions-plugin has backup poms disabled as VCS is here.

## Set a new release version

```shell
mvn versions:set -DprocessAllModules -DgenerateBackupPoms=false -DnewVersion=XX-SNAPSHOT 
```

Do a replacement in this readme file so that examples are updated to the new version.

## Report on what plugin updates are available

```shell
   mvn versions:display-plugin-updates | more

```

## Update all library versions and parent dependencies

```shell
mvn versions:update-parent -U
mvn versions:update-properties -U
mvn versions:use-latest-releases -U
```

---

# Changes
          
## 4.0.1
* OSS deployment fixes.

## 4.0.0

* Java 21 as a minimum requirement. Spring support library upgrades. OSS test library updates and API changes.
* Severely reduced Dukascopy rate limiter to 1ps as 10ps was too aggressive and lead to Dukascopy throttling without 500
  throws.
* Dukascopy rate throttling has changed to be 30 second limiting on "high" request rates without error throws. This has
  been reverse-engineered as there is no documentation. The old three times retry on code 500 behavior has been
  preserved but with a thirty second exponential backoff.
* Extra debugging logs for tracing rate limiting.
* MarketStatus check to not request download of data outside FX global market hours.
* Used configuration to reduce timeout testing to 1s rather than default 30s for production use.

## 3.0.0

* Java 17 as a minimum requirement. Spring support library upgrades.

## 2.1.3

* TradingInputJsonStreams now supports a visitor when loading a stream from json data.

## 2.1.2

* Added an example spring configuration.

## 2.1.1

* Library updates.

## 2.1.0

### Breaking API changes

* Moved generic stream classes into trading.model.streams package.
* There are some minor API changes such as adding IOException to close methods where missed.

### Other Changes

* Reworked stream model and produced a set of generic input stream utilities in trading.model.stream.
* Added a stream Collection method to TradingJsonStreams, prefer the InputStream version for efficiency.
* trading.model.stream.TradingInputStreamMapper produces generic streams and transforms for any MODEL.
* trading.model.stream.TradingInputStreamMapper has overloads for onClose Runnable for cleanup operations.

## 2.0.5

* Spotted fix for hasNext in TradingInputJsonStreams. Fix ported from closed source.

## 2.0.4

* Correct aggregation bug in count before and count after bar searches. A duplicate bar may have been included in
  streams due to
  some end searching being inclusive.

## 2.0.3

* Correct write bug in JSON stream output that fails for large files with early close due to Jackson auto close.

## 2.0.2

* Support for streaming JSON file formats (as arrays) using TradingInputJsonStreams. Relies on Jackson.

## 2.0.1

* Added locking around local and s3 cache usage for multithreaded scenarios.

## 2.0.0

### Breaking API changes

* Added bar caching that alters the cache storage and cache classes. Storage is backwards compatible however
  the construction of the caches has altered. Bar Caching produced repeated aggregations at 240X faster in our example
  in technical notes.
* Due to bar caching tick visitors on aggregation queries are no longer supported.
* Tick search functions **are** still supported for tick visitors.

### Other changes

* Added a read only json property to models with the model version (1.0).
* Split DukascopySearch implementation into Tick and Bar searches (backward compatible).
* Local cache can have the cache directory set via an alternate constructor.
* trading-data-stream development no longer deletes the default cache location.

## 1.2.0

* Improved CSV model by introducing a base class that can be extended for other projects.

## 1.1.0

* Stop searches at the Beginning of Time - arbitrarily defaulted to be 2010-01-01T00:00:00Z.
* Allow configuration of the Beginning of Time.
* Aggregate for a number of bars before a given instant.
* Aggregate for a number of bars after a given instant.
* Added delayed n-retry with exponential backoff on Dukascopy IO failure. Defaults to 3X and 2 second.
* Upped the rate limit after discovering 500s are occasional errors on empty paths.
* Added a cache primer application to test load limits (concurrent requests).

## 1.0.0

* Initial release to open source.
* Search ticks by symbol, time range,
* Aggregate bars by time range.
* Convert models (Tick, Bar) to csv.
* Cache dukascopy files Direct No Cache, Local Cache and S3 Cache.
* Combine caches into cache chains such as Local -> S3 -> Direct.
* Example CLI
