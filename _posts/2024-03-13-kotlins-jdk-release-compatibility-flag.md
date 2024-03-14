---
title: "Kotlin's JDK release compatibility flag"
layout: post

categories: post
tags:
- Kotlin
---

Yesterday, our Android app crashed with a weird `NoSuchMethodError`.

```
java.lang.NoSuchMethodError: No interface method removeFirst()Ljava/lang/Object; in class Ljava/util/List; or its super classes (declaration of 'java.util.List' appears in /apex/com.android.art/javalib/core-oj.jar)
    at app.cash.redwood.lazylayout.widget.LazyListUpdateProcessor.onEndChanges(SourceFile:165)
    at app.cash.redwood.lazylayout.view.ViewLazyList.onEndChanges(SourceFile:210)
    at app.cash.redwood.protocol.widget.ProtocolBridge.sendChanges(SourceFile:125)
    at app.cash.redwood.treehouse.ViewContentCodeBinding.receiveChangesOnUiDispatcher(SourceFile:419)
    at app.cash.redwood.treehouse.ViewContentCodeBinding$sendChanges$1.invokeSuspend(SourceFile:383)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(SourceFile:33)
    at kotlinx.coroutines.DispatchedTask.run(SourceFile:104)
    at android.os.Handler.handleCallback(Handler.java:938)
    at android.os.Handler.dispatchMessage(Handler.java:99)
    at android.os.Looper.loop(Looper.java:250)
    at android.app.ActivityThread.main(ActivityThread.java:7868)
```

[The offending code](https://github.com/cashapp/redwood/blob/2db41653e3887387b8c8468cb3f01d0c326eb39d/redwood-lazylayout-widget/src/commonMain/kotlin/app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor.kt#L165) is written in Kotlin, and looks like this:

![val widget = edit.widgets.removeFirst()](/static/post-image/removeFirst.png)

The IDE showing an italicized blue style for `removeFirst` means [it's a Kotlin extension function](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/remove-first.html) which compiles down to a static helper in the bytecode.
However, the exception clearly indicates we are calling a member function on `List` directly. What gives?

In JDK 21, as part of the [sequenced collection](https://openjdk.org/jeps/431) effort, the `List` interface [added `removeFirst()` and `removeLast()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html#removeFirst()) methods. According to the [Kotlin docs on extension functions](https://kotlinlang.org/docs/extensions.html#extensions-are-resolved-statically):

> If a class has a member function, and an extension function is defined which has the same receiver type, the same name, and is applicable to given arguments, the **member always wins**.

When we bumped our build JDK to 21, the new member became available and accidentally took precedence. Oops!

But wait, we set our Kotlin `jvmTarget` to 1.8 in order to be backwards compatible. Is that not enough?

```kotlin
val javaVersion = JavaVersion.VERSION_1_8
tasks.withType(KotlinJvmCompile::class.java).configureEach {
  it.kotlinOptions.jvmTarget = javaVersion.toString()
}
// Kotlin requires the Java compatibility matches despite have no sources.
tasks.withType(JavaCompile::class.java).configureEach {
  it.sourceCompatibility = javaVersion.toString()
  it.targetCompatibility = javaVersion.toString()
}
```

This setting controls the Java bytecode version that the Kotlin compiler emits for JVM and Android targets.
We can confirm this is being honored by inspecting the offending class with `javap`.

```
$ javap -v redwood-lazylayout-widget/build/classes/kotlin/jvm/main/app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor.class | head -8
Classfile redwood-lazylayout-widget/build/classes/kotlin/jvm/main/app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor.class
  Last modified Mar 13, 2024; size 16001 bytes
  SHA-256 checksum dbeed7bba16c023a98fa356bab7cada7abe686d5da7d4824781790de577e94a2
  Compiled from "LazyListUpdateProcessor.kt"
public abstract class app.cash.redwood.lazylayout.widget.LazyListUpdateProcessor<V extends java.lang.Object, W extends java.lang.Object> extends java.lang.Object
  minor version: 0
  major version: 52
  flags: (0x0421) ACC_PUBLIC, ACC_SUPER, ACC_ABSTRACT
```

The classfile's major version is listed at 52, which we can reverse lookup using [a version table](https://javaalmanac.io/bytecode/versions/) and see that this corresponds to Java 8. So we know that's working, at least.

Further down the output, however, the offending reference can also be seen.

```
405: checkcast     #101        // class app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor$Edit$Insert
408: invokevirtual #107        // Method app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor$Edit$Insert.getWidgets:()Ljava/util/List;
411: invokeinterface #151,  1  // InterfaceMethod java/util/List.removeFirst:()Ljava/lang/Object;
416: checkcast     #121        // class app/cash/redwood/widget/Widget
419: astore        6
```

The reason this can happen is that the Java bytecode version is independent from the set of JDK APIs that you can reference.
This is not unique to Kotlin.
`javac`'s `-target` flag behaves the same way, as you can see [in this Godbolt sample](https://java.godbolt.org/z/rKWv4K9jG).

This can be fixed with `javac` by specifying the `-bootclasspath` argument and pointing at the `rt.jar` from a JDK 8 install.
The JDK 21 compiler emits a warning telling us to do this when target _any_ bytecode version other than the default:

> warning: [options] bootstrap class path not set in conjunction with -source 8

Starting with Java 9, `javac` has a new flag, `--release`, which sets the `-source`, `-target`, and `-bootclasspath` flags automatically to the same version (and doesn't require having the old JDK available).
If we switch the Java sample to use `--release` [it now fails to compile](https://java.godbolt.org/z/bP6baz9GT)!

Kotlin 1.7 brought a new flag to `kotlinc` (Kotlin's JVM compiler) which acts just like `javac`'s `--release`: `-Xjdk-release`.
As far as I can tell, this has flown massively under the radar but is an essential piece to the cross-compilation toolkit.

Let's configure our JVM target's compilation to use this flag and see what changes.

```kotlin
 kotlin.targets.withType(KotlinJvmTarget::class.java) { target ->
  target.compilations.configureEach {
    it.kotlinOptions.freeCompilerArgs += listOf(
      "-Xjdk-release=$javaVersion",
    )
  }
}
```

After compiling and dumping the Java bytecode there is a welcome change.

```diff
 405: checkcast     #101        // class app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor$Edit$Insert
 408: invokevirtual #107        // Method app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor$Edit$Insert.getWidgets:()Ljava/util/List;
-411: invokeinterface #151,  1  // InterfaceMethod java/util/List.removeFirst:()Ljava/lang/Object;
+411: invokestatic  #152        // Method kotlin/collections/CollectionsKt.removeFirst:(Ljava/util/List;)Ljava/lang/Object;
 414: checkcast     #121        // class app/cash/redwood/widget/Widget
 417: astore        6
```

With the JDK API unavailable, the `removeFirst` extension now resolves to the static method in the Kotlin standard library.

The `-Xjdk-release` flag is useful for the Kotlin JVM plugin or the JVM targets of the Kotlin multiplatform plugin to ensure compatibility with your target minimum JVM. Users of the Kotlin Android plugin or the Android targets of the Kotlin multiplatform plugin do not need to do this, as the use of the `android.jar` as the boot classpath limits the `java.*` APIs to those of your `compileSdk` (and Android Lint ensures you don't use anything newer than your `minSdk`).

Unforunately there's no Gradle DSL for this yet, but [KT-49746](https://youtrack.jetbrains.com/issue/KT-49746/Support-Xjdk-release-in-gradle-toolchain#focus=Comments-27-8935065.0-0) tracks that.

If you use Gradle toolchains you don't have this problem. This is because you actually use the ancient JDK and JVM of your minimum target to run `javac` and `kotlinc` and miss out on a decade's worth of compiler improvements. Gradle toolchains are rarely a good idea. But that's a topic for next week…