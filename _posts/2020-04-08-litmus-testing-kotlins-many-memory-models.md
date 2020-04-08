---
title: "Litmus-Testing Kotlin's Many Memory Models"
layout: post

categories: post
tags:
- Kotlin
---

When writing multiplatform code, Kotlin's three compiler backends each have different memory models which must be considered.

JavaScript is single-threaded so you really can do no wrong. The JVM model is arguably too permissive where you can do incorrect things and have them work 99.9% of the time. When targeting native, Kotlin enforces some invariants which helps prevent you from those 0.1% bugs that crop up in the JVM.

I've been porting the [AndroidX collection library][collection] to Kotlin multiplatform to experiment with binary compatibility, performance, tooling, and the different memory models. The library consists of mutable, single-threaded data structures. This should mean the different memory models never come into play. But weirdly they do, and let's look at how.

 [collection]: https://developer.android.com/reference/androidx/collection/package-summary


### On Deck

The Kotlin standard library contains general-purpose collections like lists, sets, and maps in both mutable and read-only form. Kotlin&nbsp;1.3.70 added another collection, [`ArrayDeque`][deque], a "double-ended queue" for efficient stacks and queues. 

 [deque]: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-array-deque/

During the 1.3.70 EAP, Kevin Galligan opened [an issue][deque-issue] where `ArrayDeque` could _only_ be instantiated on the main thread and not a background thread when targeting Kotlin/Native. At the time I didn't read into it, but as I was porting these collections it came to mind.

 [deque-issue]: https://github.com/JetBrains/kotlin-native/issues/3876

The underlying cause was that the implementation relied on a top-level `val` for a shared, empty array when the collection was empty. Arrays are fixed-length, so an empty array is effectively immutable and thus can be shared by all empty collections. But that seems fine?

It _is_ fine for Kotlin/JS and Kotlin/JVM but Kotlin/Native is different here. By default, Kotlin/Native only allows the main thread to access top-level `val`s. If you want to access the value from multiple threads (potentially concurrently) you must choose whether you want thread-local or shared-but-immutable behavior with an annotation. `ArrayDeque`'s empty array was missing this annotation.

As it turns out, my collections had the exact same issue! Each started with a shared, empty array and only allocated its own storage when the first element arrived. I had tests, but the tests were only exercising the type on the main thread. It's an easy fix, just add `@SharedImmutable`, but how do I prevent regression and future problems of this nature?


### Testing Threads

Since Kotlin/Native enforces different semantics between its main thread and background threads, it's only logical to run the tests once on the main thread and once on a background thread to ensure compliance.

If our test is written solely for Kotlin/Native this is pretty easy. The native version of the standard library has a `Worker` API for running on a background thread.

```kotlin
fun threadedTest(body: () -> Unit) {
  body()

  body.freeze()
  val worker = Worker.start()
  val future = worker.execute(SAFE, { body }) {
    runCatching(it)
  }
  future.result.getOrThrow()
}
```

This function accepts a lambda which it runs synchronously (which will be on the main thread) and then transfers that lambda to a background thread where it's run a second time. The main thread blocks on the result of the background thread where it rethrows any exceptions that occurred.

Each test case is updated to put its body inside a call to this function.

```diff
-@Test fun isEmpty() {
+@Test fun isEmpty() = threadedTest {
   val map = ArrayMap()
   assertTrue(map.isEmpty())
 }
```

Running without `@SharedImmutable` now causes the test to correctly fail. Say goodbye to an entire class of Kotlin/Native bugs!


### Multiplatform

For multiplatform libraries, like my collection library, the tests are written in platform-agnostic "common" Kotlin with no access to the Kotlin/Native-specific `Worker` API. We can instead rely on the expect/actual language feature of multiplatform Kotlin to make this work.

In `src/commonTest/kotlin/` the `threadedTest` function is declared as an `expect fun`:
```kotlin
expect fun threadedTest(body: () -> Unit)
```

The native-specific implementation is put in `src/nativeTest/kotlin/`:
```kotlin
actual fun threadedTest(body: () -> Unit) {
  // Same as Kotlin/Native code from previous section.
}
```

For JavaScript in `src/jsTest/kotlin/` we don't need threading so its implementation just inlines itself away.
```kotlin
actual inline fun threadedTest(body: () -> Unit) = body()
```

For the JVM in `src/jvmTest/kotlin/` you're free to either inline it away like JavaScript or use the `Thread` APIs to invoke `body` twice. Since the memory models of the JVM and Android give no special treatment to the main thread there's really no reason to run it twice.

Now our test from the previous section can live in `src/commonTest/kotlin/` and wrap itself in `threadedTest`. On JS and JVM the test will run normally and only on native targets will it run twice.

---

The memory model of Kotlin/Native helps eliminate bugs that would probabilistically occur on more permissive platforms like the JVM. With the constraints of its memory model being runtime checked, running your unit tests on both the main thread and a background thread prevent bugs like the one which occurred with `ArrayDeque`.

I filed [an issue][test-annotation] on the Kotlin/Native repo asking for some kind of built-in mechanism to support this use case. And ideally it would be something that you could apply to a whole class rather than having to remember to do it for each function.

 [test-annotation]: https://github.com/JetBrains/kotlin-native/issues/4075