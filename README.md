SQL queries parallel processing using JdbcTemplate
==================================================

This library allows to execute single SQL query in different data sources simultaneously.

It takes SQL query string and list of [parameters mappings](http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/jdbc/core/namedparam/SqlParameterSource.html),
and executes query once for each list element in parallel. Combined queries results are provided to application as `java.util.Iterator`.
Data source choosing for next query may be controlled from application.

In runtime library depends on [spring-jdbc](http://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/jdbc.html)
and [guava](http://code.google.com/p/guava-libraries/).

Library is available in [Maven cental](http://repo1.maven.org/maven2/com/alexkasko/springjdbc/).

Javadocs for the latest release are available [here](http://alexkasko.github.com/parallel-queries/javadocs).

Library usage
-------------

Maven dependency (available in central repository):

    <dependency>
        <groupId>com.alexkasko.springjdbc</groupId>
        <artifactId>parallel-queries</artifactId>
        <version>1.2</version>
    </dependency>

To start parallel query execution you should create instance of `ParallelQueriesIterator` and call `start` method
providing list of parameters mappings:

    List<DataSource> sources = ImmutableList.of(ds1, ds2);
    String sql = "select * from foo where bar > :bar and baz < :baz";
    // prepare parameters, you may use BeanPropertySqlParameterSource or your own implementation instead of maps
    MapSqlParameterSource map1 = new MapSqlParameterSource(ImmutableMap.of("bar", 142, "baz", 143));
    MapSqlParameterSource map2 = new MapSqlParameterSource(ImmutableMap.of("bar", 242, "baz", 243));
    MapSqlParameterSource map3 = new MapSqlParameterSource(ImmutableMap.of("bar", 342, "baz", 343));
    List<MapSqlParameterSource> params = ImmutableList.of(map1, map2, map3);
    // create iterator instance and start it
    Iterator<MyObj> iter = new ParallelQueriesIterator<MyObj>(sources, sql, mapper)
        .addListener(listen1).addListener(listen2)
        .start(params);

After that you'll be able to read from iterator results of 3 queries.

###Instance creation

To create instance of `ParallelQueriesIterator` you should provide:

 * collection of data sources
 * SQL query string
 * [row mapper](http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/jdbc/core/RowMapper.html) for result sets processing

####JdbcTemplate as data source

JDBC data sources are represented by `javax.sql.DataSource` class. But some data sources may require fine tuning of JDBC
resourcese usage (e.g. [set fetch size](http://static.springsource.org/spring/docs/3.0.6.RELEASE/api/org/springframework/jdbc/core/JdbcTemplate.html#setFetchSize(int)).
Iterator uses `JdbcTemplate` to actually execute queries. If application provides collection of data sources, then
`JdbcTemplate` with default settings is created for each data source.

To support JDBC tuning iterator can take collection of preconfigured `JdbcTemplate`'s instead of `DataSource`'s.

####data source accessors

Data sources (wrapped into `NamedParameterJdbcOperations`) may be provided using `DataSourceAccessor` interface. Implementations of
this interface holds the list of actual data sources and decides what source to use for next query based on
query parameters mapping as an argument for `get` method. Application may provide its own `DataSourceAccessor`
implementation (with own `NamedParameterJdbcOperations` and `SqlParameterSource` implementation) with necessary logic.

By default library uses `RoundRobinAccessor` that returns data sources in circular order ignoring actual query parameters.

####named parameters support

Library internally uses `JdbcOperations` instead of `NamedParameterJdbcOperations` to utilize more fine grained access
to query execution (to support query cancelling). Despite it, library supports SQL queries with named parameters
using `:placeholder` syntax using exactly the same implementation of named parameters as `NamedParameterJdbcTemplate`.

####row mapper parameters access

Sometimes for result set mapping logic you need access to actual query parameters. Spring's `RowMapper` doesn't
support this directly (some databases support input parameters transfer with JDBC parameters in `from` clause).

To use query parameters in result set's mapping you may provide `RowMapperFactory` instead of `RowMapper`.

###Starting queries execution

Queries are executed in background threads. After instance creation `ParallelQueriesIterator` is a "passive" object and
will throw `IllegalStateException` on attemt to iterate over it.

To start actual query execution you must call `start` method providing collection of parameters mappings. It fires
background workers, that will be active untill all results are read from iterator or execution cancelled.

Finished (exhausted) iterator may be restarted calling `start` another time.

###Cancelling queries execution

Queries execution may be cancelled calling `cancel` method:

 * `cancelled` flag will be set
 * all active JDBC queries will be cancelled using [cancel](http://docs.oracle.com/javase/6/docs/api/java/sql/Statement.html#cancel())
 method (errors from unsuppoting drivers will be ignored)
 * all active background threads will be interrupted
 * subsequent iterator accesse will result in `ParallelQueriesException`
 * cancelled iterator cannot be restarted

###Queries execution listeners

`ParallelQueriesListener` implementations may be attached to `ParallelQueriesIterator`.
Listeners will be notified about all successfull and errored queries immediately
after query execution. Listeners are called from worker threads, so their implementation must be thread-safe.

How does it work
----------------

Under the hood `ParallelQueriesIterator` spawns an worker for each parameters mapping using provided
`ExecutorService` (by default `Executors.newCachedThreadPool()`). Workers execute queries, and write
mapped rows into `ArrayBlockingQueue` until all rows are read. On iteration calls iterator reads mapped
rows from the queue one by one and return them to the caller.

To limit max parallel queries (to value less then parameters list size) you should add such logic into `ExecutorService`
([example](https://gist.github.com/4045853)).

License information
-------------------

This project is released under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Changelog
---------

**1.2** (2013-01-22)

 * errors reporting reworked

**1.1** (2013-01-21)

 * accessors use `NamedParameterJdbcTemplate` instead of `JdbcTemplates`

**1.0** (2012-11-09)

 * initial version