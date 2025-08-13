---
title: "Flow testing with Turbine"
layout: post

external: true
blog: Cash App Code Blog
blog_link: https://code.cash.app/flow-testing-with-turbine

categories: post
tags:
  - Android
  - Kotlin
---

Say hello to [Turbine 1.0](https://github.com/cashapp/turbine), our library for testing kotlinx.coroutines `Flow` and more.

Turbine changes push-based `Flow`s into pull-based suspend functions to simplify testing.

```kotlin
mealsFlow.test {
  assertEquals(Meal.Breakfast, awaitItem())
  assertEquals(Meal.Lunch, awaitItem())
  assertEquals(Meal.Dinner, awaitItem())
  awaitComplete()
}
```

Each `awaitItem()` or `awaitComplete()` call suspends until the desired event arrives. If a different event occurs or a timeout is reached the functions throw an `AssertionError` and fail your test. You can also wait for errors, skip items, cancel the `Flow`, and more.

In addition to its use with `Flow`, standalone `Turbine`s can be created to adapt other push-based mechanisms like callbacks for testing.

```kotlin
class FakeLogger : Logger {
  val messages = Turbine<String>()

  override fun log(message: String) {
    messages += message
  }
}
```

The standalone `Turbine` offers the same API as the `test` function for pulling out values.

```kotlin
val logger = FakeLogger()

val userService = UserService(logger)
assertNull(userService.get(4552))

with(logger.messages) {
  assertEquals("HTTP --> /user/4552 GET", awaitItem())
  assertEquals("HTTP <-- /user/4552 404 Not Found", awaitItem())
}
```

As your testing needs grow, the library can help with utilities for multiple `Turbine`s, multiple `Flow`s, sharing timeouts, aggregating errors, and more.