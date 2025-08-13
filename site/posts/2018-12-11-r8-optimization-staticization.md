---
title: 'R8 Optimization: Staticization'
layout: post

categories: post
tags:
- Android
- Java
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). This post introduces R8.

The first three posts ([1](/androids-java-8-support/), [2](/androids-java-9-10-11-and-12-support/), [3](/avoiding-vendor-and-version-specific-vm-bugs/)) in this series explored D8. Among its core responsibility of converting Java bytecode to Dalvik bytecode, it desugars new Java language features and works around vendor- and version-specific bugs in Android's VMs.

In general, D8 doesn't perform optimization. It may choose to use Dalvik bytecodes which more efficiently represent the intent of Java bytecodes (as seen with [the `not-int` example](/avoiding-vendor-and-version-specific-vm-bugs/#not-a-not)). Or, in the process of desugaring language features, it may choose to optimize the desugared code it is generating. Aside from these very localized changes, D8 otherwise performs a direct translation.

R8 is a version of D8 that also performs optimization. It's not a separate tool or codebase, just the same tool operating in a more advanced mode. Where D8 first parses Java bytecode into its own intermediate representation (IR) and then writes out the Dalvik bytecode, R8 adds optimization passes over the IR before its written out.

This post (and a bunch of future posts) are going to explore some of the individual optimizations that R8 performs. We start with an optimization called _staticization_ which means the act of making something static.


### Companion Objects

Kotlin uses companion objects to model the features of Java's `static` modifier. They're actually a much more powerful language feature allowing things like inheritance and implementing interfaces. That power comes with an associated cost, however, and we pay for that cost regardless of whether we're using the added power or just emulating `static`.

```kotlin
fun main(vararg args: String) {
  println(Greeter.hello().greet("Olive"))
}

class Greeter(val greeting: String) {
  fun greet(name: String) = "$greeting, $name!"

  companion object {
    fun hello() = Greeter("Hello")
  }
}
```

In this example, the `Greeter` class uses a `companion object` to expose functionality that isn't tied to instances of `Greeter`. A convenience factory `hello` returns instances of `Greeter` initialized with the string "Hello". A `main` function calls the factory and then greets my dog Olive.

Compiling with `kotlinc`, dexing with D8, and dumping the Dalvik bytecode with `dexdump` we can see how this is implemented.

```
$ kotlinc *.kt

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
…
[000370] GreeterKt.main:([Ljava/lang/String;)V
0000: sget-object v1, LGreeter;.Companion:LGreeter$Companion;
0002: invoke-virtual {v1}, LGreeter$Companion;.hello:()LGreeter;
0005: move-result-object v1
0006: const-string v0, "Olive"
0008: invoke-virtual {v1, v0}, LGreeter;.greet:(Ljava/lang/String;)Ljava/lang/String;
000b: move-result-object v1
000c: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
000e: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(Ljava/lang/Object;)V
0011: return-void
…
```

Bytecode index `0000` loads an instance of the `Greeter$Companion` class from a static `Companion` field on `Greeter`. Index `0002` then makes a virtual method call to the `hello` function on that instance.

Looking at the nested `Companion` class confirms that it contains virtual (aka non-static methods).

```
Virtual methods   -
  #0              : (in LGreeter$Companion;)
    name          : 'hello'
    type          : '()LGreeter;'
    access        : 0x0011 (PUBLIC FINAL)
[000314] Greeter.Companion.hello:(Ljava/lang/String;)Ljava/lang/String;
0000: new-instance v0, LGreeter;
0002: const-string v1, "Hello"
0004: invoke-direct {v0, v1}, LGreeter;.<init>:(Ljava/lang/String;)V
0007: return-object v0
```

The use of a companion on `Greeter` means that a second, nested class named `Companion` is generated which adds to our binary size and slows startup because of additional class loading. The singleton instance of this class is retained in memory for the life of our application adding memory pressure. And finally, the use of instance methods require virtual calls which are slower than static calls. Granted, the impact of all these things for just one class is _extremely_ minor, but in a large application written entirely in Kotlin it begins to contribute non-trivial overhead.

We can convert the Java classfiles to Dalvik using R8 instead of D8 and see what optimizations it applies. The flags to run R8 is nearly identical to D8 except it requires adding `--pg-conf` to supply a [ProGuard-compatible configuration file](https://www.guardsquare.com/en/products/proguard/manual/usage). The one in use here keeps the `main` method as an entry point (otherwise the dex file would be empty) and disables class and method name obfuscation for the sake of readability.

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
    *.class
```

R8 will produce a `classes.dex` just like D8 except with contents that have been optimized.

```
$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
…
[000234] GreeterKt.main:([Ljava/lang/String;)V
0000: invoke-static {}, LGreeter;.hello:()LGreeter;
0003: move-result-object v1
0004: const-string v0, "Olive"
0006: invoke-virtual {v1, v0}, LGreeter;.greet:(Ljava/lang/String;)Ljava/lang/String;
0009: move-result-object v1
000a: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
000c: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(Ljava/lang/Object;)V
000f: return-void
…
```

The `main` method has changed slightly from the original version. Instead of an `sget-object` to look up the `Companion` instance and an `invoke-virtual` to call a `hello` instance method, only an `invoke-static` remains. It's also important to note that R8 hasn't just made the `hello` method static inside the `Companion` class, it has moved the method from the `Companion` to be directly on the `Greeter` class.

```
  #1              : (in LGreeter;)
    name          : 'hello'
    type          : '(Ljava/lang/String;)Ljava/lang/String;'
    access        : 0x0019 (PUBLIC STATIC FINAL)
[0002bc] Greeter.hello:(Ljava/lang/String;)Ljava/lang/String;
[000240] Greeter.hello:()LGreeter;
0000: new-instance v0, LGreeter;
0002: const-string v1, "Hello"
0004: invoke-direct {v0, v1}, LGreeter;.<init>:(Ljava/lang/String;)V
0007: return-object v0
```

With the `hello` method having been moved, the entire `Companion` class and the singleton field holding its instance on `Greeter` have both been removed.

This is staticization in practice. R8 finds occurrences of instance methods where the instance isn't actually required and makes them static. It also has special knowledge of how Kotlin implements companions so that in addition to making their methods static the extra class they'd otherwise generate can also be removed.


#### Source Transformation

Understanding exactly how a Kotlin companion is represented in bytecode and how R8's optimization works in bytecode can be challenging. In order to better understand both of these things we can emulate them at the source-code level.

The Kotlin compiler compiles the original `Greeter` class into Java bytecode which approximates to the following Java source code.

```java
public final class Greeter {
  public static final Companion Companion = new Companion();

  private final String greeting;

  public Greeter(String greeting) {
    this.greeting = greeting;
  }

  public String getGreeting() {
    return greeting;
  }

  public String greet(String name) {
    return greeting + ", " + name;
  }

  public static final class Companion {
    private Companion() {}

    public Greeter hello() {
      return new Greeter("Hello");
    }
  }
}
```

The `val greeting: String` primary constructor property declaration is translated into a private field, constructor parameter, constructor assignment statement, and getter method. The `companion object` becomes a nested class named `Companion` and the enclosing `Greeter` class keeps a static, final singleton instance of it.

The main method is put into yet another class called `GreeterKt` which is based on the filename, `Greeter.kt`.

```java
public final class GreeterKt {
  public static void main(String[] args) {
    System.out.println(Greeter.Companion.hello().greet("Olive"));
  }
}
```

In order to access the `hello` factory method, the `main` method calls through the static `Companion` field.

R8's optimization alters the code into what we otherwise would have written if the original `Greeter` was written in Java.

```diff
 public final class Greeter {
-  public static final Companion Companion = new Companion();
-
   private final String greeting;
@@

-  public static final class Companion {
-    private Companion() {}
-
-    public Greeter hello() {
-      return new Greeter("Hello");
-    }
-  }
+  public static Greeter hello() {
+    return new Greeter("Hello");
+  }
 }
```

The `hello` method becomes a static method directly inside `Greeter` and the `Companion` class and singleton instance field are removed.

```diff
 public final class GreeterKt {
   public static void main(String[] args) {
-    System.out.println(Greeter.Companion.hello().greet("Olive"));
+    System.out.println(Greeter.hello().greet("Olive"));
   }
 }
```

The `main` method is also updated to reflect this change, again looking more like if it were originally written in Java.


#### `@JvmStatic`

If you're familiar with Kotlin and its Java interoperability story, using the `@JvmStatic` annotation might have come to mind to achieve a similar effect.

```diff
   companion object {
+    @JvmStatic
     fun hello() = Greeter("Hello")
```

With the annotation added to the original example, running it through D8 only and dumping the bytecode shows an interesting result.

```
$ kotlinc *.kt

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
…
  #2              : (in LGreeter;)
    name          : 'hello'
    type          : '()LGreeter;'
    access        : 0x0019 (PUBLIC STATIC FINAL)
[00042c] Greeter.hello:()LGreeter;
0000: sget-object v0, LGreeter;.Companion:LGreeter$Companion;
0002: invoke-virtual {v0, v1}, LGreeter$Companion;.hello:()LGreeter;
0005: move-result-object v1
0006: return-object v1
…
```

A static `hello` method was added to the `Greeter` class, but it's just a trampoline into the `Companion` instance and the instance method of the same name.

```
[000234] GreeterKt.main:([Ljava/lang/String;)V
0000: sget-object v1, LGreeter;.Companion:LGreeter$Companion;
0002: invoke-virtual {v1}, LGreeter$Companion;.hello:()LGreeter;
…
```

And even with that static method present, Kotlin callers still do the `Companion` instance lookup and virtual method call.

Even with `@JvmStatic` present, R8 will still perform the staticization optimization. The `Companion`'s `greet` method body will move into the static `greet` method on `Greeter`, the `main` function will do a static method call, and the entire `Companion` class will be removed.


### More Than Companions

This optimization isn't limited to only Kotlin `companion object`s. Regular Kotlin `object`s will have their methods made `static`.

```kotlin
@Module
object HelloGreeterModule {
  @Provides fun greeter() = Greeter("Hello")
}
```

Java classes will also receive this optimization when the instance is not needed.

```java
public final class Thing {
  public static final Thing INSTANCE = new Thing();

  private Thing() {}

  public void doThing() {
    // …
  }
}
```

Running R8 on these examples and validating the resulting bytecode is left as an exercise for the reader.


---

In summary, staticization takes instance methods which don't actually require access to an instance and makes them static. For Kotlin, it understands the bytecode of companion objects and can often eliminate them entirely when they're only being used to emulate Java's `static`.

Many R8 optimizations are aware of Kotlin-specific bytecode patterns in order to make them more effective. Stay tuned for the next post which features another R8 optimization that works well with Kotlin.



_(This post was adapted from a part of my [Digging into D8 and R8](/digging-into-d8-and-r8) talk. Watch the video and look out for future blog posts for more content like this.)_