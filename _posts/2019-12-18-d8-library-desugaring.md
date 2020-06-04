---
title: 'D8 Library Desugaring'
layout: post

categories: post
tags:
- Android
- D8
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

So far in this series the coverage of D8 has been about desugaring of [Java 8 language features](/androids-java-8-support/), working around [vendor- and version-specific bugs](/avoiding-vendor-and-version-specific-vm-bugs/) in the platform, and performing [method-local optimization](/d8-optimizations/). In this post we'll cover an upcoming feature of D8 called "core library desugaring" which makes newer APIs available on older versions of Android.

Library desugaring of Java 8 APIs such as streams, optional, and the new time APIs was announced at the developer keynote of Google I/O 2019 and delivered at Android DevSummit 2019 with the first canary build of Android Studio 4.0. This will allow developers to use these features introduced in API 24 and 26 on every version their app targets. No more backport libraries and duplicated APIs!

This is also a boon to the Java library ecosystem. Many libraries have long-since moved on to Java 8 but are unable to use newer APIs in order to maintain Android compatibility. While every new API is not available, D8 desugaring should allow these libraries to use the APIs which are most desired.

### Not a new feature

Despite the recent fanfare, desugaring APIs is not a actually a new feature of D8. Since it became a usable alternative to `dx`, D8 has desugared calls to the API 19 `Objects.requireNonNull` method. But, why that one method?

Certain code patterns will cause the Java compiler to synthesize an explicit null check.

```java
class Counter { 
  final int count = 0; 
} 
class Main { 
  void doSomething(Counter counter) { 
    int count = counter.count;
  } 
}
```

When compiled with JDK 8, the Java bytecode of the `doSomething` method contains a call to `getClass()` whose return value is then thrown away.

```
void doSomething(Counter);
  Code:
     0: aload_1
     1: invokevirtual #2   // Method java/lang/Object.getClass:()Ljava/lang/Class;
     4: pop
     5: iconst_0
     6: istore_2
     ⋮
```

The zero value of `count` gets inlined into `doSomething` at bytecode index 5. As a result, if you were to pass `null` as the `Counter` the program would not throw a null-pointer exception. By including a call to `getClass()` on the `Counter`, the correct program behavior is maintained.

If you recompile this snippet with JDK 9, the bytecode changes.

```diff
 void doSomething(Counter);
   Code:
      0: aload_1
-     1: invokevirtual #2   // Method java/lang/Object.getClass:()Ljava/lang/Class;
+     1: invokestatic  #2   // Method java/util/Objects.requireNonNull:(Ljava/lang/Object;)Ljava/lang/Object;
      4: pop
      5: iconst_0
      6: istore_2
      ⋮
```

[JDK-8074306](https://bugs.openjdk.java.net/browse/JDK-8074306) changed the behavior of the Java compiler in this scenario to produce better exceptions. But the Android toolchain has historically not worked correctly with JDK 9 (and newer), so you may be wondering how these calls came to be.

The primary source was [Google's error-prone](https://errorprone.info/) compiler and static analyzer which works with JDK 8 but is built on top of the JDK 9 compiler. While error-prone resolved [the issue](https://github.com/google/error-prone/issues/375) by introducing an off-by-default flag, Retrolambda [added desugaring](https://github.com/luontola/retrolambda/issues/75) for the API which basically required that D8 do the same.

Running D8 on the Java bytecode (with a minimum API level of less than 19) desugars the call back into a `getClass()` invocation.

```
[00016c] Main.doSomething:(LCounter;)V
0000: invoke-virtual {v1}, Ljava/lang/Object;.getClass:()Ljava/lang/Class;
 ⋮
```

`Objects.requireNonNull` was the only API that D8 was able to desugar for a long time, and it did so using a simple rewrite. But soon its desugaring capabilities would have to expand in order to actually backport functionality.

### Kotlin's Java 8

Unlike the Java compiler, the Kotlin compiler emits references to many APIs when generating bytecode for its language features. A `data class` is an example of the compiler generating a lot of bytecode on your behalf.

```kotlin
data class Duration(val amount: Long, val unit: TimeUnit)
```

In Kotlin 1.1.60, when targeting Java 8 bytecode, the `hashCode` method of a `data class` changed to start referencing some Java 8 APIs.

```
public int hashCode();
  Code:
     0: aload_0
     1: getfield      #10   // Field amount:J
     4: invokestatic  #71   // Method java/lang/Long.hashCode:(J)I
     ⋮
```

The compiler is free to call `Long.hashCode` because we told it that we were targeting Java 8. This is a new static method which has been added to the `Long` class.

Normally this would not be a problem for Android since the Kotlin compiler targets Java 6 by default. Unfortunately, the community push to target Java 8 for its language features interacted poorly with a decision to have the Kotlin compiler respect the specified target of your Java compiler in Kotlin 1.3. As a result, Android developers started seeing `NoSuchMethodError`s for these `hashCode` calls because they were only available in API 24 and newer.

While the behavior of the Kotlin compiler was reverted for Android projects, there still was a potential for libraries consumed by Android projects to be targeting Java 8 and to reference these methods. The D8 team decided to step in and mitigate this problem by desugaring the `hashCode` APIs.

Running D8 on the Java bytecode (with a minimum API level of less than 24) shows the desugaring.

```
[0003e4] Duration.hashCode:()I
0000: iget-wide v0, v2, LDuration;.amount:J
0002: invoke-static {v0, v1}, L$r8$backportedMethods$utility$Long$1$hashCode;.hashCode:(J)I
 ⋮
```

I'm not sure how you expected `Long.hashCode` to be desugared, but I'm guessing it wasn't to a class named `$r8$backportedMethods$utility$Long$1$hashCode`! Unlike `Objects.requireNonNull` which was rewritten to `getClass()` to produce the same observable behavior, `Long.hashCode` has an implementation which cannot be replicated with a trivial rewrite.

### Backporting methods

Inside of the D8 project, there are template implementations of each API that it can backport.

```java
public final class LongMethods {
  public static int hashCode(long l) {
    return (int) (l ^ (l >>> 32));
  }
}
```

The code for these APIs are either written from the Javadoc specification of the method or adapted from libraries like [Google Guava](https://github.com/google/guava). When D8 is built, these templates are automatically converted into abstract representations of the method body.

```java
public static CfCode LongMethods_hashCode() {
  return new CfCode(
      /* maxStack = */ 5,
      /* maxLocals = */ 2,
      ImmutableList.of(
          new CfLoad(ValueType.LONG, 0),
          new CfLoad(ValueType.LONG, 0),
          new CfConstNumber(32, ValueType.INT),
          new CfLogicalBinop(CfLogicalBinop.Opcode.Ushr, NumericType.LONG),
          new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.LONG),
          new CfNumberConversion(NumericType.LONG, NumericType.INT),
          new CfReturn(ValueType.INT)));
}
```

When D8 is compiling bytecode and first encounters a call to `Long.hashCode`, it generates a class on-the-fly with a `hashCode` method whose body created by calling that factory method. Each `Long.hashCode` call is then rewritten to point at this newly-generated class.

```
Class #0            -
  Class descriptor  : 'L$r8$backportedMethods$utility$Long$1$hashCode;'
  Access flags      : 0x1401 (PUBLIC ABSTRACT SYNTHETIC)
  Superclass        : 'Ljava/lang/Object;'
  Direct methods    -
    #0
      name          : 'hashCode'
      type          : '(J)I'
      access        : 0x1009 (PUBLIC STATIC SYNTHETIC)
00044c:                   |[00044c] $r8$backportedMethods$utility$Long$1$hashCode.hashCode:(J)I
00045c: 1300 2000         |0000: const/16 v0, #int 32
000460: a500 0200         |0002: ushr-long v0, v2, v0
000464: c202              |0004: xor-long/2addr v2, v0
000466: 8423              |0005: long-to-int v3, v2
000468: 0f03              |0006: return v3
```

This process allows the Java 8-targeting `data class` work on versions of Android prior to API 24. If you look closely, you can probably map each Dalvik bytecode back to the abstract representation and then back to the template source code.

It may sound overkill to generate one class per method but this ensures that there is only one implementation of each API that requires backporting. When using R8, these synthesized classes also participate in optimizations such as method inlining and class merging which ultimately reduce their impact.

D8 can desugar 98 individual APIs from Java 7 and Java 8 which were added to existing types. But why stop there?

Because of how easy it is to add these templates, D8 can also desugar an additional 58 individual APIs from Java 9, 10, and 11 on existing types. This potentially allows Java libraries to target even newer versions of Java and still be used on Android.

A full list of the APIs which are available to desugar can be found [here](/static/files/d8_api_desugar_list.txt). Most of these are already available in AGP 3.6.0.

### Backporting Types

Types like `Optional`, `Function`, `Stream`, and `LocalDateTime` are just some of those added in Java 8 which came to Android in API 24 and API 26. Backporting these to work on older API levels is more complicated than what it took to backport a single method for a few reasons.

```java
class Main {
  public static void main(String... args) {
    System.out.println(LocalDateTime.now());
  }
}
```

`LocalDateTime` was introduced in Android API 26 and an app whose minimum API level is 26 or higher can call into the class directly.

```
[000240] Main.main:([Ljava/lang/String;)V
0000: sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;
0002: invoke-static {}, Ljava/time/LocalDateTime;.now:()Ljava/time/LocalDateTime;
0005: move-result-object v0
0006: invoke-virtual {v1, v0}, Ljava/io/PrintStream;.println:(Ljava/lang/Object;)V
0009: return-void
```

To enable the use of these types when the minimum API is below 26, the Android Gradle plugin (4.0 or newer) requires that you enable "core library desugaring" in its DSL.

```groovy
android {
  compileOptions {
    coreLibraryDesugaringEnabled true
  }
}
```

Recompiling will change the bytecode to reference the backport types.

```diff
 [000240] Main.main:([Ljava/lang/String;)V
 0000: sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;
-0002: invoke-static {}, Ljava/time/LocalDateTime;.now:()Ljava/time/LocalDateTime;
+0002: invoke-static {}, Lj$/time/LocalDateTime;.now:()Lj$/time/LocalDateTime;
 0005: move-result-object v0
 0006: invoke-virtual {v1, v0}, Ljava/io/PrintStream;.println:(Ljava/lang/Object;)V
 0009: return-void
```

The call to `java.time.LocalDateTime` was simply rewritten to `j$.time.LocalDateTime`, but the rest of the APK has changed dramatically.

Using the [diffuse tool](https://github.com/JakeWharton/diffuse/) we can get a high-level view of the changes.

```
$ diffuse diff app-min-26.apk app-min-25.apk
OLD: app-min-26.apk (signature: V2)
NEW: app-min-25.apk (signature: V2)

          │          compressed          │         uncompressed
          ├───────┬──────────┬───────────┼─────────┬──────────┬─────────
 APK      │ old   │ new      │ diff      │ old     │ new      │ diff
──────────┼───────┼──────────┼───────────┼─────────┼──────────┼─────────
      dex │ 680 B │   44 KiB │ +43.4 KiB │   944 B │ 90.9 KiB │ +90 KiB
     arsc │ 524 B │    520 B │      -4 B │   384 B │    384 B │     0 B
 manifest │ 603 B │    603 B │       0 B │ 1.2 KiB │  1.2 KiB │     0 B
    other │ 229 B │    229 B │       0 B │    95 B │     95 B │     0 B
──────────┼───────┼──────────┼───────────┼─────────┼──────────┼─────────
    total │ 2 KiB │ 45.4 KiB │ +43.4 KiB │ 2.6 KiB │ 92.6 KiB │ +90 KiB


         │        raw        │           unique
         ├─────┬──────┬──────┼─────┬─────┬────────────────
 DEX     │ old │ new  │ diff │ old │ new │ diff
─────────┼─────┼──────┼──────┼─────┼─────┼────────────────
   count │   1 │    2 │   +1 │     │     │
 strings │  16 │ 1005 │ +989 │  16 │ 996 │ +980 (+983 -3)
   types │   7 │  175 │ +168 │   7 │ 170 │ +163 (+164 -1)
 classes │   1 │   88 │  +87 │   1 │  88 │  +87 (+87 -0)
 methods │   5 │  728 │ +723 │   5 │ 727 │ +722 (+724 -2)
  fields │   1 │  255 │ +254 │   1 │ 255 │ +254 (+254 -0)
```

There's two important things that this summary tells us:
1. Our APK size grew by 43.4KB which is entirely attributed to dex files. Looking at the dex changes there are a bunch of new classes, methods, and fields.
2. The number of dex files increased from one to two despite the number of total methods being nowhere close to the limit. These were release builds so we should be getting the minimum number of dex files.

Let's break each of these down.

#### APK size impact

Historically, in order to use the `java.time` APIs in an app with a minimum supported API level below 26 you would need to use the [ThreeTenBP](https://github.com/ThreeTen/threetenbp/) library (or [ThreeTenABP](https://github.com/JakeWharton/ThreeTenABP/)). This is a standalone repackaging of the `java.time` APIs in the `org.threeten.bp` package which requires you to update all your imports.

D8 is basically performing that same operation but at the bytecode level. It rewrites your code from calling `java.time` to `j$.time` as seen in the bytecode diff above. To accompany that rewrite, an implementation needs to be bundled into the application. That is the cause of the large APK size change.

In this example the release APK is minified using R8 which also minifies the backport code. If minification is disabled, the increase in dex size jumps up to 180KB, 206 classes, 3272 methods, and 713 fields.

#### Second Dex

A release build will cause D8 or R8 to produce the minimum number of dex files required, and that's actually still the case here. D8 and R8 are responsible for producing the dex files for user code and your declared libraries. This means that only the `Main` type will be present in the first dex which we can confirm by dumping its members.

```
$ unzip app-min-25.apk classes.dex && \
    diffuse members --dex --declared classes.dex
com.example.Main <init>()
com.example.Main main(String[])
```

As D8 or R8 are compiling your code and performing rewrites to the `j$` packages, they record the types and APIs that are being rewritten. This produces a set of shrinker rules that are specific to the backported types. Currently (i.e., for AGP 4.0.0-alpha06) these rules are located at `build/intermediates/desugar_lib_project_keep_rules/release/out/4` and for this example contains only the `LocalDateTime.now()` reference.

```
-keep class j$.time.LocalDateTime {
    j$.time.LocalDateTime now();
}
```

All of the available backported types have been pre-compiled from OpenJDK source to a dex file as part of Google's [desugar_jdk_libs](https://github.com/google/desugar_jdk_libs) project. That dex file is downloaded from Google's maven repo and then fed into a tool called L8 along with those generated keep rules. L8 shrinks this dex file in isolation using the provided rules to produce the final, second dex file.

Dumping the L8-minified second dex file shows a set of types and APIs that have been entirely obfuscated except for the `LocalDateTime.now()` API that the application is referencing.

```
$ unzip app-min-25.apk classes2.dex && \
    diffuse members --dex classes2.dex | grep -C 6 'LocalDateTime.now'
j$.time.LocalDateTime c(s) → long
j$.time.LocalDateTime compareTo(Object) → int
j$.time.LocalDateTime d() → h
j$.time.LocalDateTime d(s) → x
j$.time.LocalDateTime equals(Object) → boolean
j$.time.LocalDateTime hashCode() → int
j$.time.LocalDateTime now() → LocalDateTime
j$.time.LocalDateTime toString() → String
j$.time.a <init>(k)
j$.time.a a() → k
j$.time.a a: k
j$.time.a b() → f
j$.time.a c() → long
```

L8 is purpose-built for processing this special dex file. Previously in this series, R8 [was introduced](/r8-optimization-staticization/) as...

> ...a version of D8 that also performs optimization. It’s not a separate tool or codebase, just the same tool operating in a more advanced mode.
 
Well L8 is a version of R8 that optimizes the JDK desugar dex file. It's not a separate tool or codebase, just the same tool operating in a more advanced mode.

It may not be clear why the explicit extra dex is needed rather than consuming the desugared JDK types like any other library and allowing them to be processed normally by R8. First of all, Google probably doesn't want me talking about it which should itself be somewhat of an indication why the extra ceremony is needed. For more information you can consult the OpenJDK source code license, specifically the very end. Sorry if that's not enough information, but I suspect that's all I'm allowed to say.

By virtue of always requiring at least a second dex, you either need have a minimum supported API of 21 or use [legacy multidex](https://developer.android.com/studio/build/multidex#mdex-pre-l). Most applications should choose the former, or use this feature as yet-another justification to potentially increase your minimum to 21.

#### Backporting methods on backported types

In addition to backporting methods on the types that have been around since API 1 like `Long`, D8 and R8 will also backport newer methods on these backportable types like `Optional`. These use the same template mechanism as detailed earlier, but will only be available when your minimum API level is high enough to access the target type or you have core library desugaring enabled.

For `Stream` and the four different optional types, D8 and R8 will backport 18 methods from Java 9, 10, and 11. The full list of those APIs can be found [here](/static/files/d8_api_desugar_list_on_desugared_types.txt).

### Developer Story

As a developer wanting to write code using these APIs, how do you know which ones are available for backport? Currently there's not a great way to know about them all.

To start with, once you enable `coreLibraryDesugaring` the IDE and Lint will start allowing you to use the new types and new APIs when supported. Running Lint on this example will produce no errors despite the minimum supported API being below 26 which `LocalDateTime` would otherwise require. When library desugaring is disabled, though, the `NewApi` check fails as it normally would. 

```
Main.java:7: Error: Call requires API level 26 (current min is 25): java.time.LocalDateTime#now [NewApi]
    System.out.println(LocalDateTime.now());
                                     ~~~
```

This ensures you don't errantly use an unsupported type or API, but it does not help for discoverability.

For now the best list of backported types is in the [Android Studio 4.0 feature list](https://developer.android.com/studio/releases/gradle-plugin#j8-library-desugaring) and the best list of backported APIs on existing types are the two lists in this post ([1](/static/files/d8_api_desugar_list.txt), [2](/static/files/d8_api_desugar_list_on_desugared_types.txt)). Hopefully in the future these will be more discoverable, though.

---

The backporting of individual APIs has been improving since D8 and R8's inception. With core library desugaring now becoming available in Android Gradle plugin 4.0 alphas, applications have access to the foundational types from Java 8 even when their minimum supported API level is lower than when those types were introduced. It also means that Java libraries can start to leverage these types while still maintaining compatibility with Android.

It's important to remember that even with all this shiny new API availability, the JDK and Java APIs are continuing to improve along their six-month release cadence. While D8 and R8 can help bridge the gap by desugaring some of those APIs from Java 9, 10, and 11 even before they land in Android, pressure must be maintained to actually ship these APIs in the Android framework.
