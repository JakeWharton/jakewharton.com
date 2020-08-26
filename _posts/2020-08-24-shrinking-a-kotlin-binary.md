---
title: 'Shrinking a Kotlin binary by 99.2%'
layout: post

categories: post
---

We'll get to the shrinking, but first let's motivate the binary in question. Three years ago I wrote the ["Surfacing Hidden Change to Pull Requests" post][original_post] which covered pushing important stats and diffs into PRs as a comment. This avoids surprises with changes that affect binary size, manifests, and dependency trees.

 [original_post]: https://developer.squareup.com/blog/surfacing-hidden-change-to-pull-requests/

Showing dependency trees used Gradle's `dependencies` task and `diff -U 0` to display changes from the previous commit. The example in that post bumped the Kotlin version from 1.1-M03 to 1.1-M04 producing the following diff:

```diff
@@ -125,2 +125,3 @@
-|    \--- org.jetbrains.kotlin:kotlin-stdlib:1.0.4 -> 1.1-M03
-|         \--- org.jetbrains.kotlin:kotlin-runtime:1.1-M03
+|    \--- org.jetbrains.kotlin:kotlin-stdlib:1.0.4 -> 1.1-M04
+|         \--- org.jetbrains.kotlin:kotlin-runtime:1.1-M04
+|              \--- org.jetbrains:annotations:13.0
@@ -145,2 +146 @@
-+--- org.jetbrains.kotlin:kotlin-stdlib:1.1-M03
-+--- org.jetbrains.kotlin:kotlin-runtime:1.1-M03
++--- org.jetbrains.kotlin:kotlin-stdlib:1.1-M04
```

Aside from seeing the version bump reflected, there's two extra facts here we can deduce about the change:

 1. The `kotlin-runtime` dependency gained a dependency on Jetbrains' `annotations` artifact as seen in the first section of the diff.
 2. A direct dependency on `kotlin-runtime` was removed as seen in the second section of the diff. This is fine, as the first section already tells us that `kotlin-runtime` is a dependency of `kotlin-stdlib`.

These two facts are shown in the displayed diff, but there's a subtle third fact which is only implied. Because the first section is indented, we know that one of our direct dependencies has a transitive dependency on `kotlin-stdlib`. Unfortunately we have no idea which dependency is affected.

To solve this problem I wrote a tool called `dependency-tree-diff` which shows the path to a root dependency for any changes in the tree.

```diff
 +--- com.jakewharton.rxbinding:rxbinding-kotlin:1.0.0
-|    \--- org.jetbrains.kotlin:kotlin-stdlib:1.0.4 -> 1.1-M03
-|         \--- org.jetbrains.kotlin:kotlin-runtime:1.1-M03
+|    \--- org.jetbrains.kotlin:kotlin-stdlib:1.0.4 -> 1.1-M04
+|         \--- org.jetbrains.kotlin:kotlin-runtime:1.1-M04
+|              \--- org.jetbrains:annotations:13.0
-+--- org.jetbrains.kotlin:kotlin-stdlib:1.1-M03 (*)
-\--- org.jetbrains.kotlin:kotlin-runtime:1.1-M03
+\--- org.jetbrains.kotlin:kotlin-stdlib:1.1-M04 (*)
```

Our implicit third fact, which other direct dependency was affected, is now explicit in the output. Change authors can now reflect whether there may be any compatibility issues with the affected dependencies.

You can learn more about the tool and see another example [in its README][dtd].

 [dtd]: https://github.com/JakeWharton/dependency-tree-diff

### Shrinking the binary

This tool needs to be checked into our repo and run on CI. Having successfully built [adb-event-mirror](https://github.com/JakeWharton/adb-event-mirror/) using Kotlin script the first version of this tool also used Kotlin script. While it worked and was tiny, `kotlinc` is not installed on the CI machines. We rely on the Kotlin Gradle plugin to compile Kotlin, not a standalone binary.

You can locally redirect the Kotlin script cache directory to capture the compiled jar, but it still depends on the Kotlin script artifact which is large, has lots of dependencies, and are still quite dynamic. It was clear this wasn't the right path, but I filed [KT-41304](https://youtrack.jetbrains.com/issue/KT-41304) to hopefully make producing a fat `.jar` of a script easier in the future.

I switched to a classic Kotlin Gradle project and produced a fat `.jar` with the `kotlin-stdlib` dependency included. After prepending a script to [make the jar self-executing](https://skife.org/java/unix/2011/06/20/really_executable_jars.html), the binary clocked in 1699978 bytes (or \~1.62MiB). Not bad, but we can do better!

#### Removing Kotlin metadata

Listing the files in the `.jar` using `unzip -l` shows that aside from `.class`, the majority are `.kotlin_module` or `.kotlin_metadata`. These are used by the Kotlin compiler and by Kotlin's reflection and neither are needed for our binary.

We can filter these out of the binary along with `module-info.class` which is used for Java 9's module system and files in `META-INF/maven/` which propagate information about projects built with the Maven tool.

Removing all these files yields a new binary size of 1513414 bytes (\~1.44MiB), an 11% reduction in size.

#### Using R8

R8 is the code optimizer and obfuscator for Android builds. While it's normally used to optimize and obfuscate Java classfiles during conversion to the Dalvik executable format, it also supports outputting Java classfiles. In order to use it, we need to specify the entry point to the tool using ProGuard's configuration syntax.

```
-dontobfuscate
-keepattributes SourceFile, LineNumberTable

-keep class com.jakewharton.gradle.dependencies.DependencyTreeDiff {
  public static void main(java.lang.String[]);
}
```

In addition to the entrypoint, obfuscation is disabled, and we retain the source file and line number attributes so that any exceptions which occur will still be understandable.

Passing the fat `.jar` through R8 produces a new minified `.jar` which can then be made executable. The resulting binary is now just 41680 bytes (\~41KiB), a 98% reduction in size. Nice!

Since we are producing a binary and not a library, the `-allowaccessmodification` option will make optimizations like class merging and inlining more effective by allowing hidden members to be made public. Adding this produces a binary of 37630 bytes (\~37KiB).

#### Tweaking standard library usage

It is absolutely safe to stop here, but I'm bad at stopping...

Now that the binary is sufficiently small we can start looking at what code is contributing to the size. Normally I would turn to `javap` for peeking at bytecode, but since we only care about seeing API calls we can unzip the binary and open the classfiles in IntelliJ IDEA which will use the Fernflower decompiler to show roughly-equivalent Java.

The `main` method starts by reading in the arguments as files:
```kotlin
fun main(vararg args: String) {
  if (args.size == 2) {
    val old = args[0].let(::File).readText()
    val new = args[1].let(::File).readText()
```

The decompiled code looks like this:
```java
public static final void main(String... var0) {
  Intrinsics.checkNotNullParameter(var0, "args");
  if (var0.length == 2) {
    String[] var10000 = var0;
    String var3 = var0[0];
    var3 = FilesKt__FileReadWriteKt.readText$default(new File(var3), (Charset)null, 1);
    String var1 = var10000[1];
    String var8 = FilesKt__FileReadWriteKt.readText$default(new File(var1), (Charset)null, 1);
```

Peeking at `FilesKt__FileReadWriteKt` shows the unfortunate file reading code we've all written at some point in the past, and it pulls in `kotlin.ExceptionsKt`, `kotlin.jvm.internal.Intrinsics`, and `kotlin.text.Charsets`.

Switching from `java.io.File` to `java.nio.path.Path` means we can use a built-in method for reading the contents.

```diff
 fun main(vararg args: String) {
   if (args.size == 2) {
-    val old = args[0].let(::File).readText()
-    val new = args[1].let(::File).readText()
+    val old = args[0].let(Paths::get).let(Paths::readString)
+    val new = args[1].let(Paths::get).let(Paths::readString)
```

With these changes the binary drops to 30914 bytes (\~30KiB).

Another standard library usage that caught my eye is splitting the inputs by line:
```kotlin
private fun findDependencyPaths(text: String): Set<List<String>> {
  val dependencyLines = text.lineSequence()
    .dropWhile { !it.startsWith("+--- ") }
    .takeWhile { it.isNotEmpty() }
```

The decompiled Java looks somewhat like this:
```java
public static final Set findDependencyPaths(String var0) {
  String[] var10000 = new String[]{"\r\n", "\n", "\r"};
  List var1;
  DelimitedRangesSequence var2;
```

This indicates that we're using a Kotlin implementation of splitting and using its `Sequence` type. Java 11 added a `String.lines()` which returns a `Stream` that also has the `dropWhile` and `takeWhile` operators which are already in use. Unfortunately Kotlin also has a `String.lines()` extension, so we need a cast in order to use the Java 11 method.

```diff
 private fun findDependencyPaths(text: String): Set<List<String>> {
-  val dependencyLines = text.lineSequence()
+  val dependencyLines = (text as java.lang.String).lines()
     .dropWhile { !it.startsWith("+--- ") }
     .takeWhile { it.isNotEmpty() }
```

This change drops the binary to just 13643 bytes (\~13KiB) for a 99.2% reduction.

#### Remaining bloat

Kotlin being a multiplatform language means that it has its own implementation of an empty list, set, and map. When targeting the JVM, however, there's no reason to use these over the ones provided by `java.util.Collections`. I filed [KT-41333](https://youtrack.jetbrains.com/issue/KT-41333) to track this enhancement.

Dumping the contents of the final binary shows its empty collections (and related types) contribute about 50% of the remaining size:
```
$ unzip -l build/libs/dependency-tree-diff-r8.jar
Archive:  build/libs/dependency-tree-diff-r8.jar
  Length      Date    Time    Name
---------  ---------- -----   ----
       84  12-31-1969 19:00   META-INF/MANIFEST.MF
      926  12-31-1969 19:00   com/jakewharton/gradle/dependencies/DependencyTrees$findDependencyPaths$dependencyLines$1.class
      854  12-31-1969 19:00   com/jakewharton/gradle/dependencies/DependencyTrees$findDependencyPaths$dependencyLines$2.class
     6224  12-31-1969 19:00   com/jakewharton/gradle/dependencies/DependencyTreeDiff.class
      604  12-31-1969 19:00   com/jakewharton/gradle/dependencies/Node.class
     2534  12-31-1969 19:00   kotlin/collections/CollectionsKt__CollectionsKt.class
     1120  12-31-1969 19:00   kotlin/collections/EmptyIterator.class
     3227  12-31-1969 19:00   kotlin/collections/EmptyList.class
     2023  12-31-1969 19:00   kotlin/collections/EmptySet.class
     1958  12-31-1969 19:00   kotlin/jvm/internal/CollectionToArray.class
     1638  12-31-1969 19:00   kotlin/jvm/internal/Intrinsics.class
---------                     -------
    21192                     11 files
```

In addition to those extra types, the bytecode contains a bunch of extra null checks. For example, the decompiled bytecode for `findDependencyPaths` from the last section actually looks like this:
```java
public static final Set findDependencyPaths(String var0) {
  Intrinsics.checkNotNullParameter(var0, "$this$lines");
  Intrinsics.checkNotNullParameter(var0, "$this$lineSequence");
  String[] var10000 = new String[]{"\r\n", "\n", "\r"};
  Intrinsics.checkNotNullParameter(var0, "$this$splitToSequence");
  Intrinsics.checkNotNullParameter(var10000, "delimiters");
  Intrinsics.checkNotNullParameter(var10000, "$this$asList");
```

These `Intrinsics` calls enforce the nullability invariants of the type system on function parameters, but after inlining all but the first one are redundant. Duplicate calls like this appear all over the code. This is [an R8 bug](https://issuetracker.google.com/issues/139276374) caused by Kotlin renaming these intrinsic methods and R8 not updating to properly track that change.

With these two issues fixed, it's likely the binary will drop into single-digit KiBs producing a high-99 percent reduction from the original fat `.jar`.

---

If you are building a JVM binary or a JVM library which shades dependencies make sure you use a tool like R8 or ProGuard to remove unused code paths, or use a Graal native image to produce a minimal native binary. This tool was kept as Java bytecode so that a single `.jar` can be used on multiple platforms.

The full source code and build setup for `dependency-tree-diff` is available [on GitHub][dtd].
