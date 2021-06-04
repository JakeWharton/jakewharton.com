---
title: 'Case-insensitive filesystems considered harmful (to me)'
layout: post

categories: post
---

Having been burned by case-insensitive filesystem bugs one too many times, I long ago switched my development folder to a case-sensitive filesystem partition on my otherwise case-insensitive Mac. Unfortunately this can actually work against me as I interact with the computers of coworkers and service providers which use the default. Well I was burned again, and this is the tale!

I've been working on two projects based on [Jetpack Compose][1][^1] which require me to recompile its sources. Despite building them unmodified, I still run its tests against my compiled version to ensure this core functionality of my project behaves as expected. However, both of my projects recently started experiencing test failures on CI, and it was the same, single test failing on both projects.

 [1]: https://developer.android.com/jetpack/compose
 [^1]: Obligatory: [I mean Compose and **NOT** Compose UI][2]!
 [2]: /a-jetpack-compose-by-any-other-name/

The first project [failed about a month ago][3] when I added a MacOS worker in addition to the Linux worker to build a JNI library. Being so focused on the JNI compilation, I figured the Compose failure was a flake or something wrong with my setup. Its failure was:
```
androidx.compose.runtime.CompositionTests[jvm] > testInsertOnMultipleLevels[jvm] FAILED
    java.lang.NoClassDefFoundError: androidx/compose/runtime/CompositionTests$testInsertOnMultipleLevels$1$item$1 (wrong name: androidx/compose/runtime/CompositionTests$testInsertOnMultipleLevels$1$Item$1)
        at java.base/java.lang.ClassLoader.defineClass1(Native Method)
         ⋮
        at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:522)
        at androidx.compose.runtime.CompositionTests$testInsertOnMultipleLevels$1.invokeSuspend$Item(CompositionTests.kt:2055)
```
Like I said I didn't look too closely at this output and assumed it was my own fault.

[3]: https://github.com/JakeWharton/mosaic/runs/2547311635

The second project (which is not open source yet) started failing yesterday when I added a Windows worker to publish new targets for its Kotlin multiplatform library. Notably, the project already had a MacOS worker, and the PR to add the Windows worker did see both workers succeed. The merge commit, however, failed with an exception on the Windows worker which looked awfully familiar:
```
androidx.compose.runtime.CompositionTests[jvm] > testInsertOnMultipleLevels[jvm] FAILED
    java.lang.NoClassDefFoundError: androidx/compose/runtime/CompositionTests$testInsertOnMultipleLevels$1$Item$1 (wrong name: androidx/compose/runtime/CompositionTests$testInsertOnMultipleLevels$1$item$1)
        at java.lang.ClassLoader.defineClass1(Native Method)
         ⋮
        at java.lang.ClassLoader.loadClass(ClassLoader.java:351)
        at androidx.compose.runtime.CompositionTests$testInsertOnMultipleLevels$1.invokeSuspend$Item(CompositionTests.kt:2055)
```
"It's the same exception!", my brain thought. But if you look closely it _is_ the same but it's also different. In this case we tried to load `CompositionTests$testInsertOnMultipleLevels$1$Item$1` (note the uppercase "i" in `Item`) but found a class named `CompositionTests$testInsertOnMultipleLevels$1$item$1` (note the lowercase "i" in `item`). This is in contrast to the first exception above where the "item" casing is reversed.

Cracking open `CompositionTests` we can look at the `testInsertOnMultipleLevels` method and see the source of this class:
```kotlin
fun testInsertOnMultipleLevels() = compositionTest {
  // …code…

  fun Item(number: Int, numbers: List<Int>) {
    Linear {
      // --> This lambda is the source! <--
      // …code…
    }
  }

  // …code…
}
```

The anonymous lambda passed to `compositionTest` becomes `$1`, the nested `Item` function becomes `$Item`, and the lambda passed to `Linear` becomes another `$1` producing the final class name of `CompositionTests$testInsertOnMultipleLevels$1$Item$1`.

This all seems fine, though. So how could the name of the class for the function change casing from `Item` to `item`?

Thankfully, with the investigative powers of [Isaac Udy](https://medium.com/@isaac.udy_90859) helping, we stumbled upon more code further down the function:

```kotlin
fun testInsertOnMultipleLevels() = compositionTest {
  // …code…

  fun Item(number: Int, numbers: List<Int>) {
    Linear {
      // …code…
    }
  }

  // …code…

  fun MockViewValidator.item(number: Int, numbers: List<Int>) {
    Linear {
      // …code…
    }
  }

 // …code…
}
```

The class generation in this second nested function follow a similar formula to the first. The anonymous lambda passed to `compositionTest` once again becomes `$1`, the nested `MockViewValidator.item` function becomes `$item`, and the lambda passed to `Linear` becomes another `$1` producing the final class name of `CompositionTests$testInsertOnMultipleLevels$1$item$1`.

And there it is. The lambda inside first function produces a class named `CompositionTests$testInsertOnMultipleLevels$1$Item$1` which is written to `CompositionTests$testInsertOnMultipleLevels$1$Item$1.class` on the filesystem. The lambda inside the second function produces a class named `CompositionTests$testInsertOnMultipleLevels$1$item$1` which is written to `CompositionTests$testInsertOnMultipleLevels$1$item$1.class` on the filesystem. Except on a case-insensitive filesystem, _those are the same file!_

To be clear, the problematic steps are this:
 1. The build system cleans the output directory giving us a blank slate on the filesystem.
 2. The Kotlin compiler generates the class `CompositionTests$testInsertOnMultipleLevels$1$Item$1`.
 3. The Kotlin compiler opens the `CompositionTests$testInsertOnMultipleLevels$1$Item$1.class` file (which does not exist and is created), writes the bytecode for `CompositionTests$testInsertOnMultipleLevels$1$Item$1`, and closes the file.
 4. The Kotlin compiler generates the class `CompositionTests$testInsertOnMultipleLevels$1$item$1`.
 5. The Kotlin compiler opens the `CompositionTests$testInsertOnMultipleLevels$1$item$1.class` file (but the filesystem sees `CompositionTests$testInsertOnMultipleLevels$1$Item$1.class` as an existing match and opens it as an existing file), writes the bytecode for `CompositionTests$testInsertOnMultipleLevels$1$item$1`, and closes the file.

When the project builds on my machine the non-standard, case-sensitive filesystem sees those as separate files and the failure does not occur. On MacOS- and Windows-based CI workers with their filesystem defaults, however, they're seen as the same and one overwrites the other. This is what leads to the class name of the second appearing in the file name of the first.

The fix here is easy: rename one of the functions to produce different names. And in an ironic twist of timing, JetBrains [made the exact fix][4] to Compose just 12 hours ago.

```diff
-fun MockViewValidator.item(number: Int, numbers: List<Int>) {
+fun MockViewValidator.validateItem(number: Int, numbers: List<Int>) {
   Linear {
     // …code…
   }
 }
```

A simple git submodule update and all my problems are now solved.

 [4]: https://android.googlesource.com/platform/frameworks/support/+/f705520d29e250a762c7c8ba354715e3def6fcde%5E!/

Or are they?

This is not the first time I have had this problem, and it likely won't be the last. I would like to make the argument that this is a Kotlin compiler bug. Regardless of whether you are targeting a case-insensitive filesystem, the Kotlin compiler could avoid this entire class of problem by further mangling the name of this otherwise unnamed type to avoid case-insensitive collision.

You can trivially reproduce this if you have a case-insensitive filesystem:
```kotlin
class Hey
class hey
```
```
$ kotlinc Hey.kt
$ ls Hey*
Hey.class	Hey.kt
```

And a minimal reproducer for the more cryptic cause in this post would be:
```kotlin
fun complex() = run {
  fun Nested() {
    run { println("Nested") }
  }
  fun String.nested() {
    run { println("String.nested") }
  }
}
fun run(lambda: () -> Unit) = lambda()
```
```
$ kotlinc Complex.kt
$ ls Complex*
Complex.kt	ComplexKt$complex$1$Nested$1.class	ComplexKt$complex$1.class	ComplexKt.class
```

I have filed [KT-47123](https://youtrack.jetbrains.com/issue/KT-47123) to advocate that the compiler should automatically prevent this from happening.

Hey Java users you're not totally immune either!

```java
class Hey {}
class hey {}
```
```
$ javac Hey.java
$ ls Hey*
Hey.class	Hey.java
```

I'm confident that _this_ year will finally be the year of the Linux desktop to solve all these problems with its case-sensitive-by-default filesystems, right? But until then, having tools which are smarter about filesystem interaction in a world where both case-sensitive and case-insensitive variants exist would go a long way to reducing developer headaches like this.
