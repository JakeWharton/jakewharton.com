---
title: 'D8 Optimizations'
layout: post

categories: post
tags:
- Android
- D8
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

No, that's not a typo! While the optimizations in this series so far have been done by R8 during whole-program optimization, D8 can also perform some simple optimizations.

D8 [was introduced](/androids-java-8-support/) as the new Java-to-Dalvik bytecode compiler for Android. It handles backporting of Java 8 language features to work on Android (as well as [those of Java 9 and beyond](/androids-java-9-10-11-and-12-support/)). It also works around [vendor- and version-specific bugs](/avoiding-vendor-and-version-specific-vm-bugs/) in the platform.

That's what we've seen from D8 so far in the series, but it has two other responsibilities that we'll cover in this post and the next:

 1. Backporting methods to work on older API levels where they didn't exist.
 2. Performing local optimizations to reduce bytecode size and/or improve performance.

We'll cover API backporting in the next post in the series. For now, let's look at some of the local optimizations that D8 might perform.


### Switch Rewriting

The last two posts ([1](/r8-optimization-enum-ordinals-and-names/), [2](/r8-optimization-enum-switch-maps/)) have dealt with optimizing switch statements. Both have slightly lied about the bytecode that D8 and R8 produce for certain switch statements. Let's look at one of those examples again.

```java
enum Greeting {
  FORMAL, INFORMAL;
  
  static String greetingType(Greeting greeting) {
    switch (greeting) {
      case FORMAL: return "formal";
      case INFORMAL: return "informal";
      default: throw new AssertionError();
    }
  }
}
```

The full Java bytecode that was shown for `greetingType` used the `lookupswitch` bytecode which has offsets for where to jump when a value is matched.

```
static java.lang.String greetingType(Greeting);
  Code:
     0: getstatic     #2      // Field Main$1.$SwitchMap$Greeting:[I
     3: aload_0
     4: invokevirtual #3      // Method Greeting.ordinal:()I
     7: iaload
     8: lookupswitch  {
                   1: 36
                   2: 39
             default: 42
        }
    36: ldc           #4      // String formal
    38: areturn
    39: ldc           #5      // String informal
    41: areturn
    42: new           #6      // class java/lang/AssertionError
    45: dup
    46: invokespecial #7      // Method java/lang/AssertionError."<init>":()V
    49: athrow
```

The `tableswitch` Java bytecode was shown as being rewritten to `packed-switch` when converted to Dalvik bytecode.

```
[000584] Main.greetingType:(LGreeting;)Ljava/lang/String;
0000: sget-object v0, LMain$1;.$SwitchMap$Greeting:[I
0002: invoke-virtual {v2}, LGreeting;.ordinal:()I
0005: move-result v1
0006: aget v0, v0, v1
0008: packed-switch v0, 00000017
000b: new-instance v0, Ljava/lang/AssertionError;
000d: invoke-direct {v0}, Ljava/lang/AssertionError;.<init>:()V
0010: throw v0
0011: const-string v0, "formal"
0013: return-object v0
0014: const-string v0, "informal"
0016: return-object v0
0017: packed-switch-data (8 units)
```

If we actually compile and dex the above source file with D8, its Dalvik bytecode output is different.

```diff
 [0005f0] Main.greetingType:(LGreeting;)Ljava/lang/String;
 0000: sget-object v0, LMain$1;.$SwitchMap$Greeting:[I
 0002: invoke-virtual {v1}, LGreeting;.ordinal:()I
 0005: move-result v1
 0006: aget v0, v0, v1
-0008: packed-switch v0, 00000017
+0008: const/4 v1, #int 1
+0009: if-eq v0, v1, 0014
+000b: const/4 v1, #int 2
+000c: if-eq v0, v1, 0017
 000e: new-instance v0, Ljava/lang/AssertionError;
 0010: invoke-direct {v0}, Ljava/lang/AssertionError;.<init>:()V
 0013: throw v0
 0014: const-string v0, "formal"
 0016: return-object v0
 0017: const-string v0, "informal"
 0019: return-object v0
-0017: packed-switch-data (8 units)
```

Instead of a `packed-switch` at bytecode index 0008, there are a series of `if`/`else if`-like checks. Based on the indices, you might think this winds up producing a larger binary but it's actually the opposite. The original `packed-switch` is accompanied by a `packed-switch-data` bytecode that reports itself as being 8 units long. So the `packed-switch` version has a total cost of 26 bytecodes whereas the `if`/`else if` version only costs 20.

Rewriting switches to normal conditionals is only done when there is a bytecode savings. This depends on the number of `case` blocks, whether there's fallthrough, and whether or not the values are contiguous or not. D8 computes the cost of both forms and then chooses that which is smaller.


### String Optimizations

Back in February there was a post on [R8's string constants operations](/r8-optimization-string-constant-operations/). It showed an example from OkHttp where a call to `String.length` was made on a constant.

```java
static String patternHost(String pattern) {
  return pattern.startsWith(WILDCARD)
      ? pattern.substring(WILDCARD.length())
      : pattern;
}
```

When compiled with the old `dx` tool the output is a straightforward translation.

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

Bytecode index 0008 performs the `String.length` call on the constant loaded at index 0000.

With D8, however, this method call on a constant is detected and evaluated at compile-time to its corresponding numerical value.

```diff
 [0001a8] Test.patternHost:(Ljava/lang/String;)Ljava/lang/String;
 0000: const-string v0, "*."
 0002: invoke-virtual {v1, v0}, Ljava/lang/String;.startsWith:(Ljava/lang/String;)Z
 0005: move-result v0
 0006: if-eqz v0, 000d
-0008: invoke-virtual {v0}, Ljava/lang/String;.length:()I
-0011: move-result v1
+0008: const/4 v0, #int 2
 0009: invoke-virtual {v1, v0}, Ljava/lang/String;.substring:(I)Ljava/lang/String;
 000c: move-result-object v1
 000d: return-object v1
```

Removing a method call is not something that D8 or even R8 will normally do. This optimization is only safe to apply because `String` is a final class in the framework with well-defined behavior.

In the nine months since the original post, the number of methods on a string which can be optimized has grown substantially. Both D8 and R8 will compute `isEmpty()`, `startsWith(String)`, `endsWith(String)`, `contains(String)`, `equals(String)`, `equalsIgnoreCase(String)`, `contentEquals(String)`, `hashCode()`, `length()`, `indexOf(String)`, `indexOf(int)`, `lastIndexOf(String)`, `lastIndexOf(int)`, `compareTo(String)`, `compareToIgnoreCase(String)`, `substring(int)`, `substring(int, int)`, and `trim()` on a constant string. Obviously it's unlikely that most of these will apply without R8 inlining, but they're there when it does occur.


### Known Array Lengths

Just like how you might call `length()` on a constant string to maintain a single source of truth, it's not uncommon to see code call `length` on an array which has a constant size for the same reason.

Let's once again turn to OkHttp for a Kotlin example of this pattern.

```kotlin
private fun decodeIpv6(input: String, pos: Int, limit: Int): InetAddress? {
  val address = ByteArray(16)
  var b = 0

  var i = pos
  while (i < limit) {
    if (b == address.size) return null // Too many groups.
```

The use of `address.size` (which becomes a call to `length` in bytecode) prevents having to duplicate the 16 constant or extract it to a shared constant value. The downside is that each iteration of this parsing loop has resolve the array length as seen in output of `dx`.

```
[00020c] OkHttpKt.decodeIpv6:(Ljava/lang/String;II)Ljava/net/InetAddress;
0000: const/16 v5, #int 16
0002: new-array v0, v5, [B
0004: const/4 v1, #int 0
0005: const/4 v2, #int 0
0006: if-ge v2, v8, 0036
0008: array-length v6, v0
0009: if-ne v1, v6, 000b
 ⋮
```

The constant 16 is loaded into register v5 at bytecode index 0000 which is used as the array size at index 0002. The resulting array reference is stored in register v0. The loop then starts at index 0006 with the `i < limit` comparison. Inside the loop, v0's array length is loaded into v6 at index 0008 to be tested in the `if` at index 0009.

D8 recognizes that the `length` lookup is being done on an array reference which does not change and whose size is known at compile-time.

```diff
 [00020c] OkHttpKt.decodeIpv6:(Ljava/lang/String;II)Ljava/net/InetAddress;
 0000: const/16 v5, #int 16
 0002: new-array v0, v5, [B
 0004: const/4 v1, #int 0
 0005: const/4 v2, #int 0
 0006: if-ge v2, v8, 0036
-0008: array-length v6, v0
-0009: if-ne v1, v6, 000b
+0009: if-ne v1, v5, 000b
  ⋮
```

The call to `array-length` is removed and the `if` is rewritten to re-use register v5 which is the size that was used to create the array.

On its own this pattern is not overly common. Once again it plays well when R8 inlining comes into effect and a method checking `array.length` is inlined into a caller that declares a new array. 


---

Each of these optimizations are small. D8 can only perform an optimization when it has no externally-visible effect and does not change program behavior. That pretty much limits it to optimizations which occur inside of a single method body.

At runtime you cannot tell that a switch was rewitten to if/else conditionals. You cannot tell that a call to `length()` on a constant string was replaced with its equivalent constant value. You cannot tell that a call to `length` on an array initialized in the same method was replaced with the input size. Each of these optimizations (and the few others) that D8 is able to perform result in slightly smaller and more-efficient bytecode. And, of course, when you invoke the full power of R8, their impact is multiplied.

In the next post we'll start to cover how D8 backports new APIs on existing types to work on older API levels.