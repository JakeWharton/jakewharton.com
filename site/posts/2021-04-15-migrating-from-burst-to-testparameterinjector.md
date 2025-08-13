---
title: "Migrating from Burst to TestParameterInjector"
layout: post

external: true
blog: Cash App Code Blog
blog_link: https://code.cash.app/migrating-from-burst-to-testparameterinjector

categories: post
tags:
- Android
---

Square’s [Burst](https://github.com/square/burst) library burst onto the scene in 2014 stemming from general dissatisfaction with JUnit 4’s built-in parameterized runner. It enabled the use of enums to vary input to your test class, test methods, or some combination of both.

```kotlin
enum class Soda { PEPSI, COKE }

@RunWith(BurstJUnit4::class)
class DrinkSodaTest(
  private val soda: Soda,
) {
  @Test fun canDrink() {
    // TODO...
  }
}
```

The `canDrink` test will now run twice, once for each enum value. We used this to vary the themes, device sizes, accessibility settings, and more in our unit, integration, and snapshot tests.

The library's ongoing maintenance burden is low. In fact, it hasn't seen a release in 4 years simply because it's very stable. This doesn't mean it's outdated—new tests are written with Burst to this day.

At least up until yesterday.

A few weeks ago Google [announced](https://opensource.googleblog.com/2021/03/introducing-testparameterinjector.html) the release of [TestParameterInjector](https://github.com/google/TestParameterInjector), a library which subsumes Burst's approach and expands it to cover more types and offer more functionality. The Burst library is now being deprecated in favor of TestParameterInjector for JUnit 4-based projects.

We have migrated our Cash App Android codebase and will be migrating others in time, including our open source projects. Thankfully the migration is straightforward.

```diff
-import com.squareup.burst.BurstJUnit4
+import com.google.testing.junit.testparameterinjector.TestParameter
+import com.google.testing.junit.testparameterinjector.TestParameterInjector
 
 enum class Soda { PEPSI, COKE }
 
-@RunWith(BurstJUnit4::class)
+@RunWith(TestParameterInjector::class)
 class DrinkSodaTest(
-  private val soda: Soda,
+  @TestParameter private val soda: Soda,
 ) {
   @Test fun canDrink() {
     // TODO...
   }
 }
```

If you were using the `@Burst` annotation for field binding (common for Java tests), replace it with `@TestParameter`

```diff
-@RunWith(BurstJUnit4.class)
+@RunWith(TestParameterInjector.class)
 class DrinkSodaTest {
-  @Burst Soda soda;
+  @TestParameter Soda soda;
 
   // ...
 }
```

Burst-injected test parameters also need to add `@TestParameter`.

```diff
-@RunWith(BurstJUnit4::class)
+@RunWith(TestParameterInjector::class)
 class DrinkSodaTest {
   @Test fun canDrink(
-    soda: Soda,
+    @TestParameter soda: Soda,
   ) {
     // TODO...
   }
 }
```

Once you've migrated, check out TestParameterInjector's support for [more types](https://github.com/google/TestParameterInjector#supported-types) as well as [dynamically-generated data](https://github.com/google/TestParameterInjector#dynamic-parameter-generation-for-testparameter).

One quick heads up for Android users: if you are switching from Burst to TestParameterInjector for your instrumentation tests (that's the 'androidTest' ones) be aware that it currently only works on API 26 and newer. Follow along on [this issue](https://github.com/google/TestParameterInjector/issues/2) to track supporting earlier API levels.
