---
title: Android's Java 8 Support
layout: post

categories: post
tags:
- Android
- Java
- D8
---

I've worked from home for a few years, and during that time I've heard people around the office complaining about Android's varying support for different versions of Java. Every year at Google I/O you could find me asking about it at the fireside chats or directly to the folks responsible. At conferences and other developer events it comes up in conversation or in talks with different degrees of accuracy. It's a complicated topic because what exactly we mean when talking about Android's Java support can be unclear. There's a lot to a single version of Java: the language features, the bytecode, the tools, the APIs, the JVM, and more.

When someone talks about Android's Java 8 support they usually are referring to the language features. So let's start there with a look at how Android's toolchain deals with the language features of Java 8.


### Lambdas

The banner language feature of Java 8 was by far the addition of lambdas. This brought a more terse expression of code as data whereas previously more verbose constructs like anonymous classes would be used.

```java
class Java8 {
  interface Logger {
    void log(String s);
  }

  public static void main(String... args) {
    sayHi(s -> System.out.println(s));
  }

  private static void sayHi(Logger logger) {
    logger.log("Hello!");
  }
}
```

After compiling this program with `javac`, running it through the legacy `dx` tool produces an error.

```
$ javac *.java

$ ls
Java8.java  Java8.class  Java8$Logger.class

$ $ANDROID_HOME/build-tools/28.0.2/dx --dex --output . *.class
Uncaught translation error: com.android.dx.cf.code.SimException:
  ERROR in Java8.main:([Ljava/lang/String;)V:
    invalid opcode ba - invokedynamic requires --min-sdk-version >= 26
    (currently 13)
1 error; aborting
```

This is because lambdas use a newer bytecode, `invokedynamic`, added in Java 7. As the error message indicates, Android's support for this bytecode requires a minimum API of 26 or newer–something practically unfathomable for applications at the time of writing. Instead, a process named _desugaring_ is used which turns lambdas into representations compatible with all API levels developers are targeting.


### Desugaring History

This history of the Android toolchain's desugaring capability is… colorful. The goal is always the same: allow newer language features to run on all devices.

Initially a third-party tool called [Retrolambda](https://github.com/luontola/retrolambda) had to be used. This worked by using the built-in mechanism which the JVM uses to turn lambdas into classes at runtime except happening at compile-time. The generated classes were very expensive in terms of method count, but work on the tool over time reduced the cost to something reasonable.

The Android tools team then [announced a new compiler](https://android-developers.googleblog.com/2014/12/hello-world-meet-our-new-experimental.html) which would provide Java 8 language feature desugaring along with better performance. This was built on the Eclipse Java compiler but emitting Dalvik bytecode instead of Java bytecode. The Java 8 desugaring was extremely efficient, but otherwise adoption was low, performance was worse, and integration with other tooling was non-existent.

When the new compiler was (thankfully) abandoned, a Java bytecode to Java bytecode transformer which performed desugaring was [integrated into the Android Gradle plugin](https://android-developers.googleblog.com/2017/04/java-8-language-features-support-update.html) from Bazel, Google's bespoke build system. The desugaring output remained efficient but performance still wasn't great. It was eventually made incremental, but work was happening concurrently to provide a better solution.

The [D8 dexer was announced](https://android-developers.googleblog.com/2017/08/next-generation-dex-compiler-now-in.html) to replace the legacy `dx` tool with a promise of having desugar occur during dexing rather than a standalone Java bytecode transformation. The performance and accuracy of D8 compared to `dx` was a big win and it brought with it more efficient desugared bytecode. It was made the default dexer in Android Gradle plugin 3.1 and it then became responsible for desugaring in 3.2.


### D8

Using D8 to compile the above example to Dalvik bytecode succeeds.

```
$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ ls
Java8.java  Java8.class  Java8$Logger.class  classes.dex
```

To see how D8 desugared the lambda we can use the `dexdump` tool which is part of the Android SDK. The tool produces quite a lot of output so we'll only look at the relevant sections.

```
$ $ANDROID_HOME/build-tools/28.0.2/dexdump -d classes.dex
[0002d8] Java8.main:([Ljava/lang/String;)V
0000: sget-object v0, LJava8$1;.INSTANCE:LJava8$1;
0002: invoke-static {v0}, LJava8;.sayHi:(LJava8$Logger;)V
0005: return-void

[0002a8] Java8.sayHi:(LJava8$Logger;)V
0000: const-string v0, "Hello"
0002: invoke-interface {v1, v0}, LJava8$Logger;.log:(Ljava/lang/String;)V
0005: return-void
…
```

If you haven't seen bytecode before (Dalvik or otherwise) don't worry–most of it can be picked up without a full understanding.

In the first block, our `main` method, bytecode index `0000` retrieves a reference from a static `INSTANCE` field on a class named `Java8$1`. Since the original source didn't contain a `Java8$1` class, we can infer that it was generated as part of desugaring. The `main` method's bytecode also doesn't contain any traces of the lambda body so it likely has to do with this `Java8$1` class. Index `0002` then calls the static `sayHi` method with the `INSTANCE` reference. The `sayHi` method requires a `Java8$Logger` argument so it would seem the `Java8$1` class implements that interface. We can verify all of this in the output.

```
Class #2            -
  Class descriptor  : 'LJava8$1;'
  Access flags      : 0x1011 (PUBLIC FINAL SYNTHETIC)
  Superclass        : 'Ljava/lang/Object;'
  Interfaces        -
    #0              : 'LJava8$Logger;'
```

The presence of the `SYNTHETIC` flag means that the class was generated and the interfaces list includes `Java8$Logger`.

This class is now representing the lambda. If you look at its `log` method implementation, you might expect to find the missing lambda body.

```
…
[00026c] Java8$1.log:(Ljava/lang/String;)V
0000: invoke-static {v1}, LJava8;.lambda$main$0:(Ljava/lang/String;)V
0003: return-void
…
```

Instead, it invokes a static method on the original `Java8` class named `lambda$main$0`. Again, the original source didn't contain this method but it's present in the bytecode.

```
…
    #1              : (in LJava8;)
      name          : 'lambda$main$0'
      type          : '(Ljava/lang/String;)V'
      access        : 0x1008 (STATIC SYNTHETIC)
[0002a0] Java8.lambda$main$0:(Ljava/lang/String;)V
0000: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
0002: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
0005: return-void
```

The `SYNTHETIC` flag again confirms that this method was generated. And its bytecode contains the body of the lambda: a call to `System.out.println`. The reason that the lambda body is kept inside the original class is that it might access private members that the generated class wouldn't have access to.

All of the puzzle pieces for understanding how desugaring works are here. Seeing it in Dalvik bytecode, though, can be a bit dense and intimidating.


### Source Transformation

In order to better understand how desugaring works we can perform the transformation at the source code level. This is not how it actually works, but it's a useful exercise for learning both what happens but also reinforcing what we saw in the bytecode.

Once again, we start from the original program with a lambda.

```java
class Java8 {
  interface Logger {
    void log(String s);
  }

  public static void main(String... args) {
    sayHi(s -> System.out.println(s));
  }

  private static void sayHi(Logger logger) {
    logger.log("Hello!");
  }
}
```

First, the lambda body is moved to a sibling, package-private method.

```diff
   public static void main(String... args) {
-    sayHi(s -> System.out.println(s));
+    sayHi(s -> lambda$main$0(s));
   }
+
+  static void lambda$main$0(String s) {
+    System.out.println(s);
+  }
```

Then, a class is generated which implements the target interface and whose method body calls the lambda method.

```diff
   public static void main(String... args) {
-    sayHi(s -> lambda$main$0(s));
+    sayHi(new Java8$1());
   }
@@
 }
+
+class Java8$1 implements Java8.Logger {
+  @Override public void log(String s) {
+    Java8.lambda$main$0(s);
+  }
+}
```

Finally, because the lambda doesn't capture any state, a singleton instance is created and stored in a static `INSTANCE` variable.

```diff
   public static void main(String... args) {
-    sayHi(new Java8$1());
+    sayHi(Java8$1.INSTANCE);
   }
@@
 class Java8$1 implements Java8.Logger {
+  static final Java8$1 INSTANCE = new Java8$1();
+
   @Override public void log(String s) {
```

This results in a fully desugared source file that can be used on all API levels.

```java
class Java8 {
  interface Logger {
    void log(String s);
  }

  public static void main(String... args) {
    sayHi(Java8$1.INSTANCE);
  }

  static void lambda$main$0(String s) {
    System.out.println(s);
  }

  private static void sayHi(Logger logger) {
    logger.log("Hello!");
  }
}

class Java8$1 implements Java8.Logger {
  static final Java8$1 INSTANCE = new Java8$1();

  @Override public void log(String s) {
    Java8.lambda$main$0(s);
  }
}
```

If you actually look in the Dalvik bytecode for the generated lambda class it won't have a name like `Java8$1`. The real name will look something like `-$$Lambda$Java8$QkyWJ8jlAksLjYziID4cZLvHwoY`. The reason for the awkward naming and the advantages it brings are content for another post…


### Native Lambdas

When we used the `dx` tool to attempt to compile lambda-containing Java bytecode to Dalvik bytecode its error message indicated that this would only work with a minimum API of 26 or newer.

```
$ $ANDROID_HOME/build-tools/28.0.2/dx --dex --output . *.class
Uncaught translation error: com.android.dx.cf.code.SimException:
  ERROR in Java8.main:([Ljava/lang/String;)V:
    invalid opcode ba - invokedynamic requires --min-sdk-version >= 26
    (currently 13)
1 error; aborting
```

Thus, if you re-run D8 and specify `--min-api 26` it's reasonable to assume that "native" lambdas will be used and desugaring won't actually occur.

```
$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --min-api 26 \
    --output . \
    *.class
```

But if you dump the .dex file, you'll still find the `-$$Lambda$Java8$QkyWJ8jlAksLjYziID4cZLvHwoY` class was generated. Maybe it's a D8 bug?

To learn why desugaring _always_ occurs we need to look inside the Java bytecode of the `Java8` class.

```
$ javap -v Java8.class
class Java8 {
  public static void main(java.lang.String...);
    Code:
       0: invokedynamic #2, 0   // InvokeDynamic #0:log:()LJava8$Logger;
       5: invokestatic  #3      // Method sayHi:(LJava8$Logger;)V
       8: return
}
…
```

The output has been trimmed for readability, but inside the `main` method you'll see the `invokedynamic` bytecode at index `0`. The second argument to the bytecode is the value 0 which is the index of the associated bootstrap method. A bootstrap method is a bit of code that runs the first time that the bytecode is executed and it defines the behavior. The list of bootstrap methods are present at the bottom of the output.

```
…
BootstrapMethods:
  0: #27 invokestatic java/lang/invoke/LambdaMetafactory.metafactory:(
                        Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;
                        Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;
                        Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)
                        Ljava/lang/invoke/CallSite;
    Method arguments:
      #28 (Ljava/lang/String;)V
      #29 invokestatic Java8.lambda$main$0:(Ljava/lang/String;)V
      #28 (Ljava/lang/String;)V
```

In this case, the bootstrap method is called `metafactory` on the `java.lang.invoke.LambdaMetafactory` class. This class [lives in the JDK](https://docs.oracle.com/javase/8/docs/api/java/lang/invoke/LambdaMetafactory.html) and is responsible for creating anonymous classes on-the-fly at runtime for lambdas in a similar fashion to how D8 creates them at compile time.

If you look at the [Android documentation for `java.lang.invoke`](https://developer.android.com/reference/java/lang/invoke/package-summary) or the [AOSP source code for `java.lang.invoke`](https://android.googlesource.com/platform/libcore/+/master/ojluni/src/main/java/java/lang/invoke/), though, you'll notice this class isn't present in the Android runtime. This is why desguaring always happens at compile-time regardless of your minimum API level. The VM has the bytecode support for an equivalent to `invokedynamic`, but the JDK's built-in `LambdaMetafactory` is not available to use.


### Method References

In addition to lambdas, method references were added to the language in Java 8. They're an efficient way to create a lambda whose body points to an existing method.

The logger example in this post has been using a lambda body whose contents call an existing method, `System.out.println`. We can substitute the explicit lambda for a method reference to save some code.

```diff
   public static void main(String... args) {
-    sayHi(s -> System.out.println(s));
+    sayHi(System.out::println);
   }
```

This compiles with `javac` and dexes with D8 the same as the lambda version with one notable difference. When dumping the Dalvik bytecode, the body of the generated lambda class has changed.

```
[000268] -$$Lambda$1Osqr2Z9OSwjseX_0FMQJcCG_uM.log:(Ljava/lang/String;)V
0000: iget-object v0, v1, L-$$Lambda$1Osqr2Z9OSwjseX_0FMQJcCG_uM;.f$0:Ljava/io/PrintStream;
0002: invoke-virtual {v0, v2}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
0005: return-void
```

Instead of calling the generated `Java8.lambda$main$0` method which contains the call to `System.out.println`, the `log` implementation now invokes `System.out.println` directly.

The lambda class is also no longer a static singleton. Bytecode index `0000` above is reading an instance field for a `PrintStream` reference. This reference is `System.out` which is resolved at the call-site in `main` and passed into the constructor (which is named `<init>` in bytecode).

```
[0002bc] Java8.main:([Ljava/lang/String;)V
0000: sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;
0003: new-instance v0, L-$$Lambda$1Osqr2Z9OSwjseX_0FMQJcCG_uM;
0004: invoke-direct {v0, v1}, L-$$Lambda$1Osqr2Z9OSwjseX_0FMQJcCG_uM;.<init>:(Ljava/io/PrintStream;)V
0008: invoke-static {v0}, LJava8;.sayHi:(LJava8$Logger;)V
```

Performing the transformation at the source level again results in a straightforward transformation.

```diff
   public static void main(String... args) {
-    sayHi(System.out::println);
+    sayHi(new -$$Lambda$1Osqr2Z9OSwjseX_0FMQJcCG_uM(System.out));
   }
@@
 }
+
+class -$$Lambda$1Osqr2Z9OSwjseX_0FMQJcCG_uM implements Java8.Logger {
+  private final PrintStream ps;
+
+  -$$Lambda$1Osqr2Z9OSwjseX_0FMQJcCG_uM(PrintStream ps) {
+    this.ps = ps;
+  }
+
+  @Override public void log(String s) {
+    ps.println(s);
+  }
+}
```


### Interface Methods

The other significant language feature of Java 8 was the ability to have `static` and `default` methods in interfaces. Static methods on interfaces allow providing instance factories or other helpers directly on the interface type on which they operate. Default methods allow you to compatibly add new methods to interfaces which have default implementations.

```java
interface Logger {
  void log(String s);

  default void log(String tag, String s) {
    log(tag + ": " + s);
  }

  static Logger systemOut() {
    return System.out::println;
  }
}
```

Both of these new method types on interfaces are supported by D8's desugaring. Using the tools above it's possible to understand how these are desugared to work on all API levels. That investigation is left as an exercise for the reader.

It is worth noting, though, that both of these features are implemented natively in the Android VM as of API 24. As a result, unlike lambdas and method references, specifying `--min-api 24` to D8 will result in them not having to be desugared.


### Just Use Kotlin?

By this point, a large majority of readers will have thought of Kotlin in some capacity. Yes, Kotlin provides lambdas and method references for passing code as data. Yes, Kotlin provides default and static(-like) functions on interfaces. All of those features are actually implemented by `kotlinc` in exactly the same way that D8 desugars the Java 8 bytecode (modulo small implementation details).

Android's development toolchain and VM support of newer Java language features is still important even if you are writing 100% Kotlin code. New versions of Java bring more efficient constructs in both bytecode and in the VM that Kotlin can then take advantage of.

It's not unreasonable to think that Kotlin will stop supporting Java 6 and Java 7 bytecode at some point in the future. The [IntelliJ platform has moved to Java 8](https://blog.jetbrains.com/idea/2015/12/intellij-idea-16-eap-144-2608-is-out/) as of version 2016.1. Gradle 5.0 has moved to Java 8. The number of platforms running on older JVMs are dwindling. Without support for Java 8 bytecode and VM functionality, Android is in danger of becoming the largest ecosystem holding Kotlin's Java bytecode generation back. Thankfully D8 and ART are stepping up here to ensure that isn't the case.

### Desugaring APIs

Thus far this post has focused on the language features and bytecode of newer Java versions. The other major benefit of new Java versions are the new APIs that come with it. Java 8 brought a ton of new APIs such as streams, `Optional`, functional interfaces, `CompletableFuture`, and a new date/time API.

Going back to the original logger example, we can use the new date/time API in order to know when messages were logged.

```java
import java.time.*;

class Java8 {
  interface Logger {
    void log(LocalDateTime time, String s);
  }

  public static void main(String... args) {
    sayHi((time, s) -> System.out.println(time + " " + s));
  }

  private static void sayHi(Logger logger) {
    logger.log(LocalDateTime.now(), "Hello!");
  }
}
```

We can again compile this with `javac` and convert it to Dalvik bytecode with D8 which desugars it to run on all API levels.

```
$ javac *.java

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class
```

You can actually push this onto a phone or emulator to verify it works, something we didn't do with the previous examples.

```
$ adb push classes.dex /sdcard
classes.dex: 1 file pushed. 0.5 MB/s (1620 bytes in 0.003s)

$ adb shell dalvikvm -cp /sdcard/classes.dex Java8
2018-11-19T21:38:23.761 Hello
```

If your device runs API 26 or newer you will see a timestamp and the string "Hello!" as expected. But running it on a device with a version earlier than API 26 produces a very different result.

```
java.lang.NoClassDefFoundError: Failed resolution of: Ljava/time/LocalDateTime;
  at Java8.sayHi(Java8.java:13)
  at Java8.main(Java8.java:9)
```

D8 has desugared the new language feature of lambdas to work on all API levels but didn't do anything with the new API usage of `LocalDateTime`. This is disappointing because it means we only see _some_ of the benefits of Java 8, not all of them.

Developers can choose to bundle their own `Optional` class or use a standalone version of the date/time library called `ThreeTenBP` to work around this. But if you can manually rewrite your code to use versions bundled in your APK, why can't desugar in D8 do it for you?

It turns out that D8 already does this but only for a single API: `Throwable.addSuppressed`. This API is what allows the try-with-resources language feature of Java 7 to work on all versions of Android despite the API only being available from API 19.

All we need for the Java 8 APIs to work on all API levels then is a compatible implementation that we can bundle in the APK. It turns out the team that works on Bazel [have again already built this](https://blog.bazel.build/2018/07/19/java-8-language-features-in-android-apps.html). Their code that does the rewriting can't be used, but the standalone repackaging of these JDK APIs can be. All we need is for the D8 team to add support in their desugaring tool to do the rewriting. You can [star the D8 feature request](https://issuetracker.google.com/issues/114481425) on the Android issue tracker to convey your support.

----

While the desugaring of language features has been available in various forms for some time, the lack of API desugaring remains a large gap in our ecosystem. Until the day that the majority of apps can specify a minimum API of 26, the lack of API desugaring in Android's toolchain is holding back the Java library ecosystem. Libraries which support both Android and the JVM cannot use the Java 8 APIs that were introduced nearly 5 years ago!

And despite Java 8 language feature desugaring now being part of D8, it's not enabled by default. Developers must explicitly opt-in by specifying their source and target compatibility to Java 8. Android library authors can help force this trend by building and publishing their libraries using Java 8 bytecode (even if you don't use the language features).

D8 is being actively worked on and so the future still looks bright for Java language and API support. Even if you're solely a Kotlin user, it's important to maintain pressure on Android for support of new versions of Java for the better bytecodes and new APIs. And in some cases, D8 is actually ahead of the game for versions of Java beyond 8 which we'll explore in the next post.

_(This post was adapted from a part of my [Digging into D8 and R8](/digging-into-d8-and-r8) talk that was never presented. Watch the video and look out for future blog posts for more content like this.)_