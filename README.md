# hibernate-lite

[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk)](https://www.oracle.com/java/)
[![Hibernate ORM](https://img.shields.io/badge/Hibernate%20ORM-6.6-blue?logo=hibernate)](https://hibernate.org/orm/)
[![Jakarta Persistence](https://img.shields.io/badge/Jakarta%20Persistence-3.1-blue?logo=eclipse)](https://jakarta.ee/specifications/persistence/)
[![License](https://img.shields.io/github/license/pf-z/hibernate-lite)](LICENSE)
[![Javadoc Deploy](https://github.com/pf-z/hibernate-lite/actions/workflows/javadoc.yml/badge.svg)](https://github.com/pf-z/hibernate-lite/actions/workflows/javadoc.yml)
[![Last Commit](https://img.shields.io/github/last-commit/pf-z/hibernate-lite)](https://github.com/pf-z/hibernate-lite/commits/main)
[![Javadoc](https://img.shields.io/badge/Javadoc-latest-blue)](https://pf-z.github.io/hibernate-lite/)
[![Status](https://img.shields.io/badge/status-beta-orange)](https://github.com/pf-z/hibernate-lite)

**A lightweight Hibernate-based ORM framework with a clean and extensible API.**

Hibernate-Lite is a thin ORM layer built on top of Hibernate ORM. It removes DAO /
Repository boilerplate and makes transactions implicit, while keeping Hibernate's
native API reachable through explicit escape hatches.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
  - [1. Bootstrap](#1-bootstrap)
  - [2. CRUD](#2-crud)
  - [3. Query DSL](#3-query-dsl)
  - [4. JPQL](#4-jpql)
  - [5. Pagination](#5-pagination)
  - [6. Transactions](#6-transactions)
  - [7. Escape Hatches](#7-escape-hatches)
- [Semantics Reference](#semantics-reference)
  - [Session lifecycle and detachment](#session-lifecycle-and-detachment)
  - [Transaction semantics](#transaction-semantics)
  - [Bulk DML and the persistence context](#bulk-dml-and-the-persistence-context)
  - [Concurrency and multiple DataStores](#concurrency-and-multiple-datastores)
  - [Safety guards](#safety-guards)
- [Limitations](#limitations)
- [Error Handling](#error-handling)
- [Design Principles](#design-principles)
- [Building and Testing](#building-and-testing)
- [License](#license)

## Overview

Hibernate-Lite gives you:

- **Zero Repository interfaces** — one `DataStore` for every entity
- **Automatic transactions** — `db.save(user)` wraps itself in a transaction
- **Transparent session management** — no `Session`, no `EntityManager` in business code
- **Type-safe queries** — `User::getName` instead of `"name"`
- **Escape hatches** — raw JPA Criteria, JPQL, and the native `SessionFactory`

### Why Hibernate-Lite

Traditional Spring Data JPA forces you to write one repository interface per entity:

```java
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByStatusAndAgeGreaterThan(String status, int age);
    User findByEmail(String email);
    long countByStatus(String status);
}
```

This scales badly:

- 100 entities → 100 interfaces
- Method names are parsed at runtime (`findByStatusAndAgeGreaterThan` is magic)
- Dynamic queries require `Specification` or QueryDSL
- Transactions leak into the service layer via `@Transactional`

Hibernate-Lite replaces all of that with **one `DataStore`**:

```java
User user = db.find(User.class, 1L);
db.save(user);
db.saveAll(users);
db.delete(user);

db.transaction(() -> {
    db.save(order);
    db.save(payment);
});
```

## Features

- **Zero Repository** — no per-entity DAO boilerplate
- **Automatic transactions** — a single `save` wraps itself; batch operations share one transaction
- **Transparent session management** — no `Session` or `EntityManager` in business code
- **Type-safe query DSL** — `User::getName` checked at compile time
- **JPQL support** — for multi-table joins, subqueries, and complex queries
- **Pagination** — with or without total count, with DTO mapping
- **Conditional update and delete** — one SQL statement, with safety guards
- **Optimistic locking** — `@Version` is enforced by `save()` and `delete()`
- **Nested transaction support** — REQUIRED semantics with rollback-only propagation
- **Thread-safe per `DataStore`** — session state is bound to *(thread, SessionFactory)*
- **Native Hibernate/JPA exceptions wrapped** into `HibernateLiteException`
- **Escape hatches** — `where(QuerySpec)`, JPQL, `unwrap(SessionFactory.class)`

## Requirements

| Component  | Version           |
| ---------- | ----------------- |
| Java       | 17+               |
| Hibernate  | 6.6+              |
| Jakarta EE | 3.1 (Persistence) |

Supported databases (via Hibernate 6.6 dialects):

- PostgreSQL 12+
- MySQL 8.0+
- MariaDB 10.4+
- Oracle 19+
- SQL Server 2012+
- H2 2.1+

Hibernate-Lite does **not** manage connections. Supply a `javax.sql.DataSource`
(HikariCP, DBCP, a container pool, ...). It also does not configure a
second-level cache, a dialect, or JDBC batching — see [Limitations](#limitations).

## Installation

Hibernate-Lite is **not yet published to Maven Central**.

### Build from source

```bash
git clone https://github.com/pf-z/hibernate-lite.git
cd hibernate-lite
mvn clean install
```

```xml
<dependency>
    <groupId>me.pfzh</groupId>
    <artifactId>hibernate-lite</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### 1. Bootstrap

```java
HikariDataSource ds = new HikariDataSource();
ds.setJdbcUrl("jdbc:postgresql://localhost/mydb");
ds.setUsername("user");
ds.setPassword("pass");

DataStore db = HibernateLite.builder()
        .dataSource(ds)
        .entities(User.class, Order.class)
        .showSql(false)
        .ddlAuto("validate")
        .build();
```

### 2. Use

```java
// Create
User user = db.save(new User("Alice"));

// Read
User found = db.find(User.class, user.getId());

// Update
found.setName("Alice Smith");
db.save(found);

// Delete
db.delete(found);

// Query
List<User> active = db.query(User.class)
        .eq(User::getStatus, "ACTIVE")
        .gt(User::getAge, 18)
        .orderByDesc(User::getCreatedAt)
        .list();

// Transaction
Long id = db.transaction(() -> {
    User u = db.save(new User("Bob"));
    db.save(new Order(u));
    return u.getId();
});
```

## API Reference

### 1. Bootstrap

#### `HibernateLite.builder()`

| Method | Required | Default | Description |
|--------|----------|---------|-------------|
| `dataSource(DataSource)` | ✅ | — | Connection pool (Hikari, DBCP, ...) |
| `entities(Class<?>...)` | ✅ | — | At least one entity class |
| `showSql(boolean)` | ❌ | `false` | Print generated SQL |
| `ddlAuto(String)` | ❌ | `"validate"` | Schema strategy |

**`ddlAuto` values**: `"none"`, `"validate"` (default), `"update"`, `"create"`,
`"create-drop"`.

`build()` throws `IllegalStateException` when the data source or the entity list is
missing, and wraps any Hibernate bootstrap failure after destroying the service
registry.

#### `DataStore.close()`

Releases the `SessionFactory`. `DataStore` implements `AutoCloseable`:

```java
try (DataStore db = HibernateLite.builder()...build()) {
    // ...
}
```

**Notes**

- Idempotent — safe to call multiple times.
- After `close()`, the `DataStore` must not be used again; `unwrap(...)` still
  returns the (now closed) `SessionFactory`.
- `close()` does **not** roll back a transaction that is currently open on some
  thread. Commit or roll back first.
- The `DataSource` is **not** closed by `close()`; it belongs to the caller.

### 2. CRUD

#### `find(Class<T> type, Object id)`

```java
User u = db.find(User.class, 1L);
```

- Returns `null` if no entity exists.
- Reads from the **first-level cache of the transaction-scoped Session only**. No
  second-level cache is configured.
- The returned entity is **detached** as soon as the call returns — see
  [Session lifecycle and detachment](#session-lifecycle-and-detachment).
- Throws `IllegalArgumentException` if `type` or `id` is `null`.

#### `save(T entity)`

| ID state | Entity type | Behaviour |
|----------|-------------|-----------|
| `null` | Any | **INSERT** (`persist`) |
| non-null | Has `@GeneratedValue` | **MERGE** (`merge`) — may UPDATE an existing row |
| non-null | Business key (no `@GeneratedValue`) | ❌ throws `HibernateLiteException` |

```java
User u = new User("Alice");
u = db.save(u);          // INSERT

u.setName("Alice Smith");
u = db.save(u);          // UPDATE (merge)
```

**Notes**

1. **Always use the return value** — `merge` returns a different, managed instance.
2. **Do not assign an id yourself** on a `@GeneratedValue` entity unless you really
   mean "update the row with that id".
3. **Business-key entities cannot be created with `save()`.** The key *is* the
   identifier, so it is non-null on any valid instance and the call always throws.
   Insert them with `unwrap(SessionFactory.class)` (see [Limitations](#limitations)).
4. **Primitive identifiers** (`long id`) are never `null` (`0`), so they always take
   the `merge` path. This works, but costs an extra `SELECT`; prefer boxed types.
5. Hibernate proxies are accepted: the identifier is read from the proxy without
   initialising it.

#### `saveAll(List<T> entities)`

```java
List<User> saved = db.saveAll(users);
```

- All entities are written in **one transaction**.
- Every **50** rows the batch is flushed. When `saveAll` runs inside a caller
  transaction, only the entities written in that batch are detached; unrelated
  managed entities stay attached and their later changes are still flushed at
  commit. When `saveAll` owns the transaction, the temporary persistence context
  is cleared, so memory stays bounded for large batches.
- This is **not** JDBC batching: `hibernate.jdbc.batch_size` is never set, and
  `IDENTITY` generators cannot be batched at all.
- **Any failure rolls back the whole batch.**
- Entities of a flushed batch are **detached**.
- An empty input list is returned as-is (no transaction is opened).
- A `null` element throws `HibernateLiteException` naming the offending index.

#### `delete(Object entity)`

```java
db.delete(db.find(User.class, 1L));

User detached = new User();
detached.setId(1L);
db.delete(detached);
```

- Idempotent — silently returns when no matching row exists.
- Works with managed entities, detached entities, and lazy proxies.
- For an entity with a `@Version` field, a detached snapshot whose version differs
  from the current database row is rejected with `HibernateLiteException`; reload
  the entity before deleting it.
- Throws `HibernateLiteException` if the entity has no identifier.

### 3. Query DSL

```java
<T> LambdaQuery<T> query(Class<T> type)
```

`LambdaQuery` is **mutable and not thread-safe**: create one per query.

#### Operators

All conditions are **AND**-combined.

| Method | SQL | Example |
|--------|-----|---------|
| `eq(field, value)` | `=` | `.eq(User::getStatus, "ACTIVE")` |
| `ne(field, value)` | `<>` | `.ne(User::getName, "Admin")` |
| `gt(field, value)` | `>` | `.gt(User::getAge, 18)` |
| `ge(field, value)` | `>=` | `.ge(User::getAge, 18)` |
| `lt(field, value)` | `<` | `.lt(User::getAge, 65)` |
| `le(field, value)` | `<=` | `.le(User::getAge, 65)` |
| `like(field, pattern)` | `LIKE` | `.like(User::getName, "A%")` |
| `in(field, collection)` | `IN` | `.in(User::getRole, List.of("ADMIN","USER"))` |
| `notIn(field, collection)` | `NOT IN` | `.notIn(User::getRole, List.of("BANNED"))` |
| `isNull(field)` | `IS NULL` | `.isNull(User::getDeletedAt)` |
| `isNotNull(field)` | `IS NOT NULL` | `.isNotNull(User::getEmail)` |
| `between(field, low, high)` | `BETWEEN` | `.between(User::getAge, 18, 65)` |

Field references are **getter method references** (`User::getName`,
`User::isActive`). The property name is derived with `Introspector.decapitalize`,
so `getURL` maps to `URL`.

**Validation performed eagerly, when the condition is built:**

- `eq`/`ne`/`gt`/`ge`/`lt`/`le` reject a `null` value (use `isNull`/`isNotNull`).
- `like` requires a `String`.
- `in`/`notIn` require an `Iterable`; an **empty** collection is rejected when the
  query executes.
- `between` requires two bounds; an inverted range simply returns no rows.
- A non-getter method reference throws `HibernateLiteException`.

#### Ordering

```java
db.query(User.class)
        .orderByAsc(User::getStatus)
        .orderByDesc(User::getAge)
        .list();
```

Ordering rules are applied in declaration order. **If you do not order at all, no
`ORDER BY` is emitted** and page boundaries are not deterministic on a real
database.

#### Terminal operations

| Method | Description |
|--------|-------------|
| `list()` | All matching entities, **silently capped at 1000** |
| `list(int limit)` | Explicitly limited |
| `one()` | Exactly one (0 → `null`; >1 → `HibernateLiteException`) |
| `count()` | `SELECT COUNT(...)` |
| `exists()` | Limit-1 query (`list(1)`); no full `COUNT` |
| `delete()` | Bulk delete, requires a restriction |
| `update(field, value)` / `update(Map)` | Bulk update, requires a restriction |
| `page(PageRequest)` | Paginated result |

> **Sharp edge.** `list()` returns at most 1000 rows **without any warning**, while
> `count()` reports the real total. Use `list(n)` or `page()` when the data set may
> be larger.

#### Conditional delete / update

```java
int deleted = db.query(User.class).lt(User::getAge, 18).delete();
int updated = db.query(User.class).eq(User::getName, "Alice").update(User::getAge, 26);
```

Both are single bulk SQL statements and both require a restriction — see
[Safety guards](#safety-guards).

### 4. JPQL

```java
<T> JpqlQuery<T> query(String jpql, Class<T> resultType)
```

- `jpql` — a JPQL string (not SQL)
- `resultType` — entity class, or a scalar type such as `Long.class`

#### Parameters

```java
JpqlQuery<T> param(String name, Object value)
```

The name has **no leading `:`**. Chainable; a repeated name overwrites the previous
value; `null` values are allowed. **Every parameter you bind must be declared by the
statement** — a stray name is rejected by Hibernate.

```java
List<User> users = db.query(
        "SELECT u FROM User u WHERE u.status = :status AND u.age > :minAge",
        User.class)
        .param("status", "ACTIVE")
        .param("minAge", 18)
        .list();
```

#### Terminal operations

| Method | Description |
|--------|-------------|
| `list()` | Default limit 1000 |
| `list(int limit)` | Limited list |
| `one()` | Single result (0 → `null`; >1 → throws) |
| `count()` | Scalar count; the JPQL **must** be `SELECT COUNT(...)` |
| `exists()` | `list(1)` is non-empty |
| `page(PageRequest)` | **Always throws `UnsupportedOperationException`** |
| `page(String countJpql, PageRequest)` | Pagination with an explicit count statement |

#### Pagination

```java
Page<Order> page = db.query(
        "SELECT o FROM Order o WHERE o.status = :status ORDER BY o.createdAt DESC",
        Order.class)
        .param("status", "PENDING")
        .page(
                "SELECT COUNT(o) FROM Order o WHERE o.status = :status",
                PageRequest.of(0, 20));
```

**Slice mode** does not need a count statement — pass `null`:

```java
Page<Order> page = db.query("SELECT o FROM Order o", Order.class)
        .page(null, PageRequest.of(0, 20).withoutCount());
```

The count statement may declare a **different subset** of the parameters than the
page statement; only the names it declares are bound. Parameters that the count
statement declares but you did not supply remain unbound and fail loudly.

#### Capabilities

**Supported**: multi-table `JOIN`, subqueries, `GROUP BY` / `HAVING`, `ORDER BY`,
aggregates, DTO projections (`Object[].class`).

**Not supported**: native SQL, stored procedures, database-specific syntax — use
`unwrap(SessionFactory.class)`.

### 5. Pagination

#### `PageRequest`

```java
PageRequest.of(page, size)        // total count enabled
PageRequest.firstPage(size)       // same as of(0, size)
PageRequest.of(page, size).withoutCount()
```

- `page` is **0-based**
- `size` must be in **[1, 1000]**
- `page * size` must fit in an `int`, otherwise construction throws
  `IllegalArgumentException`
- Instances are immutable

#### `Page<T>`

| Method | Description |
|--------|-------------|
| `content()` | Unmodifiable, defensively copied list |
| `numberOfElements()` | Elements on this page |
| `isEmpty()` | Whether the page is empty |
| `page()` / `size()` | Requested page index and size |
| `totalElements()` | Total matches (**throws `IllegalStateException` in slice mode**) |
| `totalPages()` | Total pages (**throws in slice mode**) |
| `hasTotal()` | `true` for total-count mode |
| `hasNext()` | Accurate in both modes |
| `hasPrevious()` / `isFirst()` / `isLast()` | Page position helpers |
| `map(Function)` | DTO mapping that preserves all pagination metadata |

`Page` has no `equals`/`hashCode`, and `map` cannot produce `null` elements
(`List.copyOf` semantics).

#### Total-count mode vs slice mode

| Aspect | `PageRequest.of(...)` | `.withoutCount()` |
|--------|----------------------|-------------------|
| `totalElements()` | ✅ | ❌ throws |
| `totalPages()` | ✅ | ❌ throws |
| `hasTotal()` | `true` | `false` |
| `hasNext()` | accurate | accurate (fetches `size + 1` rows) |
| SQL statements | 2 | 1 |
| Use case | "page 2 of 7" | infinite scroll |

> **Consistency note.** In total-count mode the page query and the count query run
> in **two separate transactions**, so they can disagree under concurrent writes.

#### DTO mapping

```java
Page<UserDto> dtos = page.map(UserDto::from);
```

Preserves `page`, `size`, `totalElements`, `hasNext`, `hasTotal`.

### 6. Transactions

#### Programmatic

```java
<T> T transaction(TransactionCallback<T> callback)
void transaction(Runnable work)
```

- Normal return → commit
- **Any `Throwable`** (`RuntimeException` *and* `Error`) → rollback, then rethrown
  unchanged
- `TransactionCallback` cannot declare checked exceptions; wrap them if needed

#### Nested transactions (REQUIRED)

An inner `transaction(...)` joins the outer transaction. Only the outermost scope
commits.

```java
db.transaction(() -> {          // outer
    db.save(a);
    db.transaction(() -> {      // joins
        db.save(b);
    });
    db.save(c);
});                             // commits a, b, c
```

If an inner scope fails and the caller swallows the exception, the transaction is
marked **rollback-only**. The outermost commit rolls back and throws
`HibernateLiteException("Transaction marked rollback-only")`:

```java
db.transaction(() -> {
    db.save(a);
    try {
        db.transaction(() -> { throw new RuntimeException("inner"); });
    } catch (RuntimeException ignored) { }
    db.save(b);                 // still rolled back
});
```

#### Automatic transactions

These wrap themselves in a transaction when no outer one exists:

- `save` / `saveAll` / `delete`
- `find`
- every terminal operation on `LambdaQuery` and `JpqlQuery`

Reads also start a **read-write** transaction (no read-only optimisation is
applied).

#### Commit is verified

If the underlying transaction is no longer active at commit time — it was rolled
back by the database (timeout, deadlock), or by the caller through the escape
hatch — `commit()` throws instead of silently reporting success. `markRollbackOnly`
and double-commit are reported the same way.

### 7. Escape Hatches

#### `where(QuerySpec<T>)` — raw Criteria

```java
List<User> users = db.query(User.class)
        .where((cb, root) -> cb.or(
                cb.like(root.get("name"), "A%"),
                cb.like(root.get("name"), "B%")))
        .list();
```

- AND-combined with the DSL conditions; may be called multiple times
- Returning `null` contributes no restriction
- `root.get("field")` takes a plain string and is **not** compile-time checked
- Exceptions thrown inside a spec propagate to the caller

#### `unwrap(Class<T> type)`

Currently supports **`SessionFactory.class` only**.

```java
SessionFactory sf = db.unwrap(SessionFactory.class);
try (Session session = sf.openSession()) {
    session.beginTransaction();
    session.createNativeQuery("SELECT * FROM users", Object[].class).list();
    session.getTransaction().commit();
}
```

- `null` → `IllegalArgumentException`
- Unsupported type → `HibernateLiteException`
- Bypasses the library's session and transaction management
- After `close()` it returns the closed factory

## Semantics Reference

### Session lifecycle and detachment

Every `DataStore` operation runs in a `Session` that is opened lazily and **closed
when the operation (or the enclosing `db.transaction`) finishes**. Consequences:

```java
Book book = db.find(Book.class, id);
book.getAuthor().getName();     // LazyInitializationException
```

Lazy associations are only usable **inside** `db.transaction(...)`:

```java
String author = db.transaction(() -> {
    Book b = db.find(Book.class, id);
    return b.getAuthor().getName();
});
```

Entities returned by `find`, `list`, `one`, `page`, `save`, `saveAll` are
**detached** (or become detached at the next batch boundary). If you need to keep
working with an entity graph, do the work inside a transaction, or reload it.

### Transaction semantics

- **Scope**: transaction state is kept per *(thread, `SessionFactory`)*. Two
  `DataStore` instances never share a `Session`.
- **Two `DataStore`s in one call chain**: a call to `db2` inside
  `db1.transaction(...)` runs in **its own transaction** and is **not** atomic with
  `db1`. There is no distributed transaction support.
- **Thread isolation**: a transaction is never visible to another thread.
- **Cleanup**: after commit or rollback the `Session` is closed and the thread-local
  state is removed, including when the callback throws `Error`.
- **Reads** participate in the current transaction, if any.

### Bulk DML and the persistence context

`LambdaQuery.delete()` and `LambdaQuery.update(...)` execute one bulk statement.
They `flush()` pending changes first and `clear()` the persistence context
afterwards, so later reads inside the same transaction observe the new state.

The trade-off: **every entity becomes detached** at that point. Re-`find`/`save`
them afterwards if you need to keep mutating them.

> **Optimistic locking and bulk DML.** Bulk `update()` / `delete()` do **not**
> perform per-row `@Version` checks and do not increment the version attribute.
> This matches JPA bulk-update semantics, but it means a bulk statement can
> overwrite a row changed concurrently. Use per-entity `save()` / `delete()` when
> optimistic locking matters, or include the version in the `WHERE` clause through
> `where(QuerySpec)` and bump it explicitly.

### Optimistic locking

A single field annotated with `@Version` is supported (field access, like `@Id`).

- `save()` of a detached entity with a stale version fails with
  `HibernateLiteException` (cause `OptimisticLockException`) instead of
  overwriting the newer row.
- `delete()` of a detached entity whose version differs from the current row is
  rejected with `HibernateLiteException`; reload it first.
- Uninitialised Hibernate proxies do not expose their version, so the delete
  check is skipped for them.
- Bulk `update()` / `delete()` are exempt (see the note above).

### Concurrency and multiple DataStores

| Component | Thread-safe? |
|-----------|--------------|
| `DataStore` | ✅ |
| `SessionFactoryHolder`, `CrudExecutor`, `QueryExecutor`, `JpqlExecutor`, `MetadataRegistry` | ✅ |
| `LambdaQuery` | ❌ create one per query |
| `JpqlQuery` | ❌ create one per query |
| `Page`, `PageRequest` | ✅ immutable |

Multiple threads may share one `DataStore`; each gets its own `Session` and its own
transaction.

### Safety guards

`delete()` and `update()` refuse to run when the query would have **no effective
restriction**. This covers the obvious case *and* the subtle one where every
`QuerySpec` returns `null`:

```java
db.query(User.class).delete();                       // ❌ throws
db.query(User.class).where((cb, root) -> null).delete();   // ❌ throws
db.query(User.class).where((cb, root) -> cb.conjunction()).delete();  // ✅ explicit "all"
```

The check is per statement — it does not protect `save`, JPQL, or the escape
hatches.

## Limitations

| Area | Limitation |
|------|------------|
| Identifier mapping | Only a **single `@Id` field**. No `@EmbeddedId`, no `@IdClass`, no `@Id` on a getter (property access) |
| Business keys | `save()` cannot insert them; use `unwrap(SessionFactory.class)` |
| Primitive ids | A primitive `long id` is never `null`, so it always takes the `merge` path |
| Lazy loading | Associations are only readable inside `db.transaction(...)` |
| `list()` | Silently capped at 1000 rows |
| Pagination | No default `ORDER BY`; unstable pages without explicit ordering |
| Total count | Page and count run in two transactions |
| JPQL | `page(PageRequest)` always throws; no native SQL, no DML |
| Bulk DML | Detaches everything in the persistence context; does not perform per-row `@Version` checks or bump the version |
| Optimistic locking | `@Version` is checked by `save()` and `delete()`; bulk `update()`/`delete()` bypass it (JPA semantics) |
| Batching | No JDBC batching (`hibernate.jdbc.batch_size` is not set); `IDENTITY` cannot batch |
| Configuration | Only `dataSource`, `entities`, `showSql`, `ddlAuto` on the builder; extra Hibernate settings need a global classpath `hibernate.properties` and cannot differ per `DataStore` |
| Caching | No second-level cache |
| Transactions | No `REQUIRES_NEW`, no savepoints, no read-only transactions, no isolation-level control |
| `@Transient` | `@Transient`, Java `transient` and non-mapped-superclass fields are excluded from metadata; referencing one in a query/update fails with `Entity ... has no field` rather than "not a persistent attribute" |
| `Page` | No `equals`/`hashCode`; `map` cannot return `null` |
| Callbacks | `TransactionCallback` cannot throw checked exceptions |

## Error Handling

### Exception types

| Exception | Trigger |
|-----------|---------|
| `HibernateLiteException` | Runtime failures; wraps Hibernate and JPA `PersistenceException`s (including `OptimisticLockException`) |
| `IllegalArgumentException` | Parameter validation in `CrudExecutor`, `QueryExecutor`, `PageRequest` |
| `NullPointerException` | `Objects.requireNonNull` in `DataStoreImpl` / `TransactionManager` |
| `UnsupportedOperationException` | `JpqlQuery.page(PageRequest)` |
| `IllegalStateException` | `totalElements()` / `totalPages()` in slice mode |

> **Inconsistency.** Null-argument validation is not uniform: `save(null)` throws
> `IllegalArgumentException`, `db.query((Class<?>) null)` and `transaction(null)`
> throw `NullPointerException`.

### Common errors

| Scenario | Exception | Message |
|----------|-----------|---------|
| Unconditional `delete()` | `HibernateLiteException` | `refusing to delete all rows of ...` |
| Unconditional `update()` | `HibernateLiteException` | `refusing to update all rows of ...` |
| `save` with business key | `HibernateLiteException` | `Cannot determine save semantics ...` |
| Stale `delete` of a versioned entity | `HibernateLiteException` | `Stale entity: ... has version ...` |
| Stale `save` of a versioned entity | `HibernateLiteException` | `save failed` (cause: `OptimisticLockException`) |
| Unknown field | `HibernateLiteException` | `Entity ... has no field: nmae` |
| Empty `IN` list | `HibernateLiteException` | `IN / NOT IN requires a non-empty collection` |
| JPQL syntax error | `IllegalArgumentException` | Hibernate parser message |
| Commit on a dead transaction | `HibernateLiteException` | `Transaction is no longer active ...` |
| Swallowed inner failure | `HibernateLiteException` | `Transaction marked rollback-only` |
| `page * size` overflow | `IllegalArgumentException` | `page * size exceeds Integer.MAX_VALUE` |
| Negative page / bad size | `IllegalArgumentException` | `page must be >= 0` / `size must be in [1, 1000]` |
| `unwrap(String.class)` | `HibernateLiteException` | `Unsupported unwrap type` |

### Wrapping rules

- Hibernate and JPA persistence exceptions → `HibernateLiteException` (with `cause`)
- Library exceptions → rethrown unchanged (never double-wrapped)
- Parameter validation → thrown directly

## Design Principles

1. **Zero boilerplate** — no repository per entity
2. **Automatic transactions** — a single `save` wraps itself
3. **Per-`DataStore` isolation** — session state is keyed by *(thread, SessionFactory)*
4. **Nested transactions** — depth counting implements REQUIRED propagation
5. **Rollback-only propagation** — inner failures poison the whole transaction even
   if the exception is swallowed
6. **Type-safe query DSL** — `User::getName` checked at compile time
7. **Escape hatches** — `where(QuerySpec)`, JPQL, `unwrap(SessionFactory.class)`
8. **Production-safe defaults** — `ddlAuto="validate"`, no automatic schema changes
9. **Safety guards** — unconditional `delete()` / `update()` throw
10. **Fail loudly** — a commit that cannot commit throws; it never reports success

### Examples

Runnable examples for every public API live in `examples/`. They are compiled
as part of `mvn test` (extra test source root) but are never packaged:

```bash
mvn test-compile
mvn exec:java -Dexec.mainClass=me.pfzh.hibernatelite.examples.RunAll -Dexec.classpathScope=test
```

See [examples/README.md](examples/README.md) for the API coverage matrix.

## Building and Testing

```bash
mvn test              # unit + integration tests (H2 in-memory)
mvn package           # jar + Javadoc
mvn verify            # tests + Javadoc; no diagram generation
mvn -Pdiagrams verify # additionally generates/injects the architecture SVGs
                      # (requires Graphviz and a fontconfig installation)
```

The default `verify` does not need Graphviz or system fonts, so it also works in a
minimal headless container. The `diagrams` profile is opt-in; the Javadoc CI
workflow enables it explicitly.

The test suite currently contains **491 tests**, including an independent
source-derived audit suite under
`src/test/java/me/pfzh/hibernatelite/audit/` that pins the behaviours described in
this document.

## License

[Apache License 2.0](LICENSE)
