---
title: 'R8 Optimization: Value Assumption'
layout: post

categories: post
tags:
- Android
- Kotlin
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

The previous post ([part 1](/r8-optimization-null-data-flow-analysis-part-1/), [part 2](/r8-optimization-null-data-flow-analysis-part-2/)) featured R8 performing data-flow analysis of variables in order to determine if they were maybe null, always null, or never null, and then potentially performing dead-code elimination based on that info.

Another way to think about that optimization is that R8 tracks the use of a variable along with a range of its possible nullability. If any conditional against that range can be determined to always produce the same result, dead-code elimination removes the unused branches and the conditional disappears. [Part 2 of the last post](/r8-optimization-null-data-flow-analysis-part-2/#no-inlining-required) ended with an example where an `args` variable was passed into a `first` method and then checked for null before printing.

```java
System.out.println(first(args));
if (args == null) {
  System.out.println("null!");
}
```

The range of nullability for `args` in that snippet is `[null, non-null]` (meaning its either null or a non-null reference).

```java
System.out.println(first(args/* [null, non-null] */));
if (args/* [null, non-null] */ == null) {
  System.out.println("null!");
}
```

In this state, R8 can't do anything to the conditional because the reference might actually be null. However, if the `first` method checks its argument for null and throws an exception (as it did in that post), null can be eliminated as a possible value _after_ the method call.

```java
System.out.println(first(args/* [null, non-null] */));
if (args/* [non-null] */ == null) {
  System.out.println("null!");
}
```

With `args` only able to be a non-null reference at the time of the `if` check against null, the conditional will always be false and can be removed by normal dead-code elimination.

```java
System.out.println(first(args/* [null, non-null] */));
if (false) {
  System.out.println("null!");
}
```

Right now this range tracking doesn't extend beyond nullability. Checking an integer for being positive twice in a method does not cause R8 to eliminate the second conditional. That being said, there is a way to manually help R8 understand the range of other types.

### Value Assumption

R8 uses the same configuration syntax as ProGuard in order to simplify migration. Once you've migrated, though, there are some R8-specific flags you can specify. This post deals with one of those flags: `-assumevalues`.[^proguard-assumevalues]

[^proguard-assumevalues]: Since the original presentation, ProGuard has opted to include this flag and functionality in its 6.1.0 version (currently in beta at the time of writing). So while it originated in R8, it is technically no longer R8-specific.

The `-assumevalues` flag informs R8 that the specified field value or method's return value will always be between a certain range or equal to a single value. The paragraph above mentioned that R8 won't eliminate a second check for a positive value like it would a second check for null. If the integer value being checked comes from a method or is stored in a field this flag can help.

```java
class Count {
  public static void main(String... args) {
    count = 3;
    sayHi();
  }

  private static int count = 1;

  private static void sayHi() {
    if (count < 0) {
      throw new IllegalStateException();
    }
    for (int i = 0; i < count; i++) {
      System.out.println("Hi!");
    }
  }
}
```

This example has a static field that dictates how many times "Hi!" is printed. Compiling, dexing with R8, and dumping the resulting bytecode shows that the check for negative remains in the bytecode despite being an impossible condition.

```
$ javac *.java

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
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[000148] Count.main:([Ljava/lang/String;)V
0000: const/4 v2, #int 3
0001: sput v2, LCount;.count:I
0003: sget v2, LCount;.count:I
0005: if-ltz v2, 0017
0007: const/4 v2, #int 0
0008: sget v0, LCount;.count:I
000a: if-ge v2, v0, 0016
000c: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
000e: const-string v1, "Hi!"
0010: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
0013: add-int/lit8 v2, v2, #int 1
0015: goto 0008
0016: return-void
0017: new-instance v2, Ljava/lang/IllegalStateException;
0019: invoke-direct {v2}, Ljava/lang/IllegalStateException;.<init>:()V
001c: throw v2
```

R8 has inlined `sayHi()` into `main()` but everything is still here. Bytecode index 0000-0001 assign the value of 3 to `count`. Then index 0003-0005 read `count` and check if it's less than 0, jumping to index 0017 if so. Index 0007-00015 is the loop, 0016 is the implicit `return`, and 0017 is the exception code (notice how it's been moved to the bottom as explained in the previous post).

In order for R8 to eliminate the negative check it would need to analyze how the entire program interacts with `count`. While it would be trivial in this tiny example, in a real program the complexity of this task makes it infeasible.

Since this is application code in our control, we have additional knowledge of the domain of `count` which R8 can't infer. Adding an `-assumevalues` flag to our `rules.txt` gives R8 the expected range of values that reading `count` will produce.

```diff
 -keepclasseswithmembers class * {
   public static void main(java.lang.String[]);
 }
 -dontobfuscate
+-assumevalues class Count {
+  static int count return 0..2147483647;
+}
```

Just as it did for tracking whether or not a reference could be null, R8 can now track the range of values of `count`.

```java
if (count/* [0..2147483647] */ < 0) {
  throw new IllegalStateException();
}
for (int i = 0; i < count/* [0..2147483647] */; i++) {
  System.out.println("Hi!");
}
```

With `count` only able to be a positive value at the time of the `if` check for negative, the conditional will always be false and can be removed by normal dead-code elimination.

```java
if (false) {
  throw new IllegalStateException();
}
```

Running R8 with the new `rules.txt` validates that this works.

```
[000128] Count.main:([Ljava/lang/String;)V
0000: const/4 v2, #int 3
0001: sput v2, LCount;.count:I
0003: sget v2, LCount;.count:I
0005: const/4 v2, #int 0
0006: sget v0, LCount;.count:I
0008: if-ge v2, v0, 0014
000a: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
000c: const-string v1, "Hi!"
000e: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
0011: add-int/lit8 v2, v2, #int 1
0013: goto 0006
0014: return-void
```

Bytecode index 0000-0001 is still the assignment, 0005-0013 is the loop, and 0014 is the implicit `return`. No conditional in sight!

#### Side-Effects

In the final bytecode from the previous example, index 0003 still reads `count` despite its value never actually being used (it's immediately overwritten with 0 by the very next bytecode). This is the field read that would have been used for the now-eliminated conditional. Previous posts showed R8 eliminating unused code like this using its static, single-assignment intermediate representation (SSA IR). Why isn't that happening here?

When R8 eliminates code based on `-assumevalues` it explicitly keeps the method call or field read despite not needing the value. A method call might trigger some other side-effect which would result in a behavior change if removed. A field read might cause a class to be loaded for the first time where a static initializer could have side-effects. It's usually unlikely that your application has these side-effects or that you rely on them. Changing the rule from `-assumevalues` to `-assumenosideeffects` assures R8 of this allowing index 0003 to be removed.[^assumenosideeffects-field-bug]

[^assumenosideeffects-field-bug]: Currently this only works for methods and not fields. Follow [issuetracker.google.com/issues/123080377](https://issuetracker.google.com/issues/123080377) for updates on supporting fields.

This example is obviously small and contrived. But does anything come to mind as a real-world use case for eliminating impossible `if` branches by telling R8 the range of an integer field?

### `Build.VERSION.SDK_INT`

As Android developers, we're accustomed to varying implementation based on the version of the OS that our libraries and applications are running on. This is done by checking the `Build.VERSION.SDK_INT` integer field against known API levels.

```java
if (Build.VERSION.SDK_INT >= 21) {
  System.out.println("21+ :-D");
} else if (Build.VERSION.SDK_INT >= 16) {
  System.out.println("16+ :-)")
} else {
  System.out.println("Pre-16 :-(");
}
```

With `-assumevalues`, R8 can now be used to eliminate these unused branches by specifying the supported API range.

```
-assumevalues class android.os.Build$VERSION {
  int SDK_INT return 21..2147483647;
}
```

The range from this rule is used to see if any conditionals can be made constant.

```java
if (Build.VERSION.SDK_INT/* [21..2147483647] */ >= 21) {
  System.out.println("21+ :-D");
} else if (Build.VERSION.SDK_INT/* [21..2147483647] */ >= 16) {
  System.out.println("16+ :-)")
} else {
  System.out.println("Pre-16 :-(");
}
```

In this example, based on the supplied range, both conditional checks will always evaluate to true.

```java
if (true) {
  System.out.println("21+ :-D");
} else if (true) {
  System.out.println("16+ :-)")
} else {
  System.out.println("Pre-16 :-(");
}
```

With the first branch guaranteed to always be taken, dead-code elimination kicks in to remove the `else if` and `else` branches leaving only a single print.

```java
System.out.println("21+ :-D");
```

For `SDK_INT` conditionals in the application code we write day-to-day, there aren't going to be branches for API levels lower than our minimum SDK version. Android's lint tool will actually validate this with its `ObsoleteSdkInt` check (which you should set to error!).

These conditionals are far more pervasive in libraries since they tend to support a larger API range than the consuming application. It's almost guaranteed then that the libraries have branches which will never be executed in the context of your application.

#### AndroidX Core

Whether you know it or not, these `SDK_INT` conditionals are all over your app. The AndroidX 'core' library (formerly the Support 'compat' library) is present in practically in 100% of apps and it exists almost exclusively to host compatibility APIs which use `SDK_INT` checks to vary their implementation. Its minimum supported SDK is 14 which is almost certainly lower than that of your app.

```java
// ViewCompat.java
public static boolean hasOnClickListeners(@NonNull View view) {
  if (Build.VERSION.SDK_INT >= 15) {
    return view.hasOnClickListeners();
  }
  return false;
}
```

There are conditionals for _every_ API level regardless of whether they're needed for your app. The above example has a trivial fallback, but some of the compatibility implementations start to require quite a bit of code.

```java
// ViewCompat.java
public static int getMinimumWidth(@NonNull View view) {
  if (Build.VERSION.SDK_INT >= 16) {
    return view.getMinimumWidth();
  }

  if (!sMinWidthFieldFetched) {
    try {
      sMinWidthField = View.class.getDeclaredField("mMinWidth");
      sMinWidthField.setAccessible(true);
    } catch (NoSuchFieldException e) { }
    sMinWidthFieldFetched = true;
  }
  if (sMinWidthField != null) {
    try {
      return (int) sMinWidthField.get(view);
    } catch (Exception e) { }
  }
  return 0;
}
```

That legacy implementation after the first `if` sits in your APK despite few (if any) apps actually needing a pre-API 16 implementation. Some of the compatibility implementations also require entire classes for support.

```java
// DrawableCompat.java
public static Drawable wrap(@NonNull Drawable drawable) {
  if (Build.VERSION.SDK_INT >= 23) {
    return drawable;
  } else if (Build.VERSION.SDK_INT >= 21) {
    if (!(drawable instanceof TintAwareDrawable)) {
      return new WrappedDrawableApi21(drawable);
    }
    return drawable;
  } else {
    if (!(drawable instanceof TintAwareDrawable)) {
      return new WrappedDrawableApi14(drawable);
    }
    return drawable;
  }
}
```

If your minimum SDK is less than 23 then the `WrappedDrawableApi21` class is in your APK. And if your minimum SDK is less than 21 the `WrappedDrawableApi14` is also in your APK.

**There are over 850 `SDK_INT` checks in AndroidX 'core'** across every API level–double that number across all of AndroidX. You might use a few of these static helpers in your app, but it's other libraries who using are the biggest users of these APIs. Things like `RecyclerView`, fragments, `CoordinatorLayout`, and AppCompat all support API 14 as well and so they frequently call into these methods.

Using `-assumevalues` allows R8 to eliminate compatibility implementations in these methods which will never be used by your app. This means less classes, less methods, less fields, and less code in your release APK.

#### Zero-Overhead Abstraction

A common theme of these posts is multiple features of R8 combining to produce really impressive results. This post is no different! The `SDK_INT` checks in the AndroidX 'core' library delegate to the framework mechanism when available. If your minimum SDK is high enough, R8 will eliminate all of the conditionals in a compat method leaving only the call to the framework.

```java
import android.os.Build;
import android.view.View;

class ZeroOverhead {
  public static void main(String... args) {
    View view = new View(null);
    setElevation(view, 8f);
  }
  public static void setElevation(View view, float elevation) {
    if (Build.VERSION.SDK_INT >= 21) {
      view.setElevation(elevation);
    }
  }
}
```

An app with a minimum SDK of 21 using `-assumevalues` should expect to see the `setElevation` static method become a simple trampoline to the built-in method.

```
$ javac *.java

$ cat rules.txt
-keepclasseswithmembers class * {
  public static void main(java.lang.String[]);
}
-dontobfuscate
-assumevalues class android.os.Build$VERSION {
  int SDK_INT return 21..2147483647;
}

$ java -jar r8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    --pg-conf rules.txt \
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[00013c] ZeroOverhead.main:([Ljava/lang/String;)V
0000: new-instance v1, Landroid/view/View;
0002: const/4 v0, #int 0
0003: invoke-direct {v1, v0}, Landroid/view/View;.<init>:(Landroid/content/Context;)V
0006: sget v0, Landroid/os/Build$VERSION;.SDK_INT:I
0008: const/high16 v0, #int 1090519040
000a: invoke-virtual {v1, v0}, Landroid/view/View;.setElevation:(F)V
000d: return-void
```

After running this through R8, the static `setElevation` method has completely disappeared. At the call site in `main`, bytecode index 000a now shows a direct call to the real `View.setElevation` method.

After `-assumevalues` removed the conditional, the body of the static `setElevation` method is small enough that it becomes eligible for inlining. All calls to `ViewCompat.setElevation` will be rewritten to directly call `view.setElevation`. The small penalty that would otherwise be incurred from the extra method call and conditional can be completely eliminated when they no longer serve a purpose.

#### No Configuration Necessary

If you read [the post on VM-specific workarounds](/avoiding-vendor-and-version-specific-vm-bugs/) you might remember that D8 and R8 have a `--min-api` flag. When the Android Gradle plugin (AGP) invokes D8 or R8 it sets this flag to the minimum SDK version that your app supports. Starting with R8 1.4.22 which is part of AGP 3.4 beta 1 (and newer), a rule for `Build.VERSION.SDK_INT` is automatically added based on the `--min-api` flag's value.

```
-assumevalues public class android.os.Build$VERSION {
  public static int SDK_INT return <minApi>..2147483647;
}
```

Instead of having to know about this R8 feature and manually enable it with your minimum SDK version, the tool enables it by default so that everyone gets smaller APKs and better runtime performance.

Because of the use of `-assumevalues` for this automatic rule, the read of the `Build.VERSION.SDK_INT` field will be retained. You can see this in the bytecode above at index 0006. Unfortunately, switching to `-assumenosideeffects` won't cause the read to be removed like an application field would. Follow [issuetracker.google.com/issues/111763015](https://issuetracker.google.com/issues/111763015) for supporting this behavior on framework fields.

---

Defining a range for `SDK_INT` is by far the most compelling demo of value assumption and now that it's enabled by default should have a positive impact on APKs. Marking `View.isInEditMode()` as always false is potentially another useful default, but [issuetracker.google.com/issues/111763015](https://issuetracker.google.com/issues/111763015) prevents it from working correctly. Other examples will likely vary from app-to-app or depend on the libraries in use.

The next post in the series will take a look at a few optimizations that R8 applies to values which are constants.

_(This post was adapted from a part of my [Digging into D8 and R8](/digging-into-d8-and-r8) talk. Watch the video and look out for future blog posts for more content like this.)_
