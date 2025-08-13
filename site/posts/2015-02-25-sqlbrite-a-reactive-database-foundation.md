---
title: "SQLBrite: A reactive Database Foundation"
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/sqlbrite-a-reactive-database-foundation

categories: post
tags:
- Android
- Database
---

Storing, accessing, and modifying persisted data on Android has been an ever-changing landscape since the platform’s inception. Last year, after evaluating 16 libraries on a spectrum of strict requirements, we determined that nothing existed which met all our goals and instead chose to embark on writing our own solution.

SQLite is the obvious choice for persisting and querying complex data. Over the last year we designed, debated, and prototyped a complete solution for simplifying an application’s SQLite interaction. This included features such as automatic table creation and migration, object-mapping for rows, type-safe querying, and notifications for when data changes.

When it came time to integrate with [Square Cash](https://square.com/cash), we knew we had a lot of great concepts, but the implementation suffered from being heavily churned upon. So a few weeks ago, we decided to incrementally rewrite the library from scratch in order to retain the ideas and concepts — but have proper architecture.

[SQLBrite](https://github.com/square/sqlbrite) is the first release of this effort and it will serve as the foundation of future additions. Instead of single executions, you can subscribe to queries using [RxJava observables](https://github.com/ReactiveX/RxJava/):

```java
Observable<Query> users = db.createQuery("user", "SELECT * FROM user");
users.subscribe(new Action1<Query>() {
  @Override public void call(Query query) {
    Cursor cursor = query.run();
    // TODO parse data...
  }
});
```

No attempt is made to hide SQL, Cursor, or the semantics of SQLiteOpenHelper(Android’s SQLite wrapper). Instead, those three concepts are given a superpower: data change notifications.

Whenever data in a table is updated from insert, update, or delete operations (whether in a transaction or as a one-off), subscribers to that data are updated.

```java
final AtomicInteger count = new AtomicInteger();
users.subscribe(new Action1<Query>() {
  @Override public void call(Query query) {
    count.getAndIncrement();
  }
});
System.out.println("Queries: " + count.get()); // Prints 1

db.insert("user", createUser("jw", "Jake Wharton"));
db.insert("user", createUser("mattp", "Matt Precious"));
db.insert("user", createUser("strong", "Alec Strong"));

System.out.println("Queries: " + count.get()); // Prints 4
```

When multiple queries are constantly refreshed with data, the UI updates in real-time instead of staying as a simple, static page.

![](/static/post-image/sqlbrite.png)

SQLBrite is open source on GitHub at [github.com/square/sqlbrite](https://github.com/square/sqlbrite) as a first step. Over time, the feature set will become more comprehensive with the help and support of the community.
