---
title: 'R8 Optimization: Enum Ordinals and Names'
layout: post

categories: post
tags:
- Android
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

Enums are (and have always been!) a recommended way to model a fixed set of constants. Most commonly an enum only provides a set of possible constants and nothing more. But being full classes, enums can also carry helper methods and fields (both instance and static) or even implement interfaces.

A common optimization for enums in tools that perform whole-program optimization is to replace simple occurrences (i.e., those which don't have fields, methods, or interfaces) with integer values. However, there are other optimizations which are applicable to _all_ enums that are still available.


### Ordinal

Each enum constant has an `ordinal()` which returns its position in the list of all constants. Since the ordinal range is always [0, N), it can be used for indexing into other zero-based data structures such as arrays or even bits. The most common usage is actually by the Java compiler itself for `switch` statements over enums.

```java
enum Greeting {
  FORMAL {
    @Override String greet(String name) {
      return "Hello, " + name;
    }
  },
  INFORMAL {
    @Override String greet(String name) {
      return "Hey " + name + '!';
    }
  };

  abstract String greet(String name);

  static String type(Greeting greeting) {
    switch (greeting) {
      case FORMAL: return "formal";
      case INFORMAL: return "informal";
      default: throw new AssertionError();
    }
  }
}
```

The compiled bytecode reveals the hidden call to `ordinal()`.

```
[000a34] Greeting.type:(LGreeting;)Ljava/lang/String;
0000: invoke-virtual {v1}, LGreeting;.ordinal:()I
0003: move-result v1
 ⋮
```

If we call this method with one of the constants, an opportunity for optimization presents itself.

```java
public static void main(String... args) {
  System.out.println(Greeting.type(Greeting.INFORMAL));
}
```

As this is the only usage of `type` in our whole application, R8 inlines the method.

```
[000b60] Greeter.main:([Ljava/lang/String;)V
0000: sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;
0002: sget-object v0, LGreeting;.INFORMAL:LGreeting;
0004: invoke-virtual {v0}, LGreeting;.ordinal:()I
0007: move-result v0
 ⋮
0047: invoke-virtual {v1, v2}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
0050: return-void
```

Bytecode index 0002 looks up the `INFORMAL` enum constant and then 0004 - 0007 invokes its `oridinal()` method. This is now a wasteful operation since the ordinal of the constant is known at compile-time.

R8 detects when a constant lookup flows into a call to `ordinal()` and replaces the call and lookup with the correct integer value that the call would produce.

```diff
 [000b60] Greeter.main:([Ljava/lang/String;)V
 0000: sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;
-0002: sget-object v0, LGreeting;.INFORMAL:LGreeting;
-0004: invoke-virtual {v0}, LGreeting;.ordinal:()I
-0007: move-result v0
+0002: const/4 v0, #int 1
  ⋮
 0042: invoke-virtual {v1, v2}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
 0045: return-void
```

This constant value now flows into the `switch` statement which can be eliminated leaving only the desired branch.

```diff
 [000b60] Greeter.main:([Ljava/lang/String;)V
 0000: sget-object v1, Ljava/lang/System;.out:Ljava/io/PrintStream;
-0002: const/4 v0, #int 1
- ⋮
+0002: const-string v0, "informal"
 0004: invoke-virtual {v1, v0}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
 0007: return-void
```

Even though the language provides switches over an enum, it's implementation is all based on integers from the ordinal values. It's a simple optimization to replace calls to `ordinal()` on fixed constants, but it enables more advanced optimizations like branch elimination to apply where they otherwise could not.


### Name

In addition to `ordinal()`, each enum constant exposes its declared name through the `name()` method. The `toString()` will also return the declared name by default, but since that method can be overridden it's important to have a distinct `name()`.

```java
enum Greeting {
  FORMAL { /* … */ },
  INFORMAL { /* … */ };
  
  abstract String greet(String name);
  
  @Override public String toString() {
    return "Greeting(" + name().toLowercase(US) + ')';
  }
}
```

The value of `name()` is sometimes used for display, logging, or serialization.

```java
static void printGreeting(Greeting greeting, String name) {
  System.out.println(greeting.name() + ": " + greeting.greet(name));
}

public static void main(String... args) {
  printGreeting(Greeting.FORMAL, "Jake");
}
```

This program prints "FORMAL: Hello, Jake" when run. Once again, by virtue of only being called from one place, R8 inlines `printGreeting` into `main`.

```
[000474] Greeting.main:([Ljava/lang/String;)V
0000: sget-object v3, LGreeting;.FORMAL:LGreeting;
0002: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
0004: new-instance v1, Ljava/lang/StringBuilder;
0006: invoke-direct {v1}, Ljava/lang/StringBuilder;.<init>:()V
0009: invoke-virtual {v3}, LGreeting;.name:()Ljava/lang/String;
000c: move-result-object v2
 ⋮
0022: invoke-virtual {v1, v2}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
0025: return-void
```

Bytecode index 0000 looks up the `FORMAL` enum constant and then 0009 - 000c invokes its `name()` method. Just like `ordinal()`, this is a wasteful operation as the name of the constant is known at compile-time.

R8 again detects when a constant enum lookup flows into a call to `name()` and replaces the call and lookup with a string constant. If you read the [economics of generated code](/the-economics-of-generated-code/#string-duplication) post, it talked about the cost of generating new string constants. Thankfully, because these strings share their name with the name of the enum constant, we do not pay for a new string.

```diff
 [000474] Greeting.main:([Ljava/lang/String;)V
 0000: sget-object v3, LGreeting;.FORMAL:LGreeting;
 0002: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
 0004: new-instance v1, Ljava/lang/StringBuilder;
 0006: invoke-direct {v1}, Ljava/lang/StringBuilder;.<init>:()V
-0009: invoke-virtual {v3}, LGreeting;.name:()Ljava/lang/String;
-000c: move-result-object v2
+0009: const-string v2, "FORMAL"
  ⋮
 0020: invoke-virtual {v1, v2}, Ljava/io/PrintStream;.println:(Ljava/lang/String;)V
 0023: return-void
```

The lookup at bytecode index 0000 still occurs because the code needs to invoke the `greet` method, but the call to `name()` was eliminated.

This optimization won't enable other large optimizations like branch elimination to apply. But, since it produces a string, any [string operations](/r8-optimization-string-constant-operations/) that are done on the result of the `name()` call may also be performed at compile-time.

For enums without a `toString()` override, this optimization will also apply to calls to `toString()` which defaults to being the same as `name()`.

---

Both of these enum optimizations are small and really only work in the context of other R8 optimizations. Although, if it wasn't clear in this series by now, that's how most of these optimizations achieve their true power.

So far in this series I chose to highlight optimizations based on having found bugs in them or sometimes even suggesting them myself through the R8 issue tracker. But the two optimizations in this post are somewhat special because I actually managed to contribute these myself! I suspect we won't see much else of my contribution in the series, but it feels good to have at least played a small part.

In the next post we'll come back to the enum ordinal optimization because switch statements on enums are far more complicated than they seem. Stay tuned!