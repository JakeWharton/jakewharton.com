---
title: 'R8 Optimization: Enum Switch Maps'
layout: post

categories: post
tags:
- Android
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

The previous post on R8 covered [enum ordinals](/r8-optimization-enum-ordinals-and-names/#ordinal) which then allowed branch elimination to apply to a switch statement. In that post, the full bytecode for `switch` on an enum was omitted because there's actually more to the optimization.

Let's start with a simple enum and a switch over its contents in two separate source files (this will be important later).

```java
enum Greeting {
  FORMAL, INFORMAL
}
```

```java
class Main {
  static String greetingType(Greeting greeting) {
    switch (greeting) {
      case FORMAL: return "formal";
      case INFORMAL: return "informal";
      default: throw new AssertionError();
    }
  }

  public static void main(String... args) {
    System.out.println(greetingType(Greeting.INFORMAL));
  }
}
```

If we compile and run these files the output is as expected.

```
$ javac Greeting.java Main.java
$ java -cp . Main
informal
```

The bytecode in [the previous post](/r8-optimization-enum-ordinals-and-names/#ordinal) showed that the compiler produces a call to `ordinal()` which is then used in the switch. But if that was all that the compiler did, re-ordering the constants of `Greeting` would break the output of `Main`.

```diff
 enum Greeting {
-  FORMAL, INFORMAL
+  INFORMAL, FORMAL
 }
```

After changing the constant order, we can recompile _only_ `Greeting.java` and yet the application still produces the correct output.

```
$ javac Greeting.java
$ java -cp . Main
informal
```

If the bytecode was only relying on the value of `ordinal()`, this code would have produced "formal". 

### Into The Bytecode

To understand how this works we can look at the Java bytecode of `greetingType`.

```
$ javap -c Main.class
class Main {
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
}
```

Let's break the contents down. The first bytecode of this method has a lot of information to unpack:

```
0: getstatic     #2      // Field Main$1.$SwitchMap$Greeting:[I
```

This looks up a static field on the class `Main$1` with the name `$SwitchMap$Greeting` and the type `int[]`. We obviously did not write this class or field, so it must have been generated automatically by `javac`.

The next two bytecodes perform the call to `ordinal()` on the method argument.

```
3: aload_0
4: invokevirtual #3      // Method Greeting.ordinal:()I
```

Java bytecode is stack-based, so the `int[]` result of `getstatic` and the `int` value of `ordinal()` both remain on the stack. (If you don't understand how a stack-based machine works, you can watch [this presentation](/sinking-your-teeth-into-bytecode/) for an introduction.) The next instruction uses that `int[]` and `int` as its operands.

```
7: iaload
```

This "integer array load" instruction looks up a value in the `int[]` at the index returned by `ordinal()`. The rest of the bytecodes of the method are a "normal" switch statement which uses the value from the array as its input.

### Switch Maps

It's pretty clear that this `$SwitchMap$Greeting` array is the mechanism which allows our code to continue to work despite the ordinals changing their value. So how does it work?

When compiled, each `case` of the `switch` is assigned one-based index. The `default` branch is assigned zero.

```java
switch (greeting) {
  case FORMAL: ...   // <-- index 1
  case INFORMAL: ... // <-- index 2
  default: ...       // <-- index 0
}
```

The `$SwitchMap$Greeting` array is populated at runtime in the static initializer of `Main$1`. The empty `int[]` is created first and assigned to the `$SwitchMap$Greeting` field.

```
0: invokestatic  #1      // Method Greeting.values:()[LGreeting;
3: arraylength
4: newarray      int
6: putstatic     #2      // Field $SwitchMap$Greeting:[I
```

The length of this array is the same as the number of constants (which might not match the number of `case` blocks). This is important since ordinals are used as an index into this array.

The next bytecodes are repeated for each constant used in the switch statement.

```
 9: getstatic     #2      // Field $SwitchMap$Greeting:[I
12: getstatic     #3      // Field Greeting.FORMAL:LGreeting;
15: invokevirtual #4      // Method Greeting.ordinal:()I
18: iconst_1
19: iastore
```

The ordinal of `FORMAL`, the first `case` subject, is used as the offset in the array where its corresponding switch index value of 1 is stored. The same is done for the ordinal of `INFORMAL` and the value 2. This `int[]` effectively creates a map from the ordinals which may change to a fixed set of integer values which will not.

<a href="/static/post-image/switch-map@2x.png">
  <img
    class="center-block"
    src="/static/post-image/switch-map.png"
    srcset="/static/post-image/switch-map.png 1x,
            /static/post-image/switch-map@2x.png 2x"
    alt="Diagram showing the switch map working when the ordinals are changed."
    />
</a>

By using this map, the switch statement can remain stable even when we re-arrange the constants of `Greeting`.

### The Optimization

The switch map indirection created by `javac` is useful when the enum may be recompiled separately from the callers. Android applications are packaged as a single unit, so the indirection is nothing but wasted binary size and runtime overhead.

Running D8 on the class files from above shows that the indirection is maintained.

```
$ java -jar $R8_HOME/build/libs/d8.jar \
      --lib $ANDROID_HOME/platforms/android-29/android.jar \
      --release \
      --output . \
      *.class

$ $ANDROID_HOME/build-tools/29.0.2/dexdump -d classes.dex
 ⋮
[00040c] Main.greetingType:(LGreeting;)Ljava/lang/String;
0000: sget-object v0, LMain$1;.$SwitchMap$Greeting:[I
0002: invoke-virtual {v1}, LGreeting;.ordinal:()I
0005: move-result v1
0006: aget v1, v0, v1
0008: packed-switch v1, 00000024
 ⋮
```

R8, however, performs whole-program analysis and optimization. There's no point for it to retain this indirection since the enum cannot change independently of the switch.

```diff
 [00040c] Main.greetingType:(LGreeting;)Ljava/lang/String;
-0000: sget-object v0, LMain$1;.$SwitchMap$Greeting:[I
 0000: invoke-virtual {v1}, LGreeting;.ordinal:()I
 0003: move-result v1
-0006: aget v1, v0, v1
 0004: packed-switch v1, 00000024
```

The branches of the switch are rewritten to account for the fact that the input now uses the zero-based ordinal directly instead of the one-based values from the switch map. With the `Main$1` class and its array being no longer referenced, it is eliminated like normal dead code.

Only with this indirection removed can the [enum ordinal optimization](/r8-optimization-enum-ordinals-and-names/) from the previous post result in eliminating the switch. Otherwise, the ordinal value would flow into the `int[]` as an index which is not safe to eliminate in the general case.

#### Kotlin

An enum used in a Kotlin `when` will also produce a similar indirection for the same reasons.

```kotlin
val Greeting.type get() = when (this) {
  Greeting.FORMAL -> "formal"
  Greeting.INFORMAL -> "informal"
}
```

When compiled, the Java bytecode shows a similar mechanism but with different names.

```
$ javap -c MainKt
public final class MainKt {
  public static final java.lang.String getType(Greeting);
    Code:
      0: aload_0
      1: getstatic     #21     // Field MainKt$WhenMappings.$EnumSwitchMapping$0:[I
      4: swap
      5: invokevirtual #27     // Method Greeting.ordinal:()I
      8: iaload
      9: tableswitch   {
                    1: 36
                    2: 41
              default: 46
         }
     ⋮
```

The generated class is suffixed with `$WhenMappings` instead of an arbitrary integer and the array is named `$EnumSwitchMapping$0`.

R8 initially did not detect Kotlin mappings because of these slightly different names. Version 1.6 of R8 (included in AGP 3.6) will correctly detect and eliminate them.

---

Switch map elimination is a nice win for binary size and runtime performance. More importantly, by removing an indirection between the input to a switch and its branching logic, other optimizations like turning calls to `ordinal()` into a constant can result in branch elimination.

More R8 optimization posts coming soon. Stay tuned!
