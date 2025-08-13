---
title: 'D8 Optimization: Assertions'
layout: post

categories: post
tags:
- Android
- D8
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

The `assert` keyword is quirky Java language syntax used for testing invariants. That is: things you expect to **always** be true.

Its syntax has two forms:
```java
assert <bool-expression>;
assert <bool-expression> : <expression>;
```
The first expression will only be evaluated at runtime if the `-ea` (enable assertions) flag is set on the JVM. The second expression, if present, is used as the argument to the `AssertionError` constructor that's thrown if the first expression returns false.

As an Android developer you might not be too familiar with `assert`. This is because every Android app runs on a VM which is forked from a shared "zygote" process which has assertions disabled. Thus, even if you put an `assert` in your code, there is no way to actually enable it.

So why bother talking about it? Well it turns out they're about to become useful on Android for the first time!

### Today's behavior

`assert` statements guard things which must _always_ be true in order for your program to execute correctly. Let's write one.
```java
class IdGenerator {
  private int id = 0;

  int next() {
    assert Thread.currentThread() == Looper.getMainLooper().getThread();
    return id++;
  }
}
```

This class creates unique IDs and guarantees they're unique by only allowing calls from the main thread. If this class was called concurrently from multiple threads you might see duplicate values. Sure it's a little contrived and there's things like `@MainThread` which is checked by Lint but we're focusing on `assert` so roll with it.

The [Null Data Flow Analysis][ndfa] post introduced the SSA form that R8 uses to eliminate branches of code which it can prove will never be executed. The SSA for the `next()` method when parsed from Java bytecode looks _very_ roughly like this:

 [ndfa]: /r8-optimization-null-data-flow-analysis-part-1/

<!--
digraph G {
  rankdir="RL";
  "if thread == main thread" -> "if assertions enabled"
  "throw AssertionError" -> "if thread == main thread"
  "int value = id" -> "if thread == main thread"
  "int value = id" -> "if assertions enabled"
  "id = value + 1" -> "int value = id"
  "return value" -> "id = value + 1"
}
-->
<a href="/static/post-image/assert-1.png"><img src="/static/post-image/assert-1.png"/></a>

D8 knows that Android does not support Java assertions. It will remove the check and replace it with `false` allowing dead-code elimination to occur. This propagates to the nodes which can only be taken when it returns true.

<!--
digraph G {
  rankdir="RL";
  
  "if assertions enabled" [style=dotted]
  "if thread == main thread"[style=dotted]
  "throw AssertionError" [style=dotted]
  
  "if thread == main thread" -> "if assertions enabled" [style=dotted]
  "throw AssertionError" -> "if thread == main thread" [style=dotted]
  "int value = id" -> "if thread == main thread" [style=dotted]
  "int value = id" -> "if assertions enabled" [style=dotted]
  "id = value + 1" -> "int value = id"
  "return value" -> "id = value + 1"
}
-->
<a href="/static/post-image/assert-2.png"><img src="/static/post-image/assert-2.png"/></a>

As a result, the boolean expression and optional message expression are entirely eliminated from the bytecode. Only the field read, field increment, and return remain.

<!--
digraph G {
  rankdir="RL";
  "id = value + 1" -> "int value = id"
  "return value" -> "id = value + 1"
}
-->
<a href="/static/post-image/assert-3.png"><img src="/static/post-image/assert-3.png" style="max-height: 43px"/></a>

We can confirm this by sending the Java source through the compilation pipeline:
```bash
$ javac -bootclasspath $ANDROID_HOME/platforms/android-29/android.jar IdGenerator.java
$ java -jar $R8_HOME/build/libs/d8.jar \
      --lib $ANDROID_HOME/platforms/android-29/android.jar \
      --output . \
      IdGenerator.class
$ dexdump -d classes.dex
 ⋮
[00011c] IdGenerator.next:()I
0000: iget v0, v2, LIdGenerator;.id:I
0002: add-int/lit8 v1, v0, #int 1
0004: iput v1, v2, LIdGenerator;.id:I
0006: return v0
 ⋮
```

Eliminating a runtime check which always returns false is an easy win, but the SSA form means that we eliminate the bytecode for both expressions of the `assert` statement including any intermediate values they rely on.

### Tomorrow's behavior

The version of D8 in AGP 4.1 slightly changes the thinking around Java `assert`. Instead of assuming that the runtime check will always fail at runtime (which it _still_ does), it computes the check at compile-time based on whether your build is debuggable.

In practice, this means that any debug variant will replace the assertions-enabled check at compile-time with `true`.

<!--
digraph G {
  rankdir="RL";
  
  "if assertions enabled" [style=dotted]
  
  "if thread == main thread" -> "if assertions enabled" [style=dotted]
  "throw AssertionError" -> "if thread == main thread"
  "int value = id" -> "if thread == main thread"
  "int value = id" -> "if assertions enabled" [style=dotted]
  "id = value + 1" -> "int value = id"
  "return value" -> "id = value + 1"
}
-->
<a href="/static/post-image/assert-4.png"><img src="/static/post-image/assert-4.png"/></a>

This eliminates the enabled check but retains the invariant check.

<!--
digraph G {
  rankdir="RL";
  
  "throw AssertionError" -> "if thread == main thread"
  "int value = id" -> "if thread == main thread"
  "id = value + 1" -> "int value = id"
  "return value" -> "id = value + 1"
}
-->
<a href="/static/post-image/assert-5.png"><img src="/static/post-image/assert-5.png" style="max-height:97px"/></a>

Sending `IdGenerator` through D8 with the `--force-enable-assertions` flag that AGP automatically adds for debug variants shows this in Dalvik bytecode:
```diff
 $ java -jar $R8_HOME/r8/build/libs/d8.jar \
       --lib $ANDROID_HOME/platforms/android-29/android.jar \
+      --force-enable-assertions \
       --output . \
       IdGenerator.class
 $ dexdump -d classes.dex
  ⋮
 [000190] IdGenerator.next:()I
+0000: invoke-static {}, Ljava/lang/Thread;.currentThread:()Ljava/lang/Thread;
+0003: move-result-object v0
+0004: invoke-static {}, Landroid/os/Looper;.getMainLooper:()Landroid/os/Looper;
+0007: move-result-object v1
+0008: invoke-virtual {v1}, Landroid/os/Looper;.getThread:()Ljava/lang/Thread;
+000b: move-result-object v1
+000c: if-ne v0, v1, 0015
 000e: iget v0, v2, LIdGenerator;.id:I
 0010: add-int/lit8 v1, v0, #int 1
 0012: iput v1, v2, LIdGenerator;.id:I
 0014: return v0
+0015: new-instance v0, Ljava/lang/AssertionError;
+0017: invoke-direct {v0, v1}, Ljava/lang/AssertionError;.<init>:()V
+001a: throw v0
  ⋮
```

Our debug build still tests the invariant at runtime but the release build completely eliminates the check. This behavior is now similar to the JVM where unit tests turn on the `-ea` flag whereas production does not.

(If you're wondering why the code which throws the exception was moved to the bottom of the method check out the [Optimizing Bytecode by Manipulating Source Code](/optimizing-bytecode-by-manipulating-source-code/) post.)

---

This feature is already available in the latest AGP 4.1 alphas. The nature of invariants are such that they should never fail unless you're already doing something very wrong. By checking them in debug builds we have only confidence to gain in the correctness of our libraries and application code when running on Android.

Kotlin's `assert()` function currently has a subtle behavior difference compared to Java's `assert` keyword. For more information see Jesse Wilson's [Kotlin’s Assert Is Not Like Java’s Assert](https://publicobject.com/2019/11/18/kotlins-assert-is-not-like-javas-assert/) post. D8 currently does not recognize Kotlin's `assert()` to apply the optimization in this post, but the [original D8 feature request](https://issuetracker.google.com/issues/139898386) remains open for this very reason.

Unlike some of the R8 optimizations covered in recent posts, this optimization is localized to the body of a single method which is why it can also be performed by D8. Check out the [D8 Optimizations](/d8-optimizations/) post for more optimizations which apply in both D8 and R8.

And stay tuned for more D8 and R8 optimization posts coming soon!
