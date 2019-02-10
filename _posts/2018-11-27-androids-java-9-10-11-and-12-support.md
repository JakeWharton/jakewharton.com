---
title: Android's Java 9, 10, 11, and 12 Support
layout: post

categories: post
tags:
- Android
- Java
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/).

The first post in this series [explored Android's Java 8 support](/androids-java-8-support). Having support for the language features and APIs of Java 8 is table stakes at this point. We're not quite there with the APIs yet, sadly, but D8 has us covered with the language features. There's a future promise for the APIs which is essential for the health of the ecosystem.

A lot of the reaction to the previous post echoed that Java 8 is quite old. The rest of the Java ecosystem is starting to move to Java 11 (being the first long-term supported release after 8) after having toyed with Java 9 and 10. I was _hoping_ for that reaction because I mostly wrote that post so that I could set up this one.

With Java releases happening more frequently, Android's yearly release schedule and delayed uptake of newer language features and APIs _feels_ more painful. But is it actually the case that we're stuck with those of Java 8? Let's take a look at the Java releases beyond 8 and see how the Android toolchain fares.


### Java 9

The last release on the 2 - 3 year schedule, Java 9 contains a few new language features. None of them are major like lambdas were. Instead, this release focused on cleaning up some of the sharp edges on existing features.


#### Concise Try With Resources

Prior to this release the try-with-resources construct required that you define a local variable (such as `try (Closeable bar = foo.bar())`). But if you already have a `Closeable`, defining a new variable is redundant. As such, this release allows you to omit declaring a new variable if you already have an effectively-final reference.

```java
import java.io.*;

class Java9TryWithResources {
  String effectivelyFinalTry(BufferedReader r) throws IOException {
    try (r) {
      return r.readLine();
    }
  }
}
```

This feature is implemented entirely in the Java compiler so D8 is able to dex it for Android.

```
$ javac *.java

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ ls
Java9TryWithResources.java  Java9TryWithResources.class  classes.dex
```

Unlike the lambdas or static interface methods of Java 8 which required special desugaring, this Java 9 feature becomes available to all API levels for free.


#### Anonymous Diamond

Java 7 introduced the diamond operator which allowed omitting a generic type from the initializer if it could be inferred from the variable type.

```java
List<String> strings = new ArrayList<>();
```

This cut down on redundant declarations, but it wasn't available for use on anonymous classes. With Java 9 that is now supported.

```java
import java.util.concurrent.*;

class Java9AnonymousDiamond {
  Callable<String> anonymousDiamond() {
    Callable<String> call = new Callable<>() {
      @Override public String call() {
        return "Hey";
      }
    };
    return call;
  }
}
```

Once again this is entirely implemented in the Java compiler so the resulting bytecode is as if `String` was explicitly specified.

```
$ javac *.java

$ javap -c *.class
class Java9AnonymousDiamond {
  java.util.concurrent.Callable<java.lang.String> anonymousDiamond();
    Code:
       0: new           #7  // class Java9AnonymousDiamond$1
       3: dup
       4: aload_0
       5: invokespecial #8  // Method Java9AnonymousDiamond$1."<init>":(LJava9AnonymousDiamond;)V
       8: areturn
}

class Java9AnonymousDiamond$1 implements java.util.concurrent.Callable<java.lang.String> {
  final Java9AnonymousDiamond this$0;

  Java9AnonymousDiamond$1(Java9AnonymousDiamond);
    Code:
       0: aload_0
       1: aload_1
       2: putfield      #1  // Field this$0:LJava9AnonymousDiamond;
       5: aload_0
       6: invokespecial #2  // Method java/lang/Object."<init>":()V
       9: return

  public java.lang.String call();
    Code:
       0: ldc           #3  // String Hey
       2: areturn
}
```

Because there is nothing interesting in the bytecode, D8 handles this without issue.

```
$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ ls
Java9AnonymousDiamond.java  Java9AnonymousDiamond.class  Java9AnonymousDiamond$1.class  classes.dex
```

Yet another language feature available to all API levels for free.


#### Private Interface Methods

Interfaces with multiple static or default methods can often lead to duplicated code in their bodies. If these methods were part of a class and not an interface private helper functions could be extracted. Java 9 adds the ability for interfaces to contain private methods which are only accessible to its static and default methods.

```java
interface Java9PrivateInterface {
  static String hey() {
    return getHey();
  }

  private static String getHey() {
    return "hey";
  }
}
```

This is the first language feature that requires some kind of support. Prior to this release, the `private` modifier was not allowed on an interface member. Since D8 is already responsible for desugaring default and static methods, private methods were straightforward to include using the same technique.

```
$ javac *.java

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ ls
Java9PrivateInterface.java  Java9PrivateInterface.class  classes.dex
```

Static and default methods are supported natively in ART as of API 24. When you pass `--min-api 24` for this example, the static method is not desugared. Curiously, though, the private static method is also not desugared.

```
$ $ANDROID_HOME/build-tools/28.0.2/dexdump -d classes.dex
Class #1            -
  Class descriptor  : 'LJava9PrivateInterface;'
  Access flags      : 0x0600 (INTERFACE ABSTRACT)
  Superclass        : 'Ljava/lang/Object;'
  Direct methods    -
    #0              : (in LJava9PrivateInterface;)
      name          : 'getHey'
      type          : '()Ljava/lang/String;'
      access        : 0x000a (PRIVATE STATIC)
00047c:                 |[00047c] Java9PrivateInterface.getHey:()Ljava/lang/String;
00048c: 1a00 2c00       |0000: const-string v0, "hey"
000490: 1100            |0002: return-object v0
```

We can see that the `getHey()` method's access flags still contain both `PRIVATE` and `STATIC`. If you add a `main` method which calls `hey()` and push this to a device it will actually work. Despite being a feature added in Java 9, ART allows private interface members since API 24!

Those are all the language features of Java 9 and they all already work on Android. How about that.

The APIs of Java 9, though, are not yet included in the Android SDK. A new process API, var handles, a version of the Reactive Streams interfaces, and collection factories are just some of those which were added. Since libcore (which contains implementation of `java.*`) and ART are developed in AOSP, we can peek and see that work is already underway towards supporting Java 9. Once included included in the SDK, some of its APIs will be candidates for desugaring to all API levels.


#### String Concat

The new language features and APIs of a Java release tend to be what we talk about most. But each release is also an opportunity to optimize the bytecode which is used to implement a feature. Java 9 brought an optimization to a ubiquitous language feature: string concatenation.

```java
class Java9Concat {
  public static String thing(String a, String b) {
    return "A: " + a + " and B: " + b;
  }
}
```

If we take this fairly innocuous piece of code and compile it with Java 8 the resulting bytecode will use a `StringBuilder`.

```
$ java -version
java version "1.8.0_192"
Java(TM) SE Runtime Environment (build 1.8.0_192-b12)
Java HotSpot(TM) 64-Bit Server VM (build 25.192-b12, mixed mode)

$ javac *.java

$ javap -c *.class
class Java9Concat {
  public static java.lang.String thing(java.lang.String, java.lang.String);
    Code:
       0: new           #2  // class java/lang/StringBuilder
       3: dup
       4: invokespecial #3  // Method java/lang/StringBuilder."<init>":()V
       7: ldc           #4  // String A:
       9: invokevirtual #5  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      12: aload_0
      13: invokevirtual #5  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      16: ldc           #6  // String  and B:
      18: invokevirtual #5  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      21: aload_1
      22: invokevirtual #5  // Method java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
      25: invokevirtual #7  // Method java/lang/StringBuilder.toString:()Ljava/lang/String;
      28: areturn
}
```

The bytecode contains the code we otherwise would have written if the language didn't allow simple concatenation.

If we change the compiler to Java 9, however, the result is very different.

```
$ java -version
java version "9.0.1"
Java(TM) SE Runtime Environment (build 9.0.1+11)
Java HotSpot(TM) 64-Bit Server VM (build 9.0.1+11, mixed mode)

$ javac *.java

$ javap -c *.class
class Java9Concat {
  public static java.lang.String thing(java.lang.String, java.lang.String);
    Code:
       0: aload_0
       1: aload_1
       2: invokedynamic #2,  0  // InvokeDynamic #0:makeConcatWithConstants:(
                                                      Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
       7: areturn
}
```

The entire `StringBuilder` usage has been replaced with a single `invokedynamic` bytecode! The behavior here is similar to how [native lambdas work on the JVM](/androids-java-8-support/#native-lambdas) which was discussed in the last post.

At runtime, on the JVM, the [JDK class `StringConcatFactory`](https://docs.oracle.com/javase/9/docs/api/java/lang/invoke/StringConcatFactory.html#makeConcatWithConstants-java.lang.invoke.MethodHandles.Lookup-java.lang.String-java.lang.invoke.MethodType-java.lang.String-java.lang.Object...-) is responsible for returning a block of code which can efficiently concatenate the arguments and constants together. This allows the implementation to change over time without the code having to be recompiled. It also means that the `StringBuilder` can be pre-sized more accurately since the argument's lengths can be queried.

If you want to learn more about why this change was made, [Aleksey Shipilëv gave a great presentation](https://www.youtube.com/watch?v=wIyeOaitmWM) on the motivations, implementation, and resulting benchmarks of the change.

Since the Android APIs don't yet include anything from Java 9, there is no `StringConcatFactory` available at runtime. Thankfully, just like it did for `LambdaMetafactory` and lambdas, D8 is able to desugar `StringConcatFactory` for concatenations.

```
$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ $ANDROID_HOME/build-tools/28.0.2/dexdump -d classes.dex
[000144] Java9Concat.thing:(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
0000: new-instance v0, Ljava/lang/StringBuilder;
0002: invoke-direct {v0}, Ljava/lang/StringBuilder;.<init>:()V
0005: const-string v1, "A: "
0007: invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
000a: invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
000d: const-string v2, " and B: "
000f: invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
0012: invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
0015: invoke-virtual {v0}, Ljava/lang/StringBuilder;.toString:()Ljava/lang/String;
0018: move-result-object v2
0019: return-object v2
```

This means that all of the language features of Java 9 can be used on all API levels of Android despite changes in the bytecode that the Java compiler emits.

But Java is now on a six-month release schedule making Java 9 actually two versions old. Can we keep it going with newer versions?


### Java 10

The only language feature of Java 10 was called local-variable type inference. This allows you to omit the type of local variable by replacing it with `var` when that type can be inferred.

```java
import java.util.*;

class Java10 {
  List<String> localVariableTypeInferrence() {
    var url = new ArrayList<String>();
    return url;
  }
}
```

This is another feature implemented entirely in the Java compiler.

```
$ javac *.java

$ javap -c *.class
Compiled from "Java10.java"
class Java10 {
  java.util.List<java.lang.String> localVariableTypeInferrence();
    Code:
       0: new           #2  // class java/util/ArrayList
       3: dup
       4: invokespecial #3  // Method java/util/ArrayList."<init>":()V
       7: areturn
}
```

No new bytecodes or runtime APIs are required for this feature to work and so it can be used for Android just fine.

Of course, like the versions of Java before it, there are new APIs in this release such as `Optional.orElseThrow`, `List.copyOf`, and `Collectors.toUnmodifiableList`. Once added to the Android SDK in a future API level, these APIs can be trivially desugared to run on all API levels.


### Java 11

Local-variable type inference was enhanced in Java 11 to support its use on lambda variables. You don't see types used in lambda parameters often so a lot of people don't even know this syntax exists. This is useful when you need to provide an explicit type to help type inference or when you want to use a type-annotation on the parameter.

```java
import java.util.function.*;

@interface NonNull {}

class Java11 {
  void lambdaParameterTypeInferrence() {
    Function<String, String> func = (@NonNull var x) -> x;
  }
}
```

Just like Java 10's local-variable type inference this feature is implemented entirely in the Java compiler allowing it to work on Android.

New APIs in Java 11 include a bunch of new helpers on `String`, `Predicate.not`, and null factories for `Reader`, `Writer`, `InputSteam`, and `OutputStream`. Nearly all of the API additions in this release could be trivially desugared once available.

A major API addition to Java 11 is the [new HTTP client, `java.net.http`](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/package-summary.html). This client was previously available experimentally in the `jdk.incubator.http` package since Java 9. This is a very large API surface and implementation which leverages `CompletableFuture` extensively. It will be interesting to see whether or not this even lands in the Android SDK let alone is available via desugaring.


#### Nestmates

Like Java 9 and its string concatenation bytecode optimization, Java 11 took the opportunity to fix a long-standing disparity between Java's source code and its class files and the JVM: nested classes.

In Java 1.1, nested classes were added to the language but not the class specification or JVM. In order to work around the lack of support in class file, nesting classes in a source file instead creates sibling classes which use a naming convention to convey nesting.

```java
class Outer {
  class Inner {}
}
```

Compiling this with Java 10 or earlier will produce two class files from a single source file.

```
$ java -version
java version "10" 2018-03-20
Java(TM) SE Runtime Environment 18.3 (build 10+46)
Java HotSpot(TM) 64-Bit Server VM 18.3 (build 10+46, mixed mode)

$ javac *.java

$ ls
Outer.java  Outer.class  Outer$Inner.class
```

As far as the JVM is concerned, these classes have no relationship except that they exist in the same package.

This illusion mostly works. Where it starts to break down is when one of the classes needs to access something that is private in the other.

```java
class Outer {
  private String name;

  class Inner {
    String sayHi() {
      return "Hi, " + name + "!";
    }
  }
}
```

When these classes are made siblings, `Outer$Inner.sayHi()` is unable to access `Outer.name` because it is private to another class.

In order to work around this problem and maintain the nesting illusion, the Java compiler adds a package-private _synthetic accessor method_ for any member accessed across this boundary.

```diff
 class Outer {
   private String name;
+
+  String access$000() {
+    return name;
+  }

   class Inner {
     String sayHi() {
-      return "Hi, " + name + "!";
+      return "Hi, " + access$000() + "!";
     }
```

This is visible in the compiled class file for `Outer`.

```
$ javap -c -p Outer.class
class Outer {
  private java.lang.String name;

  static java.lang.String access$000(Outer);
    Code:
       0: aload_0
       1: getfield      #1  // Field name:Ljava/lang/String;
       4: areturn
}
```

Historically this has been at most a small annoyance on the JVM. For Android, though, these synthetic accessor methods contribute to the method count in our dex files, increase APK size, slow down class loading and verification, and degrade performance by turning a field lookup into a method call!

In Java 11, the class file format was updated to introduce the concept of _nests_ to describe these nesting relationships.

```
$ java -version
java version "11.0.1" 2018-10-16 LTS
Java(TM) SE Runtime Environment 18.9 (build 11.0.1+13-LTS)
Java HotSpot(TM) 64-Bit Server VM 18.9 (build 11.0.1+13-LTS, mixed mode)

$ javac *.java

$ javap -v -p *.class
class Outer {
  private java.lang.String name;
}
NestMembers:
  Outer$Inner

class Outer$Inner {
  final Outer this$0;

  Outer$Inner(Outer);
    Code: …

  java.lang.String sayHi();
    Code: …
}
NestHost: class Outer
```

The output here has been trimmed significantly, but the two class files are still produced except without an `access$000` in `Outer` and with new `NestMembers` and `NestHost` attributes. These allow the VM to enforce a level of access control between package-private and private called _nestmates_. As a result, `Inner` can directly access `Outer`'s `name` field.

ART does not understand the concept of nestmates so it needs to be desugared back into synthetic accessor methods.

```
$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class
Compilation failed with an internal error.
java.lang.UnsupportedOperationException
  at com.android.tools.r8.org.objectweb.asm.ClassVisitor.visitNestHostExperimental(ClassVisitor.java:158)
  at com.android.tools.r8.org.objectweb.asm.ClassReader.accept(ClassReader.java:541)
  at com.android.tools.r8.org.objectweb.asm.ClassReader.accept(ClassReader.java:391)
  at com.android.tools.r8.graph.JarClassFileReader.read(JarClassFileReader.java:107)
  at com.android.tools.r8.dex.ApplicationReader$ClassReader.lambda$readClassSources$1(ApplicationReader.java:231)
  at java.base/java.util.concurrent.ForkJoinTask$AdaptedCallable.exec(ForkJoinTask.java:1448)
  at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:290)
  at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1020)
  at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1656)
  at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1594)
  at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:177)
```

Unfortunately, at the time of writing, this does not work. The version of ASM, the library used to read Java class files, predates the final implementation of nestmates. Beyond that, though, D8 does not support desugaring of nest mates. You can [star the D8 feature request](https://issuetracker.google.com/issues/116628246) on the Android issue tracker to convey your support for this feature.

Without support for desugaring nestmates it is currently impossible to use Java 11 for Android. Even if you avoid accessing things across the nested boundary, the mere presence of nesting will fail to compile.

Without the APIs from Java 11 in the Android SDK, its single language feature of lambda parameter type inference isn't compelling. For now, Android developers are not missing anything by being stuck on Java 10. That is, until we start looking forward…


### Java 12

With a release date of March 2019, Java 12 is quickly approaching. The language features and APIs of this release have been in development for a few months already. Through early-access builds, we can download and experiment with these today.

In the current EA build, number 20, there are two new language features available: expression switch and string literals.

```java
class Java12 {
  static int letterCount(String s) {
    return switch (s) {
      case "one", "two" -> 3;
      case "three" -> 5;
      default -> s.length();
    };
  }

  public static void main(String... args) {
    System.out.println(`
 __        ______    ______   ______   ______    ______    ______    
/\ \      /\  ___\  /\__  _\ /\__  _\ /\  ___\  /\  == \  /\  ___\   
\ \ \____ \ \  __\  \/_/\ \/ \/_/\ \/ \ \  __\  \ \  __<  \ \___  \  
 \ \_____\ \ \_____\   \ \_\    \ \_\  \ \_____\ \ \_\ \_\ \/\_____\ 
  \/_____/  \/_____/    \/_/     \/_/   \/_____/  \/_/ /_/  \/_____/
`);
    System.out.println("three: " + letterCount("three"));
  }
}
```

Once again, both of these features are implemented entirely as part of the Java compiler without any new bytecodes or APIs.

```
$ java -version
openjdk version "12-ea" 2019-03-19
OpenJDK Runtime Environment (build 12-ea+20)
OpenJDK 64-Bit Server VM (build 12-ea+20, mixed mode, sharing)

$ javac *.java

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ ls
Java12.java  Java12.class  classes.dex
```

We can push this to a device to ensure that it actually works at runtime.

```
$ adb push classes.dex /sdcard
classes.dex: 1 file pushed. 0.6 MB/s (1792 bytes in 0.003s)

$ adb shell dalvikvm -cp /sdcard/classes.dex Java12

 __        ______    ______   ______   ______    ______    ______
/\ \      /\  ___\  /\__  _\ /\__  _\ /\  ___\  /\  == \  /\  ___\
\ \ \____ \ \  __\  \/_/\ \/ \/_/\ \/ \ \  __\  \ \  __<  \ \___  \
 \ \_____\ \ \_____\   \ \_\    \ \_\  \ \_____\ \ \_\ \_\ \/\_____\
  \/_____/  \/_____/    \/_/     \/_/   \/_____/  \/_/ /_/  \/_____/

three: 5
```

This works because the bytecode for expression switch is the same as the "regular" switch we would otherwise write with an uninitialized local, `case` blocks with `break`, and a separate `return` statement. And a multi-line string literal is just a string with newlines in it, something we've been able to do with escape characters forever.

As with all the other releases covered, there will be new APIs in Java 12 and it's the same story as before. They'll need added to the Android SDK and evaluated for desugaring capability.

Hopefully by the time Java 12 is actually released D8 will have implemented desugaring for Java 11's nestmates. Otherwise the pain of being stuck on Java 10 will go up quite a bit!


---

Java 8 language features are here and desugaring of its APIs are coming ([star the issue!](https://issuetracker.google.com/issues/114481425)). As the larger Java ecosystem moves forward to newer versions, it's reassuring that every language feature between 8 and 12 is already available on Android.

With Java 9 work seemingly happening in AOSP (cross your fingers for Android P+1), hopefully we'll have a new batch of APIs in the summer as candidates for desugaring. Once that lands, the smaller releases of Java will hopefully yield faster integration into the Android SDK.

Despite this, the end advice remains the same as in the last post. It's vitally important to maintain pressure on Android for supporting the new APIs and VM features from newer versions of Java. Without APIs being integrated into the SDK they can't (easily) be made available for use via desugaring. Without VM features being integrated into ART D8 bears a desugaring burden for all API levels instead of only to provide backwards compatibility.

Before these posts move on to talk about R8, the optimizing version of D8, the next one will cover how D8 works around version-specific and vendor-specific bugs in the VM.

_(This post was adapted from a part of my [Digging into D8 and R8](/digging-into-d8-and-r8) talk that was never presented. Watch the video and look out for future blog posts for more content like this.)_
