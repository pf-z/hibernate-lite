# hibernate-lite examples

Runnable examples for **every public API** of hibernate-lite, grouped by
feature instead of one file per method.

Each class is a standalone program with its own `main`, and the whole set can
also be run in one go.

## Requirements

- JDK 17+
- Maven 3.8+

The examples are part of the main Maven build. `examples/src/main/java` is
added as an extra **test** source root, so they:

- compile with the same test-scoped dependencies as the test suite
  (H2, HikariCP, Logback) - no extra dependency is needed;
- are compiled by every `mvn test`, which keeps them from rotting;
- land in `target/test-classes`, so they never ship inside the library jar.

```bash
# from the repository root
mvn test-compile

# run everything
mvn exec:java -Dexec.mainClass=me.pfzh.hibernatelite.examples.RunAll -Dexec.classpathScope=test

# or run a single category
mvn exec:java -Dexec.mainClass=me.pfzh.hibernatelite.examples.PaginationExample -Dexec.classpathScope=test
```

In an IDE, mark `examples/src/main/java` as a test source root.

Every example opens its own in-memory H2 database (`ddlAuto("create-drop")`),
so the order of execution does not matter and nothing is left behind.

## Layout

```
examples/
├── README.md                     this file
└── src/main/java/me/pfzh/hibernatelite/examples/
    ├── ExampleSupport.java       DataStore/H2 bootstrap + console helpers
    ├── RunAll.java               runs every example in order
    ├── model/
    │   ├── User.java             generated Long id, name, age, status
    │   ├── Account.java          business key (@Id String), no @GeneratedValue
    │   └── Purchase.java         lazy @ManyToOne to User
    ├── CrudExample.java          find / save / saveAll / delete
    ├── QueryOperatorExample.java all 12 operators, ordering, terminal operations
    ├── PaginationExample.java    PageRequest + Page, both modes, DTO mapping
    ├── BulkOperationExample.java conditional update/delete + safety guards
    ├── JpqlExample.java          parameters, projections, joins, aggregation, paging
    ├── TransactionExample.java   commit, rollback, nesting, rollback-only, Error
    ├── EscapeHatchExample.java   where(QuerySpec), unwrap(SessionFactory)
    ├── LifecycleExample.java     Builder, close(), batching, detached state
    └── ErrorHandlingExample.java one call per exception type
```

## What each example shows

| Class | Topics |
|---|---|
| `CrudExample` | insert, update, merge return value, batch save, delete in three shapes, business-key restriction |
| `QueryOperatorExample` | `eq` `ne` `gt` `ge` `lt` `le` `like` `in` `notIn` `isNull` `isNotNull` `between`, AND composition, `orderByAsc/Desc`, `list` `list(n)` `one` `count` `exists` |
| `PaginationExample` | `PageRequest.of/firstPage/withoutCount/offset/page/size/countTotal`, total-count vs slice mode, every `Page` accessor, `map`, out-of-range page, invalid requests |
| `BulkOperationExample` | `update(field,value)`, `update(Map)`, `delete()`, both safety guards, persistence-context clearing |
| `JpqlExample` | `param`, `list`, `list(n)`, `one`, `count`, `exists`, `Object[]` projection, `GROUP BY`, `JOIN`, `page(countJpql, request)`, `page(null, request.withoutCount())`, `page(request)` throwing |
| `TransactionExample` | callback and Runnable forms, rollback, nested REQUIRED, rollback-only propagation, `Error` cleanup |
| `EscapeHatchExample` | `where(spec)` alone / with DSL / repeated / returning null, `unwrap(SessionFactory)` + native SQL, `SessionContext.current` |
| `LifecycleExample` | all `Builder` options, validation, try-with-resources, idempotent `close()`, use-after-close, 50-row batch boundary, detached entities and lazy loading |
| `ErrorHandlingExample` | `IllegalArgumentException`, `NullPointerException`, `HibernateLiteException`, `IllegalStateException`, `UnsupportedOperationException` |

## API coverage

Every public method of the facade is exercised below.

### Bootstrap

| API | Where |
|---|---|
| `HibernateLite.builder()` | `ExampleSupport`, `LifecycleExample` |
| `Builder.dataSource` | `ExampleSupport`, `LifecycleExample` |
| `Builder.entities` | `ExampleSupport`, `LifecycleExample` |
| `Builder.showSql` | `ExampleSupport`, `LifecycleExample` |
| `Builder.ddlAuto` | `ExampleSupport`, `LifecycleExample` |
| `Builder.build` | `ExampleSupport`, `LifecycleExample` |

### `DataStore`

| API | Where |
|---|---|
| `find(Class, Object)` | `CrudExample` |
| `save(T)` | `CrudExample`, `TransactionExample` |
| `saveAll(List)` | `CrudExample`, `LifecycleExample` |
| `delete(Object)` | `CrudExample` |
| `transaction(TransactionCallback)` | `TransactionExample` |
| `transaction(Runnable)` | `TransactionExample` |
| `query(Class)` | every query example |
| `query(String, Class)` | `JpqlExample` |
| `unwrap(Class)` | `EscapeHatchExample`, `LifecycleExample` |
| `close()` | `LifecycleExample`, `ExampleSupport` |

### `LambdaQuery`

| API | Where |
|---|---|
| `eq` `ne` `gt` `ge` `lt` `le` | `QueryOperatorExample` |
| `like` `in` `notIn` | `QueryOperatorExample` |
| `isNull` `isNotNull` `between` | `QueryOperatorExample` |
| `orderByAsc` `orderByDesc` | `QueryOperatorExample` |
| `where(QuerySpec)` | `EscapeHatchExample`, `BulkOperationExample` |
| `list()` `list(int)` | `QueryOperatorExample` |
| `one()` `count()` `exists()` | `QueryOperatorExample`, `JpqlExample` |
| `delete()` | `BulkOperationExample` |
| `update(SFunction, Object)` | `BulkOperationExample` |
| `update(Map)` | `BulkOperationExample` |
| `page(PageRequest)` | `PaginationExample`, `BulkOperationExample` |

### `JpqlQuery`

| API | Where |
|---|---|
| `param(String, Object)` | `JpqlExample` |
| `list()` `list(int)` | `JpqlExample` |
| `one()` `count()` `exists()` | `JpqlExample` |
| `page(PageRequest)` | `JpqlExample` (throws) |
| `page(String, PageRequest)` | `JpqlExample` |

### `PageRequest`

| API | Where |
|---|---|
| `of(int, int)` | `PaginationExample` |
| `firstPage(int)` | `PaginationExample` |
| `withoutCount()` | `PaginationExample` |
| `offset()` `page()` `size()` `countTotal()` | `PaginationExample` |

### `Page`

| API | Where |
|---|---|
| `content()` | `PaginationExample` |
| `numberOfElements()` `isEmpty()` | `PaginationExample` |
| `page()` `size()` | `PaginationExample` |
| `totalElements()` `totalPages()` | `PaginationExample` |
| `hasTotal()` `hasNext()` `hasPrevious()` | `PaginationExample` |
| `isFirst()` `isLast()` | `PaginationExample` |
| `map(Function)` | `PaginationExample` |

### Support types

| API | Where |
|---|---|
| `QuerySpec.toPredicate` | `EscapeHatchExample`, `BulkOperationExample` |
| `TransactionCallback.execute` | `TransactionExample` |
| `SFunction` (getter references) | every query example |
| `HibernateLiteException` | `ErrorHandlingExample`, `CrudExample` |
| `SessionContext.current` (internal, escape hatch) | `EscapeHatchExample` |

## Notes

- `examples` is compiled as part of `mvn test` (extra test source root declared
  in the root `pom.xml`), but the classes go to `target/test-classes` and are
  not part of the published jar.
- The examples target the fixed semantics documented in the root `README.md`.
  In particular `where(spec -> null)` is rejected for `delete()`/`update()`,
  and detached lazy associations throw `LazyInitializationException`.
- `Account` is a business-key entity on purpose: `save()` rejects it, so
  `CrudExample` shows the `unwrap(SessionFactory.class)` workaround.
