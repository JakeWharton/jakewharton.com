---
title: 'R8 Optimization: Null Data Flow Analysis (Part 2)'
layout: post

categories: post
tags:
- Android
- Kotlin
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

[Part 1 of this post](/r8-optimization-null-data-flow-analysis-part-1/) demonstrated R8's ability to eliminate null checks after method inlining. This was accomplished by virtue of nullability information being present in R8's (and D8's) intermediate representation (IR). When the arguments flowing into a method were always non-null or always null, the now-inlined null check can be computed at compile-time.

Examples in the last two posts have mostly used Kotlin. To improve readability of their bytecode, I've been removing a section of it. The last post started with an example of a `coalesce` function being called from a `main` function.

```kotlin
fun <T : Any> coalesce(a: T?, b: T?): T? = a ?: b

fun main(args: Array<String>) {
 println(coalesce("one", "two"))
 println(coalesce(null, "two"))
}
```

Multiple versions of the compiled bytecode of this function were shown in that post and they all started with `sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;`. This is the bytecode looking up the static `System.out` field on which it can eventually invoke the `println` method.

If you compile, dex, and dump the bytecode of the Kotlin source above, however, the first bytecodes are something quite different.

```
$ kotlinc *.kt

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class kotlin-stdlib-1.3.11.jar

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[00023c] NullsKt.main:([Ljava/lang/String;)V
0000: const-string v0, "args"
0002: invoke-static {v2, v0}, Lkotlin/jvm/internal/Intrinsics;.checkParameterIsNotNull:(Ljava/lang/Object;Ljava/lang/String;)V
0005: sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;
…
```

Instead of bytecodes representing the body of the function we wrote, the Kotlin compiler first emits a call to the standard library's `Intrinstrics.checkParameterIsNotNull` function. This call is a behind-the-scenes runtime validation of a compile-time constraint.

Kotlin's type system models the nullability of references. By making the parameter of my `main` function `Array<String>`, I have declared it as never being null. But since this is a public API that anyone in any language can invoke, nothing prevents a non-Kotlin caller from passing null. In order to validate the non-null constraint and protect its users, the Kotlin compiler inserts defensive checks for non-null parameters in every public API function.

_(Note: While there is a way to disable generation of these defensive checks, it's not wise to do.)_

Let's take a look at how using R8 on the same source file changes the output.

```
$ cat rules.txt
-keepclasseswithmembers class * {
  public static void main(java.lang.String[]);
}
-dontobfuscate

$ java -jar r8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    --pg-conf rules.txt \
    *.class kotlin-stdlib-1.3.11.jar

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[000314] NullsKt.main:([Ljava/lang/String;)V
0000: if-eqz v1, 0011
0002: sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;
…
0010: return-void
0011: const-string v1, "args"
0013: invoke-static {v1}, Lkotlin/jvm/internal/Intrinsics;.throwParameterIsNullException:(Ljava/lang/String;)V
```

The string constant load and `Intrinsics` method call which started the method body in the D8 output above has been replaced with a standard null check via the `if-eqz` bytecode. If that null check succeeds (i.e., the reference is null), the program jumps ahead to the end of the method's bytecodes where the code that builds and throws the exception lives. As a result, in the normal operation of this method where `args` is non-null, the runtime can execute bytecodes index 0000 through 0010 without a jump.

If we make a quick conjecture about _why_ the bytecode looks like this with R8 we might say that it's the result of inlining. In part 1 we saw the `coalesce` function be inlined and so here the `Instrinsics.checkParameterIsNotNull` implementation could have just been inlined too. A quick glance at [its implementation](https://github.com/JetBrains/kotlin/blob/d5ebe2e66a974c3a51d6136d8e6980708cbdd058/libraries/stdlib/jvm/runtime/kotlin/jvm/internal/Intrinsics.java#L114-L118) does show a standard null check and a call to `Instrincs.throwParameterIsNullException`.

```java
public static void checkParameterIsNotNull(Object value, String paramName) {
  if (value == null) {
    throwParameterIsNullException(paramName);
  }
}
```

But the actual R8 bytecode doesn't match what you would expect when thinking about how inlining works. If this method was inlined, the body of the `if` should appear at the top of the method immediately after the check. Beyond that, despite being a tiny method it's actually larger than R8's inlining threshold. The only way it would be inlined is if it was used infrequently (which it isn't). There's a few tricks at play here which produce the actual result we're seeing.

The first trick is that R8 will increase the inlining threshold for a method when it null checks an argument whose value is also an argument at the call site. Since `checkParameterIsNotNull` is _only_ used for arguments at call sites the inlining threshold for this method goes up. The body of the method is otherwise empty so it becomes eligible and is inlined.

The second trick is that R8 can recognize the sequence of bytecodes which both perform a null check on an argument and then throw an exception. When this pattern is recognized, R8 assumes it's the uncommon path for method execution. In order to optimize for the common path, the null check is inverted so that the non-null case immediately follows the check. The exception-throwing code is pushed to the bottom of the method.

But the `if` check of `checkParameterIsNotNull` does not match the sequence of bytecodes R8 needs to recognize the argument-check pattern. The body of the `if` contains a static method call instead of an exception being thrown. So the final trick is that R8 has an intrinsic which recognizes calls to `Intrinsics.throwParameterIsNullException` as being equivalent to throwing an exception. This allows the body to correctly match the pattern.

These three tricks combine to explain why R8 produces the bytecode we see above.

And remember, _every_ method which is potentially visible to a non-Kotlin caller has this code for _every_ non-null parameter. That's a large amount of occurrences in any non-trivial app!

With R8 replacing a static method call with a standard null-check and moving the uncommon case to the end of the method the code retains the safety of the check while minimizing the performance implications.

### Combining Null Information

Once again, [part 1 of this post](/r8-optimization-null-data-flow-analysis-part-1/) showed R8 using nullability information of values to eliminate unnecessary null checks. The first half of this part showed R8 raise inlining thresholds to ignore null checks and replace Kotlin's `Intrinsic` method with standard null check bytecodes. These two features sound like they could combine to make some impactful changes. And they do!

This example adds a function, `String.double`, which just duplicates the string its called on. This function is invoked on the result of `coalesce` with a safe-call operator since null might be returned.

```kotlin
fun String.double(): String = this + this

fun coalesce(a: String?, b: String?): String? = a ?: b

fun main(args: Array<String>) {
  println(coalesce(null, "two")?.double())
}
```

Before looking at the R8 output, let's enumerate the null checks which are present before dexing and optimizing:

 1. `args` argument is checked because it's a public function.
 2. The return value of `coalesce` is checked before conditionally invoking `double`.
 3. `coalesce` checks the `first` argument before conditionally returning either `first` or `second`.
 4. `double`'s receiver is checked because it's a public function.

You can run D8 on the example to confirm. But running with R8 produces a pretty picture.

```
[000310] NullsKt.main:([Ljava/lang/String;)V
0000: if-eqz v1, 0019
0002: const-string v1, "two"
0004: new-instance v0, Ljava/lang/StringBuilder;
0006: invoke-direct {v0}, Ljava/lang/StringBuilder;.<init>:()V
0009: invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
000c: invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
000f: invoke-virtual {v0}, Ljava/lang/StringBuilder;.toString:()Ljava/lang/String;
0012: move-result-object v1
0013: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
0015: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(Ljava/lang/Object;)V
0018: return-void
0019: const-string v1, "args"
001b: invoke-static {v1}, Lkotlin/jvm/internal/Intrinsics;.throwParameterIsNullException:(Ljava/lang/String;)V
```

All of the null checks except the one guarding the argument were eliminated!

Because R8 can prove `coalesce` returns a non-null reference, all downstream null checks can be eliminated. This means the safe-call isn't needed and is replaced with a normal method call. The null check on the receiver of the `double` function is also eliminated.

### No Inlining Required

The examples so far have included inlining to aid in reducing the output. In practice, inlining won't happen to the degree that it does in these small examples. That doesn't prevent elimination of all null checks.

While I find the Kotlin examples particularly compelling here because of the forced, defensive null checks, looking at the optimization for Java is interesting because of the opposite behavior. Java doesn't put defensive null checks on public method arguments and so data flow analysis can use other patterns for nullability signals even without inlining.

```java
final class Nulls {
  public static void main(String[] args) {
    System.out.println(first(args));
    if (args == null) {
      System.out.println("null!");
    }
  }

  public static String first(String[] values) {
    if (values == null) throw new NullPointerException("values == null");
    return values[0];
  }
}
```

Every reference is potentially-nullable in Java. As a result, it's not uncommon to see defensive checks in library methods like `first` (even when annotated `@NonNull`!). Library methods might be large or called from all over your application and so they usually aren't inlined. To simulate this, we can explicitly tell R8 to keep `first` as a method in the `rules.txt`.

```diff
 -keepclasseswithmembers class * {
   public static void main(java.lang.String[]);
 }
 -dontobfuscate
+-keep class Nulls {
+   public static java.lang.String first(java.lang.String[]);
+}
```

Even without inlining the output is favorable.

```
[000144] Nulls.first:([Ljava/lang/String;)Ljava/lang/String;
0000: if-eqz v1, 0006
0002: const/4 v0, #int 0
0003: aget-object v1, v1, v0
0005: return-object v1
0006: new-instance v1, Ljava/lang/NullPointerException;
0008: const-string v0, "values == null"
000a: invoke-direct {v1, v0}, Ljava/lang/NullPointerException;.<init>:(Ljava/lang/String;)V
000d: throw v1

[000170] Nulls.main:([Ljava/lang/String;)V
0000: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
0002: invoke-static {v1}, LNulls;.first:([Ljava/lang/String;)Ljava/lang/String;
0005: move-result-object v1
0006: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
0009: return-void
```

In `first`, R8 has once again inverted the null check so that the uncommon case of throwing an exception is at the bottom of the method at index 0006. Normal execution of this method will flow from index 0000 straight down to 0005 and return.

In `main`, the explicit null check of `args` and its printing has disappeared. This is because R8 has tracked that the `args` reference flowed into `first` where it became impossible to be null after that call. As a result, any null checks that occur after the call to `first` don't need to occur.

---

All of these examples are small and somewhat contrived, but they demonstrate a part of the data-flow analysis that R8 is doing with regard to nullability and null checking. In the scope of your whole application whether it's Java, Kotlin, or mixed, unnecessary null checks and unused branches can be eliminated without sacrificing the safety they otherwise afford.

Next week's R8 post will cover my favorite feature of the tool. It's also the one which I think produces the best demo and which resonates with every Android developer. Stay tuned!

_(This post was adapted from a part of my [Digging into D8 and R8](/digging-into-d8-and-r8) talk. Watch the video and look out for future blog posts for more content like this.)_
