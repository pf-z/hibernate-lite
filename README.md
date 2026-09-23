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

Hibernate-Lite is a lightweight ORM layer built on top of Hibernate ORM, designed to simplify database development while keeping access to Hibernate's powerful capabilities.

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
- [Error Handling](#error-handling)
- [Design Principles](#design-principles)
- [Full API Index](#full-api-index)
- [License](#license)

## Overview

Hibernate-Lite removes the boilerplate of DAO / Repository layers while preserving Hibernate's full power underneath.

If you have ever wished for:

- **Zero Repository interfaces** — no `UserRepository`, no `UserDao`, no `UserDaoImpl`
- **Automatic transactions** — `db.save(user)` just works
- **Transparent session management** — no `Session`, no `EntityManager` in your business code
- **Type-safe queries** — `User::getName` instead of `"name"`
- **Escape hatch** — full access to native Hibernate when needed

then Hibernate-Lite is for you.

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
- **Conditional update and delete** — single SQL statement, with safety guards
- **Nested transaction support** — REQUIRED semantics
- **Thread-safe by design** — `Session` is bound to `ThreadLocal`, cleaned up automatically
- **Native Hibernate exceptions wrapped** into `HibernateLiteException`
- **Escape hatches** — `where(QuerySpec)` and `unwrap(SessionFactory.class)`

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

Returns a `Builder` for configuring the library.

| Method | Required | Default | Description |
|--------|----------|---------|-------------|
| `dataSource(DataSource)` | ✅ | — | Connection pool (Hikari, DBCP, etc.) |
| `entities(Class<?>...)` | ✅ | — | At least one entity class |
| `showSql(boolean)` | ❌ | `false` | Print generated SQL |
| `ddlAuto(String)` | ❌ | `"validate"` | Schema strategy |

**`ddlAuto` values**:

- `"none"` — do nothing
- `"validate"` — **default**, check schema only (production-safe)
- `"update"` — auto-create/update tables (development)
- `"create"` — recreate on startup
- `"create-drop"` — create on startup, drop on shutdown

#### `DataStore.close()`

Releases the `SessionFactory` and related resources. `DataStore` implements `AutoCloseable`, so it can be used in try-with-resources.

```java
try (DataStore db = HibernateLite.builder()...build()) {
    // ...
}
```

**Notes**:

- Idempotent — safe to call multiple times
- After calling `close()`, the `DataStore` must not be used again

### 2. CRUD

#### `find(Class<T> type, Object id)`

Find an entity by primary key.

```java
User u = db.find(User.class, 1L);
```

- Returns `null` if no entity exists
- Hits Hibernate first/second-level cache
- Throws `IllegalArgumentException` if `type` or `id` is `null`

#### `save(T entity)`

Smart save — behavior depends on the entity's ID state:

| ID state | Entity type | Behavior |
|----------|-------------|----------|
| `null` | Any | **INSERT** (`persist`) |
| non-null | Has `@GeneratedValue` | **UPDATE** (`merge`) |
| non-null | Business key (no `@GeneratedValue`) | ❌ **Throws exception** |

```java
// Insert
User u = new User("Alice");
u = db.save(u);

// Update
u.setName("Alice Smith");
u = db.save(u);
```

**Notes**:

1. **Always use the return value** — `merge` returns a new managed instance
2. **Business keys not supported** — `save` throws for entities without `@GeneratedValue`
3. **Do not manually set ID then save** — may accidentally UPDATE instead of INSERT

#### `saveAll(List<T> entities)`

Batch save all entities in one transaction.

```java
List<User> users = List.of(
        new User("Alice"),
        new User("Bob"),
        new User("Charlie")
);
List<User> saved = db.saveAll(users);
```

- All entities saved in one transaction
- Flushes and clears the persistence context every **50 rows**
- Any failure → all roll back
- **Returned entities may be detached** due to flush/clear

#### `delete(Object entity)`

Delete an entity.

```java
User u = db.find(User.class, 1L);
db.delete(u);

// Detached entity also works
User detached = new User();
detached.setId(1L);
db.delete(detached);
```

- Idempotent — silently returns if no matching record exists

### 3. Query DSL

#### Create a query

```java
<T> LambdaQuery<T> query(Class<T> type)
```

Returns a chainable query object. **`LambdaQuery` is not thread-safe** — create a new one per query.

#### Operators

All condition methods return `this` for chaining. Multiple conditions are **AND-combined** by default.

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

**Field references use getter method references**: `User::getName`, `User::isActive`.

#### Multiple conditions (AND)

```java
List<User> users = db.query(User.class)
        .eq(User::getStatus, "ACTIVE")
        .gt(User::getAge, 18)
        .like(User::getName, "A%")
        .list();
// WHERE status = ? AND age > ? AND name LIKE ?
```

#### Ordering

```java
// Ascending
db.query(User.class).orderByAsc(User::getName).list();

// Descending
db.query(User.class).orderByDesc(User::getCreatedAt).list();

// Multiple fields (applied in order)
db.query(User.class)
        .orderByAsc(User::getStatus)
        .orderByDesc(User::getAge)
        .list();
```

#### Aggregates and single result

| Method | Description |
|--------|-------------|
| `list()` | All matching entities, default limit 1000 |
| `list(int limit)` | Limited list |
| `one()` | Exactly one (0 → null; >1 → throws) |
| `count()` | Count matching entities |
| `exists()` | Whether any entity matches |

```java
List<User> users = db.query(User.class).eq(User::getStatus, "ACTIVE").list();

List<User> top10 = db.query(User.class).orderByDesc(User::getAge).list(10);

User u = db.query(User.class).eq(User::getEmail, "alice@x.com").one();

long count = db.query(User.class).eq(User::getStatus, "ACTIVE").count();

boolean exists = db.query(User.class).eq(User::getName, "Alice").exists();
```

#### Conditional delete

```java
int deleted = db.query(User.class)
        .lt(User::getAge, 18)
        .delete();
```

**Safety guard**: **unconditional delete throws an exception**:

```java
db.query(User.class).delete();
// ❌ HibernateLiteException:
//    delete() requires at least one condition or where() clause;
//    refusing to delete all rows of ...
```

**Escape hatch + conditional delete**:

```java
int deleted = db.query(User.class)
        .gt(User::getAge, 0)
        .where((cb, root) -> cb.equal(root.get("status"), "INACTIVE"))
        .delete();
```

#### Conditional update

**Single field**:

```java
int updated = db.query(Order.class)
        .lt(Order::getCreatedAt, cutoff)
        .update(Order::getStatus, "CANCELLED");
```

**Multiple fields**:

```java
int updated = db.query(User.class)
        .eq(User::getName, "Alice")
        .update(Map.of(
                "age", 26,
                "status", "VIP"
        ));
```

**Escape hatch + conditional update**:

```java
int updated = db.query(User.class)
        .where((cb, root) -> cb.lt(root.get("age"), 18))
        .update(User::getStatus, "MINOR");
```

**Safety guard**: **unconditional update throws an exception**.

### 4. JPQL

#### Create a JPQL query

```java
<T> JpqlQuery<T> query(String jpql, Class<T> resultType)
```

- `jpql` — standard JPQL string
- `resultType` — entity class or scalar (`Long.class`)

#### Parameters

```java
JpqlQuery<T> param(String name, Object value)
```

- Name does not include the leading `:`
- Chainable
- Value can be `null`

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
| `one()` | Single result (0 → null; >1 → throws) |
| `count()` | Count (JPQL must be `SELECT COUNT(...)`) |
| `exists()` | Existence check |
| `page(PageRequest)` | **Throws `UnsupportedOperationException`** |
| `page(String countJpql, PageRequest)` | Pagination (must provide count JPQL) |

#### Examples

```java
// Multi-table JOIN
List<Order> orders = db.query(
        "SELECT o FROM Order o JOIN o.user u WHERE u.status = :status",
        Order.class)
        .param("status", "ACTIVE")
        .list();

// Count
long count = db.query(
        "SELECT COUNT(u) FROM User u WHERE u.status = :status",
        Long.class)
        .param("status", "ACTIVE")
        .count();

// Pagination (must provide count JPQL)
Page<Order> page = db.query(
        "SELECT o FROM Order o WHERE o.status = :status",
        Order.class)
        .param("status", "PENDING")
        .page(
                "SELECT COUNT(o) FROM Order o WHERE o.status = :status",
                PageRequest.of(0, 20));
```

#### Capabilities

**Supported**:

- Multi-table `JOIN`
- Subqueries
- `GROUP BY` / `HAVING`
- `ORDER BY`
- Aggregates

**Not supported**:

- Native SQL — use `unwrap(SessionFactory.class)`
- Stored procedures
- Database-specific syntax

#### Exceptions

- `jpql` is `null` or blank → `IllegalArgumentException`
- `resultType` is `null` → `IllegalArgumentException`
- JPQL syntax error → **Hibernate 6 throws `IllegalArgumentException`**
- Runtime errors → `HibernateLiteException`

### 5. Pagination

#### `PageRequest`

**`PageRequest.of(int page, int size)`**:

- `page` — **0-based** (first page is 0)
- `size` — page size, **max 1000**
- **Total count is enabled by default**

**`PageRequest.firstPage(int size)`**: equivalent to `PageRequest.of(0, size)`.

**`PageRequest.withoutCount()`**:

- Returns a **new instance** (immutable)
- **Disables total count**, but `hasNext` is still **accurate** (fetches one extra row)

```java
PageRequest r1 = PageRequest.of(0, 20);                  // First page, with total
PageRequest r2 = PageRequest.of(1, 20);                  // Second page
PageRequest r3 = PageRequest.of(0, 20).withoutCount();   // Without total
```

#### DSL pagination

```java
Page<User> page = db.query(User.class)
        .eq(User::getStatus, "ACTIVE")
        .orderByDesc(User::getCreatedAt)
        .page(PageRequest.of(0, 20));
```

#### JPQL pagination

**Must provide count JPQL**:

```java
Page<User> page = db.query(
        "SELECT u FROM User u WHERE u.status = :status ORDER BY u.createdAt DESC",
        User.class)
        .param("status", "ACTIVE")
        .page(
                "SELECT COUNT(u) FROM User u WHERE u.status = :status",
                PageRequest.of(0, 20));
```

#### `Page<T>` API

| Method | Description |
|--------|-------------|
| `content()` | Page content (**unmodifiable**) |
| `numberOfElements()` | Actual element count |
| `isEmpty()` | Whether the page is empty |
| `page()` | 0-based page number |
| `size()` | Requested page size |
| `totalElements()` | **Total elements** (throws `IllegalStateException` in slice mode) |
| `totalPages()` | **Total pages** (throws in slice mode) |
| `hasTotal()` | Whether total count is available |
| `hasNext()` | Whether another page exists |
| `hasPrevious()` | Whether a previous page exists |
| `isFirst()` | Whether this is the first page |
| `isLast()` | Whether this is the last page |
| `map(Function)` | DTO mapping, **preserves metadata** |

#### Total-count mode vs Slice mode

| Aspect | With count | Without count |
|--------|-----------|---------------|
| `totalElements()` | ✅ | ❌ throws |
| `totalPages()` | ✅ | ❌ throws |
| `hasTotal()` | `true` | `false` |
| `hasNext()` | accurate | **accurate** |
| `isLast()` | accurate | **accurate** |
| SQL queries | 2 (data + count) | 1 (data, fetches one extra row) |
| Use case | "total X, page Y" | Infinite scroll, load more |

#### DTO mapping

```java
Page<UserDto> dtos = page.map(UserDto::from);
```

Preserves: `page`, `size`, `totalElements`, `hasNext`, `hasTotal`.

### 6. Transactions

#### Programmatic (with return value)

```java
<T> T transaction(TransactionCallback<T> callback)
```

- Normal return → commit
- Throws `RuntimeException` → rollback + rethrow

```java
Long id = db.transaction(() -> {
    User u = db.save(new User("Alice"));
    db.save(new Order(u));
    return u.getId();
});
```

#### Programmatic (without return value)

```java
void transaction(Runnable work)
```

```java
db.transaction(() -> {
    db.save(order);
    db.save(payment);
});
```

#### Nested transactions

**REQUIRED propagation** — inner calls reuse the outer transaction.

```java
db.transaction(() -> {              // Outer
    db.save(a);
    db.transaction(() -> {          // Inner — reuses outer transaction
        db.save(b);
    });
    db.save(c);
});                                  // Commits all
```

**Inner exception swallowed — outer still rolls back**:

```java
db.transaction(() -> {
    db.save(a);
    try {
        db.transaction(() -> {
            throw new RuntimeException("inner");
        });
    } catch (RuntimeException ignored) {
        // Swallowed by user
    }
    db.save(b);                     // ← Still rolled back
});
// Throws HibernateLiteException: Transaction marked rollback-only
```

#### Automatic transactions

The following methods automatically wrap themselves in a transaction when no outer transaction exists:

- `save` / `saveAll` / `delete`
- `find`
- All terminal operations on `LambdaQuery` and `JpqlQuery`

You never need to manage transactions manually for a single operation.

### 7. Escape Hatches

#### `where(QuerySpec<T>)` — raw Criteria

```java
LambdaQuery<T> where(QuerySpec<T> spec)
```

- Combined with DSL conditions via **AND**
- Can be called multiple times
- Returning `null` means no additional restriction

```java
// OR nesting
List<User> users = db.query(User.class)
        .where((cb, root) -> cb.or(
                cb.like(root.get("name"), "A%"),
                cb.like(root.get("name"), "B%")
        ))
        .list();

// DSL + Criteria mixed
List<User> users = db.query(User.class)
        .eq(User::getStatus, "ACTIVE")
        .where((cb, root) -> cb.gt(root.get("age"), 25))
        .list();
// WHERE status = ? AND age > ?
```

**Note**: `root.get("field")` takes plain strings, **not** compile-time checked.

#### `unwrap(Class<T> type)` — native Hibernate

```java
<T> T unwrap(Class<T> type)
```

**Currently supported**: `SessionFactory.class`.

```java
SessionFactory sf = db.unwrap(SessionFactory.class);

try (Session session = sf.openSession()) {
    session.beginTransaction();
    List<?> results = session.createNativeQuery("SELECT * FROM users", Object[].class)
            .list();
    session.getTransaction().commit();
}
```

**Notes**:

- Unsupported types → `HibernateLiteException`
- `null` → `IllegalArgumentException`
- **Bypasses the library's transaction and session management**
- Inside a `db.transaction(...)`, prefer `SessionContext.current(sf)` over `openSession()`

## Error Handling

### Exception types

| Exception | Trigger |
|-----------|---------|
| `HibernateLiteException` | Runtime errors within the library (wraps Hibernate exceptions) |
| `IllegalArgumentException` | Parameter validation failures (null / empty / invalid) |
| `NullPointerException` | `Objects.requireNonNull` scenarios |
| `UnsupportedOperationException` | `JpqlQuery.page(PageRequest)` single-arg version |

### Common errors

| Scenario | Exception | Message example |
|----------|-----------|-----------------|
| Unconditional `delete()` | `HibernateLiteException` | `refusing to delete all rows of ...` |
| Unconditional `update()` | `HibernateLiteException` | `refusing to update all rows of ...` |
| `save` with business key | `HibernateLiteException` | `Cannot determine save semantics ...` |
| Field does not exist | `HibernateLiteException` | `Entity ... has no field: nmae` |
| JPQL syntax error | `IllegalArgumentException` | Hibernate parser exception |
| `find(null, ...)` | `IllegalArgumentException` | `entity type cannot be null` |
| Negative page | `IllegalArgumentException` | `page must be >= 0` |
| Size over limit | `IllegalArgumentException` | `size must be in [1, 1000]` |
| `totalElements()` in slice mode | `IllegalStateException` | `Total element count is not available` |
| `unwrap(String.class)` | `HibernateLiteException` | `Unsupported unwrap type` |

### Exception wrapping rules

- **Hibernate native exceptions** → wrapped as `HibernateLiteException` (with `cause`)
- **Library exceptions** → rethrown as-is
- **Parameter validation exceptions** → thrown directly as `IllegalArgumentException`

## Design Principles

1. **Zero boilerplate** — no repository per entity
2. **Automatic transactions** — a single `save` wraps itself; batch operations share one transaction
3. **Thread-safe by design** — `Session` is bound to `ThreadLocal` and cleaned up automatically
4. **Nested transaction support** — `depth` counting implements REQUIRED propagation
5. **Rollback-only propagation** — inner failures mark the whole transaction, even if exceptions are swallowed
6. **Type-safe query DSL** — `User::getName` checked at compile time
7. **Escape hatches** — `where(QuerySpec)` and `unwrap(SessionFactory.class)`
8. **Production-safe defaults** — `ddlAuto="validate"`, no automatic schema changes
9. **Safety guards** — unconditional `delete()` and `update()` throw exceptions
10. **Immutable results** — `Page.content()` is unmodifiable

## Full API Index

### `DataStore`

| Method | Returns |
|--------|---------|
| `find(Class<T>, Object)` | `T` |
| `save(T)` | `T` |
| `saveAll(List<T>)` | `List<T>` |
| `delete(Object)` | `void` |
| `transaction(TransactionCallback<T>)` | `T` |
| `transaction(Runnable)` | `void` |
| `query(Class<T>)` | `LambdaQuery<T>` |
| `query(String, Class<T>)` | `JpqlQuery<T>` |
| `unwrap(Class<T>)` | `T` |
| `close()` | `void` |

### `LambdaQuery<T>`

| Method | Returns |
|--------|---------|
| `eq` / `ne` / `gt` / `ge` / `lt` / `le` | `LambdaQuery<T>` |
| `like(SFunction<T,?>, String)` | `LambdaQuery<T>` |
| `in` / `notIn(SFunction<T,?>, Collection<?>)` | `LambdaQuery<T>` |
| `isNull` / `isNotNull(SFunction<T,?>)` | `LambdaQuery<T>` |
| `between(SFunction<T,?>, Object, Object)` | `LambdaQuery<T>` |
| `orderByAsc` / `orderByDesc(SFunction<T,?>)` | `LambdaQuery<T>` |
| `where(QuerySpec<T>)` | `LambdaQuery<T>` |
| `list()` / `list(int)` | `List<T>` |
| `one()` | `T` |
| `count()` | `long` |
| `exists()` | `boolean` |
| `delete()` | `int` |
| `update(SFunction<T,?>, Object)` | `int` |
| `update(Map<String, Object>)` | `int` |
| `page(PageRequest)` | `Page<T>` |

### `JpqlQuery<T>`

| Method | Returns |
|--------|---------|
| `param(String, Object)` | `JpqlQuery<T>` |
| `list()` / `list(int)` | `List<T>` |
| `one()` | `T` |
| `count()` | `long` |
| `exists()` | `boolean` |
| `page(String countJpql, PageRequest)` | `Page<T>` |

### `PageRequest`

| Method | Returns |
|--------|---------|
| `of(int, int)` | `PageRequest` |
| `firstPage(int)` | `PageRequest` |
| `withoutCount()` | `PageRequest` |
| `offset()` | `int` |
| `page()` / `size()` | `int` |
| `countTotal()` | `boolean` |

### `Page<T>`

| Method | Returns |
|--------|---------|
| `content()` | `List<T>` |
| `numberOfElements()` | `int` |
| `isEmpty()` | `boolean` |
| `page()` / `size()` | `int` |
| `totalElements()` | `long` |
| `totalPages()` | `int` |
| `hasTotal()` | `boolean` |
| `hasNext()` / `hasPrevious()` | `boolean` |
| `isFirst()` / `isLast()` | `boolean` |
| `map(Function)` | `Page<R>` |

### `HibernateLite.Builder`

| Method | Returns |
|--------|---------|
| `dataSource(DataSource)` | `Builder` |
| `entities(Class<?>...)` | `Builder` |
| `showSql(boolean)` | `Builder` |
| `ddlAuto(String)` | `Builder` |
| `build()` | `DataStore` |

### `QuerySpec<T>`

| Method | Returns |
|--------|---------|
| `toPredicate(CriteriaBuilder, Root<T>)` | `Predicate` |

## Testing

```bash
mvn clean test
```

The project currently has **270+ tests** covering:

- CRUD operations
- Query DSL operators
- Sorting, aggregates, conditional delete/update
- JPQL queries
- Pagination (with / without count)
- Transactions (commit / rollback / nested)
- Escape hatches
- Metadata caching
- Thread binding and session lifecycle

## Contributing

Issues and pull requests are welcome.

## License

[Apache License 2.0](LICENSE)