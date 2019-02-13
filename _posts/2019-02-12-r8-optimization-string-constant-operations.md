---
title: 'R8 Optimization: String Constant Operations'
layout: post

categories: post
tags:
- Android
- R8
---
 
> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

The [previous post in the series](/r8-optimization-value-assumption/) covered an R8 flag which allows you to specify the return value range of a field or method. R8 can use this to automatically remove conditionals against `SDK_INT` based on your app's minimum supported API level, for example. That can only happen because multiple R8 features are working together. This post (and the next few) will cover smaller optimizations of R8 which work best when combined with others.

Aside from the eight primitive types of Java, all of the other values your program interacts with are instances of classes whose data can only be manipulated at runtime. That is, all except for one type: strings. Strings are such a fundamental and ubiquitous type that they are given special treatment in the Java and Kotlin language, in Java bytecode, and in Dalvik bytecode. And because of that special treatment, tools like R8 can manipulate them at compile-time!


### Constant Pool and String Data

When you write a string literal in Java or Kotlin, the contents of that string are encoded in a special section of the bytecode. For Java bytecode it's called the _constant pool_. For Dalvik bytecode it's called the _string data section_. In addition to string literals which were present in the source code, strings for the names of types, methods, fields, and other structural elements are included in these sections.

When you look at the Java bytecode of a class file through `javap` as these posts have been doing, references to the constant pool use an octothorpe (`#`) followed by a number.

```
0: new           #2  // class java/lang/StringBuilder
3: dup
4: invokespecial #3  // Method java/lang/StringBuilder."<init>":()V
7: ldc           #4  // String A:
```

Helpful comments are included so that we don't have to manually consult the constant pool to figure out what each means.

If you invoke `javap` with the `-v` argument the constant pool will be included in the output.

```
Constant pool:
   #1 = Methodref          #9.#18         // java/lang/Object."<init>":()V
   #2 = Class              #19            // java/lang/StringBuilder
   #3 = Methodref          #2.#18         // java/lang/StringBuilder."<init>":()V
   #4 = String             #20            // A:
    ⋮ 
  #10 = Utf8               <init>
  #11 = Utf8               ()V
    ⋮ 
  #18 = NameAndType        #10:#11        // "<init>":()V
  #19 = Utf8               java/lang/StringBuilder
  #20 = Utf8               A:
```

\#4 is a `String` type whose data is at #20 which is a UTF-8 entry for "A:". This was one of the string literals from the source (taken from [the Java 9 string concat example](/androids-java-9-10-11-and-12-support/#string-concat)). If you look at #2 or #3, they're signatures for a `Class` and `Methodref` (method reference), respectively. Each uses one or more UTF-8 entries to create the signature it represents.

When using `dexdump` to look at Dalvik bytecode, the program doesn't show the string data section directly. Instead, strings are substituted into the bytecode output to make it easier to read.

```
0000: new-instance v0, Ljava/lang/StringBuilder; // type@0003
0002: invoke-direct {v0}, Ljava/lang/StringBuilder;.<init>:()V // method@0003
0005: const-string v1, "A: " // string@0002
```

Hints of the string data section are shown in the comments which follow each line. `string@0002` indicates this literal comes from index 2 in the string data section. The `type@0003` and `method@0003` hints point to separate sections of the dex which themselves eventually use the string data to create their signatures (similar to how the constant pool in the Java bytecode worked).


### String Operations

Performing string operations on literals isn't something that frequently happens in your source code. You wouldn't write something like `new User("OliveJakeHazel".substring(5, 9))` to create a `User` named "Jake". You would use `"Jake"` as the string literal without a `substring` call. One notable exception to this is computing the length of a string literal.

```java
static String patternHost(String pattern) {
  return pattern.startsWith(WILDCARD)
      ? pattern.substring(WILDCARD.length())
      : pattern;
}
```

This code is adapted from a real example inside [OkHttp](https://github.com/square/okhttp) where a string is tested for a prefix and then conditionally removed. The length is computed so that if the constant changes the value passed to `substring` remains correct.

Let's take a look at what Dalvik bytecode this example produces.

```
[0001a8] Test.patternHost:(Ljava/lang/String;)Ljava/lang/String;
0000: const-string v0, "*."
0002: invoke-virtual {v2, v0}, Ljava/lang/String;.startsWith:(Ljava/lang/String;)Z
0005: move-result v1
0006: if-eqz v1, 0010
0008: invoke-virtual {v0}, Ljava/lang/String;.length:()I
0011: move-result v1
0012: invoke-virtual {v2, v1}, Ljava/lang/String;.substring:(I)Ljava/lang/String;
000f: move-result-object v2
0010: return-object v2
```

In index `0000` to `0002`, the `WILDCARD` constant (whose value is the literal `"*."`) is loaded into register `v0` in order to call `startWith` on the parameter (in `v2`). Later, in index `0008` to `0011`, the length of `v0` is calculated and stored in `v1` so that it can be used to call `substring` on the parameter.

Since `WILDCARD` is a constant initialized with a string literal, its length is also a constant. Computing its length at runtime is a waste of time because it will always produce the same value. When the above code is compiled with R8, the call to `length()` on a constant is replaced with the value as determined at compile-time.

```
[0001a8] Test.patternHost:(Ljava/lang/String;)Ljava/lang/String;
0000: const-string v0, "*."
0002: invoke-virtual {v1, v0}, Ljava/lang/String;.startsWith:(Ljava/lang/String;)Z
0005: move-result v0
0006: if-eqz v0, 000d
0008: const/4 v0, #int 2
0009: invoke-virtual {v1, v0}, Ljava/lang/String;.substring:(I)Ljava/lang/String;
000c: move-result-object v1
000d: return-object v1
```

Index `0008` now loads the constant value of 2 which is immediately passed to the `substring` call. The bytecode gets the performance benefit of a hardcoded value without the maintenance burden of keeping the two values in sync in the source code.

And because this computation was trivial and removing the call to `length()` won't change the program's behavior, D8 will also perform this optimization!


### Inlining

Computing the length of a constant string isn't the only string operation that can happen at compile-time. Common string operations such as `startWith`, `indexOf`, and `substring` can all be computed provided that their arguments are also constants. While this is rare to find verbatim in source code, method inlining can create situations where this happens. 

```java
class Test {
  private static final String WILDCARD = "*.";

  private static String patternHost(String pattern) {
    return pattern.startsWith(WILDCARD)
        ? pattern.substring(WILDCARD.length())
        : pattern;
  }

  public static String canonicalHost(String pattern) {
    String host = patternHost(pattern);
    return HttpUrl.get("http://" + host).host();
  }

  public static void main(String... args) {
    String pattern = "*.example.com";
    String canonical = canonicalHost(pattern);
    System.out.println(canonical);
  }
}
```

Take this more complete example where the `main` method calls a public library method `canonicalHost` with a string literal. The `canonicalHost` library method delegates to `patternHost` which is a private library method. Because this program is so small both methods will ultimately be inlined into the `main` method.

We can pretend this inlining happened at the source-level to see how the code changes as the string optimizations apply.

```java
class Test {
  private static final String WILDCARD = "*.";

  public static void main(String... args) {
    String pattern = "*.example.com";
    String host = pattern.startsWith(WILDCARD)
        ? pattern.substring(WILDCARD.length())
        : pattern;
    String canonical = HttpUrl.get("http://" + host).host();
    System.out.println(canonical);
  }
}
```

R8's intermediate representation (IR) during compilation uses static single-assignment form (SSA) ([introduced in part 1 of the null analysis](/r8-optimization-null-data-flow-analysis-part-1/)) which allows it to, among other things, trace the origin of local variables. Despite `startsWith` operating on the variable `pattern`, that variable's origin can be traced to the string literal `"*.example.com"`. The argument to `startsWith`, `WILDCARD`, is also a string constant allowing the whole operation to be replaced with its result at compile-time.

```diff
 String pattern = "*.example.com";
-String host = pattern.startsWith(WILDCARD)
+String host = true
     ? pattern.substring(WILDCARD.length())
```

Dead-code elimination removes the impossible 'else' branch and the conditional.

```diff
 String pattern = "*.example.com";
-String host = true
-     ? pattern.substring(WILDCARD.length())
-     : pattern;
+String host = pattern.substring(WILDCARD.length());
 String canonical = HttpUrl.get("http://" + host).host();
```

The call to `length()` on a string constant is replaced with the constant integer value as demonstrated in the previous section.

```diff
 String pattern = "*.example.com";
-String host = pattern.substring(WILDCARD.length());
+String host = pattern.substring(2);
 String canonical = HttpUrl.get("http://" + host).host();
```

Compiling and dexing the original three-method example with R8 confirms that this is the final result.

```
$ javac -cp okhttp-3.13.1.jar Test.java

$ cat rules.txt
-keepclasseswithmembers class * {
  public static void main(java.lang.String[]);
}

$ java -jar r8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    --pg-conf rules.txt \
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[0001c0] Test.main:([Ljava/lang/String;)V
0000: const/4 v2, #int 2
0001: const-string v0, "*.example.com"
0003: invoke-virtual {v0, v2}, Ljava/lang/String;.substring:(I)Ljava/lang/String;
0006: move-result-object v2
0007: new-instance v0, Ljava/lang/StringBuilder;
0009: invoke-direct {v0}, Ljava/lang/StringBuilder;.<init>:()V
000c: const-string v1, "http://"
000e: invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
0011: invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
0014: invoke-virtual {v0}, Ljava/lang/StringBuilder;.toString:()Ljava/lang/String;
0017: move-result-object v2
0018: invoke-static {v2}, Lokhttp3/HttpUrl;.get:(Ljava/lang/String;)Lokhttp3/HttpUrl;
001b: move-result-object v2
001c: invoke-virtual {v2}, Lokhttp3/HttpUrl;.host:()Ljava/lang/String;
001f: move-result-object v2
0020: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
0022: invoke-virtual {v0, v2}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
0025: return-void
```

The `startsWith` check and conditional have been removed because inlining has made the receiver string available as a constant. Our dex file is a bit smaller and our program runs a bit faster now because this condition which always produced the same value was computed at compile-time.


### Money Left on the Table

Having `length()` and `startsWith()` replaced with a value computed at compile-time is a nice win. Other methods on `String` can be computed at compile-time such as `isEmpty()`, `contains()`, `endsWith()`, `equals()`, and `equalsIgnoreCase()`. Looking at the result above leaves me unsatisfied because optimizations were left on the table. Let's look at the final form as if it were source code and analyze what _didn't_ happen.

```java
String pattern = "*.example.com";
String host = pattern.substring(2);
String canonical = HttpUrl.get("http://" + host).host();
System.out.println(canonical);
```

The now-removed call to `startsWith` was able to be eliminated because the receiver (i.e., the target string) and argument were both known at compile-time. Looking at the above example, that condition holds true for the call to `substring`. It should have been eliminated.

```diff
-String pattern = "*.example.com";
-String host = pattern.substring(2);
+String host = "example.com";
 String canonical = HttpUrl.get("http://" + host).host();
```

The argument sent to `HttpUrl.get` is now the result of string concatenation of two string literals. The need to concatenate those at runtime should have been eliminated.

```diff
-String host = "example.com";
-String canonical = HttpUrl.get("http://" + host).host();
+String canonical = HttpUrl.get("http://example.com").host();
```

These optimizations are likely to be included in a future version of R8 but they're not as trivial as they might seem.

Every existing string optimization returns a primitive value such as a `boolean` or `int` which can be represented directly in the bytecode. As a result of those optimizations, it's possible for the string data section to shrink if a string becomes unused. In the example above, `WILDCARD` becomes unused since its only two uses (as an argument to `startsWith` and as a receiver for `length`) were replaced with primitives and so it does not appear in the final dex file.

Computing a substring or performing concatenation at compile-time has the potential to increase the size of the string data section. If the input strings are still used in other parts of the application they won't be eliminated. The new string, however, will always be added.

Doing these optimizations on the trivial program in this post removes 16 bytes of bytecode but adds 18 bytes of string data. In this case, because the input strings are not used anywhere else, an additional 20 bytes is removed for a net reduction of 18 bytes (ignoring the other parts of a dex).

In real-world applications it becomes less clear whether computing these is the correct choice. For now, these optimizations are not performed.

---

When combined with inlining, R8's string optimizations help eliminate dead code and improve runtime performance when working with string literals. To track updates to and show support for new `String` methods being computed at compile-time star [issuetracker.google.com/issues/119364907](https://issuetracker.google.com/issues/119364907). For string concatenation star [issuetracker.google.com/issues/114002137](https://issuetracker.google.com/issues/114002137).

The next post in the series will look at an optimization that creates string literals at compile-time which otherwise would need to be created at runtime.

_(This post was adapted from a part of my [Digging into D8 and R8](/digging-into-d8-and-r8) talk that was never presented. Watch the video and look out for future blog posts for more content like this.)_
