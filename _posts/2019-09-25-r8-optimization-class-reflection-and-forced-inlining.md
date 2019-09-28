---
title: 'R8 Optimization: Class Reflection and Forced Inlining'
layout: post

categories: post
tags:
- Android
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

The previous post on R8 covered [method outlining](/r8-optimization-method-outlining/) which automatically de-duplicated code. This was actually a detour from what I had promised was next at the end of the [class constant operations](/r8-optimization-class-constant-operations/) post which preceded it. So let's get back on track.

Class constant operations allow R8 to take calls such as `MyActivity.class.getSimpleName()` and replace it with the string literal `"MyActivity"`. This was presented in the context of log tags, where you might write that expression instead of the string literal so that the tag always reflects the actual class name, even after obfuscation. This works great in a static context where the `MyActivity.class` literal is fixed, but it does not work when used on an instance.


### Instance reflection

When dealing with an instance, the `Class` reference is obtained by calling `getClass()` instead of a `MyActivity.class` literal. This operation is not terribly expensive, but it is still a form of reflection.

```java
class MyActivity extends Activity {
  @Override void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String name = this.getClass().getSimpleName();
    Log.e(name, "Hello!");
  }
}
```

The `getClass()` API is just a normal method on every `Object` and appears as a normal `invoke-virtual` in bytecode.

```
[0003d0] MyActivity.onCreate:(Landroid/os/Bundle;)V
0000: invoke-super {v1, v2}, Landroid/app/Activity;.onCreate:(Landroid/os/Bundle;)V
0003: invoke-virtual {v1}, Ljava/lang/Object;.getClass:()Ljava/lang/Class;
0006: move-result-object v2
0007: invoke-virtual {v2}, Ljava/lang/Class;.getSimpleName:()Ljava/lang/String;
000a: move-result-object v2
```

Since R8 is performing whole-program analysis, it knows that there are no subtypes of `MyActivity` even though it's not marked as `final`. As a result, it can replace calls to `this.getClass()` with `MyActivity.class`.

```diff
 [000170] MyActivity.onCreate:(Landroid/os/Bundle;)V
 0000: invoke-super {v1, v2}, Landroid/app/Activity;.onCreate:(Landroid/os/Bundle;)V
-0003: invoke-virtual {v1}, Ljava/lang/Object;.getClass:()Ljava/lang/Class;
-0006: move-result-object v2
+0003: const-class v2, Lcom/example/MyActivity;
 0005: invoke-virtual {v2}, Ljava/lang/Class;.getSimpleName:()Ljava/lang/String;
 0008: move-result-object v2
```

Beyond that, the `Class<?>` reference immediately flows into a call to `getSimpleName()`. Thus, the optimization covered in [the previous post](/r8-optimization-class-constant-operations/) can now apply producing only the simple constant string.

```diff
 0000: invoke-super {v1, v2}, Landroid/app/Activity;.onCreate:(Landroid/os/Bundle;)V
-0003: const-class v2, Lcom/example/MyActivity;
-0005: invoke-virtual {v2}, Ljava/lang/Class;.getSimpleName:()Ljava/lang/String;
-0008: move-result-object v2
+0003: const-string v2, "MyActivity"
```

But how often do you write `this.getClass()` where the class is known unequivocally?

In keeping with the example of logging, let's look at a hypothetical library which accepts an `Activity` and an optional name for use with logging.
```java
class SomeLibrary {
  static SomeLibrary create(Activity activity) {
    return create(activity, activity.getClass().getSimpleName());
  }

  static SomeLibrary create(Activity activity, String name) {
    return new SomeLibrary(activity, name);
  }

  private SomeLibrary(Activity activity, String name) {
    // ...
  }

  void doSomething() {
    Log.d(name, "Starting work!");
    // ...
  }
}
```

When a name is not supplied, it is inferred from the activity class name using `getClass().getSimpleName()`. Since the input is not a fixed class literal, this cannot be replaced with a string at compile-time.

Calling this from an activity is straightforward and reminiscent of a few popular libraries.
```java
class MyActivity extends Activity {
  private SomeLibrary library;

  @Override void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    library = SomeLibrary.create(this);
  }

  @Override void onResume() {
    library.doSomething();
  }
}
```

The inlining of method bodies has been a staple in previous R8 posts as it often unlocks optimizations that otherwise would not apply. This example is no different in that regard, but it _is_ different because the `create(Activity)` method is too large to be inlined normally. The three method calls to `getClass()`, `getSimpleName()`, and the `create()` overload, along with specifying the arguments to those methods, exceeds the maximum allowed method body size for inline candidates.


### Inlining by force

R8 advertises its configuration rules as being compatible with those documented for ProGuard, the tool it's meant to replace. But aside from honoring what ProGuard supports, it does have a undocumented rules of its own. An example of this was shown in the [value assumption](/r8-optimization-value-assumption/) post (and ProGuard has since come to add support for that rule!). While undocumented, this rule is supported by R8.

Another undocumented, R8-specific rule can help guide inlinining is `-alwaysinline`. This directive overrides the limitations of normal inlining to inline method bodies which might not otherwise be considered. Unfortunately, this rule is undocumented for a very good reason: it is completely unsupported and supposed to be for testing-purposes only.

By using `-alwaysinline`, the `create(Activity)` method can be forced to be inlined.

```
-alwaysinline class com.example.SomeLibrary {
  static void create(android.app.Activity);
}
```

This causes the `getClass().getSimpleName()` calls to be moved from the library code to each call site.

```diff
 @Override void onCreate(Bundle savedInstanceState) {
   super.onCreate(savedInstanceState);
-  library = SomeLibrary.create(this);
+  library = SomeLibrary.create(this, this.getClass().getSimpleName());
 }
```

As a result, we've created the above scenario where the enclosing class is known at compile time. It will be replaced with the `MyActivity.class` class literal which is then quickly replaced with the `"MyActivity"` string literal.

```diff
 @Override void onCreate(Bundle savedInstanceState) {
   super.onCreate(savedInstanceState);
-  library = SomeLibrary.create(this, this.getClass().getSimpleName());
+  library = SomeLibrary.create(this, "MyActivity");
 }
```

Once again we see the power of successive optimizations applying. No more reflection!

Unlike previous posts where inlining happened automatically, the unsupported `-alwaysinline` directive forced this behavior in R8. Inlining should only be forced like this when you know that a subsequent optimization will apply to offset the bytecode impact. In this example, there is a chance that the instance cannot be determined at compile-time and we end up slightly bloating the bytecode. And, of course, the unsupported nature of the rule means it may change or disappear at any time. For a stable solution, Kotlin's `inline` function modifier has the same effect, but only for Kotlin callers.

---

Replacing calls to `getClass()` with a class literal is a very small optimization. It saves only four bytes when inlined, but its greatest contribution is enabling other optimizations to apply. Subsequent calls to methods like `getSimpleName()` can now be eliminated which then opens up [string optimizations](/r8-optimization-string-constant-operations/) to potentially apply.

In future R8 posts we'll come back to this `getClass()` optimization and others which it enables. But for now, there's a lot of other R8 optimizations that I want to cover without promising a specific topic next, so stay tuned.