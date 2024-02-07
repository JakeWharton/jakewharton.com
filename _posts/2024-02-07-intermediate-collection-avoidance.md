---
title: 'Intermediate collection avoidance'
layout: post

categories: post
tags:
- Kotlin
---

Given a list of users, extract their names and join them into a comma-separated list.
Kotlin's extension functions on collections make this trivial.

```kotlin
users.map { it.name }.joinToString()
```

Writing this in IntelliJ IDEA produces a "weak warning" offering advice.

> Call chain on collection type may be simplified

An intention action will refactor the code for you to a more efficient form.

```kotlin
users.joinToString() { it.name }
```

Mapping the user to their name now occurs during construction of the joined string rather than as a discrete operation.
The additional iterator and intermediate collection produced by the `map` is eliminated.

This code is both shorter and faster, and the IDE helps you discover this superior form.

Two similar fused operations that I like but which don't benefit from IDE advice are array and pre-sized list initialization with a lambda.

If we wanted to create an array of our user's names, instead of doing
```kotlin
users.map { it.name }.toTypedArray()
```
we can use
```kotlin
Array(users.size) { users[it].name }
```
This again trades the intermediate iterator and collection within `map` for an indexed loop.
Primitive array versions are also available.
```kotlin
IntArray(users.size) { users[it].age }
```

Arrays are not used too often.
Mostly for memory-sensitive or performance-sensitive code, or when calling out to a Java API.
Thankfully this lambda-accepting initializer is also available for pre-sized lists.

```kotlin
MutableList(users.size) { users[it].name }
```

Use this to initialize element default values, compute elements based on the index, or derive data from another source.

In the case of deriving data, the source needs to support random access in order to actually result in a more efficient computation.[^1] If you use a list backed by an alternate structure (linked, persistent, etc.) performance will be abysmal. This technique works best for internal library usage and should not be used when you don't control the original list.

[^1]: You might be aware of Compose UI's _horribly_-named ["fast" collection functions](https://developer.android.com/reference/kotlin/androidx/compose/ui/util/package-summary#(kotlin.collections.List).fastMap(kotlin.Function1)) which also use this strategy.

```
Benchmark                                       Score     Error   Units
--------------------------------------------- ---------- -------- -----
NamesJoinToString.map                         126.582 ±  38.237   ns/op
NamesJoinToString.map:·gc.alloc.rate.norm     232.000 ±   0.001    B/op
NamesJoinToString.lambda                       73.586 ±   1.960   ns/op
NamesJoinToString.lambda:·gc.alloc.rate.norm  168.000 ±   0.001    B/op

NamesToTypedArray.map                          78.444 ±  22.427   ns/op
NamesToTypedArray.map:·gc.alloc.rate.norm     120.000 ±   0.001    B/op
NamesToTypedArray.lambda                       10.326 ±   0.129   ns/op
NamesToTypedArray.lambda:·gc.alloc.rate.norm   40.000 ±   0.001    B/op
```

As you can see in the benchmarks above, the lambda initialization variants are both faster due to the use of indexed loops and allocate fewer bytes with no iterator or intermediate collection. We could hand-write such loops, but Kotlin's zero-overhead functions keep our code short and sweet.
