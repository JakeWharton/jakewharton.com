---
title: 'R8 Optimization: Class Constant Operations'
layout: post

categories: post
tags:
- Android
- R8
---
 
> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

The [previous post in the series](/r8-optimization-string-constant-operations/) showed R8 (and D8) invoking string methods at compile-time when the inputs were all constants. R8 is able to do this because the content of constant strings is available inside the bytecode. That post also claimed that strings are the only non-primitive type that can be manipulated like this at compile-time.

There is, however, another object type that can be manipulated at compile-time: classes. Classes are templates for the instances we interact with at runtime. Since bytecode fundamentally exists to hold these templates, some operations on classes can thus be performed at compile time.


### Log Tags

There's an ongoing debate (if you can even call it that) on the best way to define a tag string in a class. Historically there have been two strategies: string literals and calling `getSimpleName()` on the class.

```java
private static final String TAG = "MyClass";
// or
private static final String TAG = MyClass.class.getSimpleName();
```

Let's compare the difference in bytecode by defining both and adding some log messages.

```java
class MyClass {
  private static final String TAG_STRING = "MyClass";
  private static final String TAG_CLASS = MyClass.class.getSimpleName();

  public static void main(String... args) {
    Log.d(TAG_STRING, "String tag");
    Log.d(TAG_CLASS, "Class tag");
  }
}
```

Compiling, dexing, and dumping the Dalvik bytecode shows the effect of the choice.

```
[000194] MyClass.<clinit>:()V
0000: const-class v0, LMyClass;
0002: invoke-virtual {v0}, Ljava/lang/Class;.getSimpleName:()Ljava/lang/String;
0005: move-result-object v0
0006: sput-object v0, LMyClass;.TAG_CLASS:Ljava/lang/String;
0008: return-void

[000120] MyClass.main:([Ljava/lang/String;)V
0000: const-string v1, "MyClass"
0002: const-string v0, "String tag"
0004: invoke-static {v1, v0}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I
0007: sget-object v1, LMyClass;.a:Ljava/lang/String;
0009: const-string v0, "Class tag"
000b: invoke-static {v1, v0}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I
000e: return-void
```

In the `main` method, index `0000` loads the constant string of the tag. Index `0007`, on the other hand, has to look up the static field in order to get the tag value. In the `<clinit>` method, the static field is initialized by loading the `MyClass` class and then invoking `getSimpleName` at runtime. This method is automatically invoked the first time the class is loaded.

The string literal is more efficient but using the class reference is resilient to things like refactoring. But if you've read any of these posts so far, you should know where this is going! Let's try again with R8 and look at its output.

```
[000120] MyClass.main:([Ljava/lang/String;)V
0000: const-string v1, "MyClass"
0002: const-string v0, "String tag"
0004: invoke-static {v1, v0}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I
0007: const-string v0, "Class tag"
0009: invoke-static {v1, v0}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I
000c: return-void
```

The bytecode which came after index `0004` that loaded the second tag has disappeared and `v1`, the string literal tag, was re-used for the second call to `Log`.

Since the simple name of `MyClass` is known at compile-time, R8 has replaced `MyClass.class.getSimpleName()` with the string literal `"MyClass"`. Because the field value is now a constant, the `<clinit>` method becomes empty and is removed. At the usage site, the `sget-object` bytecode was replaced with a `const-string` for the constant. Finally, the two `const-string` bytecodes which reference the same string were de-duplicated and the value is reused.

So while the verdict might not be in on which pattern to use for log tag fields, R8 makes sure that those choosing the class-based route don't incur any additional runtime overhead. And because the `getSimpleName()` computation is trivial, D8 will actually perform it as well![^1]

[^1]: It won't, however, replace the `sget-object` bytecodes with `const-string` nor remove the now-empty `<clinit>` method.


### Applicability

Being able to compute `getSimpleName()` (and `getName()` and `getCanonicalName()` too!) on a `MyClass.class` reference seems of limited use–potentially even _solely_ for this log tag case. The optimization only works with a class literal reference–`getClass()` won't work! It is once again in combination with other R8 features that this optimization starts to apply more.

Consider a class which abstracts logging and uses a static initializer that accepts which class will be sending log messages.

```java
class Logger {
  static Logger get(Class<?> cls) {
    return new Logger(cls.getSimpleName());
  }
  private Logger(String tag) { /* … */ }
 
}

class MyClass {
  private static final Logger logger = Logger.get(MyClass.class);
}
```

If `Logger.get` is inlined to all of its call sites, the call to `Class.getSimpleName` which previously had a dynamic input from the method parameter will change to a static input of a class reference (`MyClass.class` in this case). R8 can now replace the call with a string literal resulting in a field initializer that directly invokes the constructor (which will also have its `private` modifier removed).

```java
class MyClass {
  private static final Logger logger = new Logger("MyClass");
}
```

This relies on the `get` method being small enough or being called in a way that the heuristics of R8 will perform the inlining.

The Kotlin language offers the ability to force a function to be inlined. It also allows marking a generic type parameter on an inline fuction as "reified" which ensures that the compiler knows which class it resolves to when compiling. With these features we can ensure our function is always inlined and that `getSimpleName` is always called on an explicit class reference.

```kotlin
class Logger private constructor(val tag: String) {
 
}
inline fun <reified T : Any> logger() = Logger(T::class.java.simpleName)

class MyClass {
 
  companion object {
    private val logger = logger<MyClass>()
  }
}
```

The initializer for `logger` will always have the bytecode equivalent of `MyClass.class.getSimpleName()` which R8 can then always replace with a string literal.

For other Kotlin examples, type inference can often allow omitting the explicit type parameter.

```kotlin
inline fun <reified T> typeAndValue(value: T) = "${T::class.java.name}: $value"
fun main() {
  println(typeAndValue("hey"))
}
```

This example outputs "java.lang.String: hey" and its bytecode contains only two constant strings, a `StringBuilder` to concatenate them, and a call to `System.out.println`. And if [this issue](https://issuetracker.google.com/issues/114002137) was implemented, you'd wind up with only a single string and the call to `System.out.println`.


### Obfuscation and Optimization

Since this optimization operates on classes, it has to interact with the other features of R8 that might affect a class such as obfuscation and different optimizations.

Let's go back to the original example.

```java
class MyClass {
  private static final String TAG_STRING = "MyClass";
  private static final String TAG_CLASS = MyClass.class.getSimpleName();

  public static void main(String... args) {
    Log.d(TAG_STRING, "String tag");
    Log.d(TAG_CLASS, "Class tag");
  }
}
```

What happens if this class is obfuscated? If R8 was not replacing the `getSimpleName` call, the first log message would have a tag of "MyClass" and the second would have a tag matching the obfuscated class name such as "a".

In order for R8 to be allowed to replace `getSimpleName` it needs to do so with a value that matches what the behavior would be at runtime. Thankfully, since R8 is also the tool which is performing obfuscation, it can defer the replacement until the the class has been given its final name.

```
[000158] a.main:([Ljava/lang/String;)V
0000: const-string v1, "MyClass"
0002: const-string v0, "String tag"
0004: invoke-static {v1, v0}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I
0007: const-string v1, "a"
0009: const-string v0, "Class tag"
000b: invoke-static {v1, v0}, Landroid/util/Log;.d:(Ljava/lang/String;Ljava/lang/String;)I
000e: return-void
```

Note how index `0007` now will load a tag value for the second log call (unlike the original R8 output) and how it correctly reflects the obfuscated name.

There are other R8 optimizations which affect the class name even when obfuscation is disabled. While I plan to cover it in a future post, R8 will sometimes merge a superclass into a subtype if it can prove the superclass isn't needed and the subtype is the only one. When this happens, the class name string optimization will correctly reflect the subtype name even if the original code was equivalent to `TheSupertype.class.getSimpleName()`.


### String Data Section

[The previous post](/r8-optimization-string-constant-operations/#money-left-on-the-table) talked about how performing an operation like `String.substring` or string concatenation at compile-time could lead to the string section of the dex file increasing in size.[^2] The optimization in this post produces strings which might not otherwise exist so that is also a possibility here.

[^2]: Coincidentally, compile-time `substring()` computation [landed in R8](https://r8-review.googlesource.com/c/r8/+/34260/) yesterday!

There's two cases to consider: when obfuscation is enabled and when it is disabled.

When obfuscation is enabled calls to `getSimpleName()` should not create a new string. Both classes and methods will be obfuscated using the same dictionary which by default starts with single letters. This means that for an obfuscated class named `b`, inserting the string "b" is almost always free since there is going to be a method or field whose name is also `b`. In the dex file all strings are stored in a single pool which contains the literals, class names, method names, and field names making the probability of a match when obfuscating very high.

With obfuscation disabled, though, replacing `getSimpleName()` is never free. Despite the unified string section of the dex file, class names are stored in [type descriptor form](https://source.android.com/devices/tech/dalvik/dex-format#typedescriptor). This includes the package name, uses `/` as separators, and is prefixed with `L` and suffixed with `;`. For `MyClass`, if in a hypothetical `com.example` package, the string data contains an entry for `Lcom/example/MyClass;`. Because of this format, the string "MyClass" doesn't already exist and will need to be added.

Both `getName()` and `getCanonicalName()` will also, unfortunately, always create new strings. Even though these return a fully-qualified strings, they don't match the type descriptor form which is already present in string data.

Since this optimization has the potential to create a large amount of strings, it's currently disabled for everything except top-level types. This means that it works in the `MyClass` example from this post but in a nested type or anonymous type it will not apply. There is also some escape analysis done to avoid applying the optimization for calls inside a single method. Both of these minimize any adverse impact on your dex size.


---

The next post on R8 will look at an optimization which produces class literals like those used in this post (i.e., the `const-class` bytecodes created from `MyClass.class`). You won't be surprised when that post shows class literal creation which in turn allows the optimizations from this post to apply which in turn allows the string optimizations to apply and so on.

_(This post was adapted from a part of my [Digging into D8 and R8](/digging-into-d8-and-r8) talk that was never presented. Watch the video and look out for future blog posts for more content like this.)_
