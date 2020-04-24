---
title: "Which is better on Android: divide by 2 or shift by 1?"
layout: post

categories: post
tags:
- Android
- D8
- Kotlin
---

I've been porting the [AndroidX collection library][collection] to Kotlin multiplatform to experiment with binary compatibility, performance, tooling, and the different memory models. Some of the data structures in the library use array-based binary trees to store elements. The Java code has a lot of shifts to replace power-of-two multiplications and divides. When ported to Kotlin, these turn into the slightly-awkward infix operators which further obfuscate the intent of the code.

 [collection]: https://developer.android.com/reference/androidx/collection/package-summary

I sampled a few people about bitwise shifts vs. multiplication/division and many had heard anecdotal claims of shifts having better performance, but everyone remained skeptical of whether it was true. Some assumed that one of the compilers seen before the code ran on a CPU would handle optimizing this case.

In an effort to satisfy my curiosity (and partially to avoid Kotlin's infix bitwise operators) I set out to answer which is better and some other related questions. Let's go!


### Does anyone optimize this?

There are three major compilers that code passes through before it hits the CPU: `javac`/`kotlinc`, D8/R8, and ART.

<a href="/static/post-image/android_compilation_pipeline.png"><img src="/static/post-image/android_compilation_pipeline.png" width="788"/></a>

Each of these has the opportunity to optimize. But do they?

#### javac

```java
class Example {
  static int multiply(int value) {
    return value * 2;
  }
  static int divide(int value) {
    return value / 2;
  }
  static int shiftLeft(int value) {
    return value << 1;
  }
  static int shiftRight(int value) {
    return value >> 1;
  }
}
```

This Java can be compiled with `javac` from JDK 14 and the resulting bytecode can be displayed with `javap`.

```
$ javac Example.java
$ javap -c Example
Compiled from "Example.java"
class Example {
  static int multiply(int);
    Code:
       0: iload_0
       1: iconst_2
       2: imul
       3: ireturn

  static int divide(int);
    Code:
       0: iload_0
       1: iconst_2
       2: idiv
       3: ireturn

  static int shiftLeft(int);
    Code:
       0: iload_0
       1: iconst_1
       2: ishl
       3: ireturn

  static int shiftRight(int);
    Code:
       0: iload_0
       1: iconst_1
       2: ishr
       3: ireturn
}
```

Every method starts with `iload_0` which loads the first argument value. The multiply and divide methods both then have `iconst_2` which loads the constant value 2. Each then runs `imul` or `idiv` to perform integer multiplication or integer division, respectively. The shift methods load the constant value 1 before `ishl` or `ishr` which is an integer shift left or integer shift right, respectively.

No optimization here, but if you know anything about Java this isn't unexpected. `javac` isn't an optimizing compiler and leaves the majority of the work to its runtime compilers on the JVM or ahead-of-time compilers.

#### kotlinc

```kotlin
fun multiply(value: Int) = value * 2
fun divide(value: Int) = value / 2
fun shiftLeft(value: Int) = value shl 1
fun shiftRight(value: Int) = value shr 1
```

The Kotlin is compiled to Java bytecode with `kotlinc` from Kotlin 1.4-M1 where the `javap` tool can once again be used.

```
$ kotlinc Example.kt
$ javap -c ExampleKt
Compiled from "Example.kt"
public final class ExampleKt {
  public static final int multiply(int);
    Code:
       0: iload_0
       1: iconst_2
       2: imul
       3: ireturn

  public static final int divide(int);
    Code:
       0: iload_0
       1: iconst_2
       2: idiv
       3: ireturn

  public static final int shiftLeft(int);
    Code:
       0: iload_0
       1: iconst_1
       2: ishl
       3: ireturn

  public static final int shiftRight(int);
    Code:
       0: iload_0
       1: iconst_1
       2: ishr
       3: ireturn
}
```

Exactly the same output as Java. This is using the original JVM backend of Kotlin, but using the forthcoming IR-based backend (via `-Xuse-ir`) also produces the same output.

#### D8

We'll use the Java bytecode output from the Kotlin example as input to the latest D8 built from `master` (SHA `2a2bf622d` at the time of writing).

```
$ java -jar $R8_HOME/build/libs/d8.jar \
      --release \
      --output . \
      ExampleKt.class
$ dexdump -d classes.dex
Opened 'classes.dex', DEX version '035'
Class #0            -
  Class descriptor  : 'LExampleKt;'
  Access flags      : 0x0011 (PUBLIC FINAL)
  Superclass        : 'Ljava/lang/Object;'
  Direct methods    -
    #0              : (in LExampleKt;)
      name          : 'divide'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000118:                              |[000118] ExampleKt.divide:(I)I
000128: db00 0102                    |0000: div-int/lit8 v0, v1, #int 2 // #02
00012c: 0f00                         |0002: return v0

    #1              : (in LExampleKt;)
      name          : 'multiply'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000130:                              |[000130] ExampleKt.multiply:(I)I
000140: da00 0102                    |0000: mul-int/lit8 v0, v1, #int 2 // #02
000144: 0f00                         |0002: return v0

    #2              : (in LExampleKt;)
      name          : 'shiftLeft'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000148:                              |[000148] ExampleKt.shiftLeft:(I)I
000158: e000 0101                    |0000: shl-int/lit8 v0, v1, #int 1 // #01
00015c: 0f00                         |0002: return v0

    #3              : (in LExampleKt;)
      name          : 'shiftRight'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000160:                              |[000160] ExampleKt.shiftRight:(I)I
000170: e100 0101                    |0000: shr-int/lit8 v0, v1, #int 1 // #01
000174: 0f00                         |0002: return v0
```

_(Note: output slightly trimmed)_

Dalvik bytecode is register-based instead of stack-based like Java bytecode. As a result, each method only has one real bytecode which does the associated integer operation. Each uses the v1 register which will be the first argument value and an integer literal of 2 or 1.

So no change behavior, but D8 isn't an optimizing compiler (although it can do [method-local optimization][method-local]).

 [method-local]: /d8-optimizations/

#### R8

To run R8 we need to define a rule in order to keep our methods from being removed.

```
-keep,allowoptimization class ExampleKt {
  <methods>;
}
```

The rules are passed with `--pg-conf` and we also supply the Android APIs to link against using `--lib`.

```
$ java -jar $R8_HOME/build/libs/r8.jar \
      --lib $ANDROID_HOME/platforms/android-29/android.jar \
      --release \
      --pg-conf rules.txt \
      --output . \
      ExampleKt.class
$ dexdump -d classes.dex
Opened 'classes.dex', DEX version '035'
Class #0            -
  Class descriptor  : 'LExampleKt;'
  Access flags      : 0x0011 (PUBLIC FINAL)
  Superclass        : 'Ljava/lang/Object;'
  Direct methods    -
    #0              : (in LExampleKt;)
      name          : 'divide'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000118:                              |[000118] ExampleKt.divide:(I)I
000128: db00 0102                    |0000: div-int/lit8 v0, v1, #int 2 // #02
00012c: 0f00                         |0002: return v0

    #1              : (in LExampleKt;)
      name          : 'multiply'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000130:                              |[000130] ExampleKt.multiply:(I)I
000140: da00 0102                    |0000: mul-int/lit8 v0, v1, #int 2 // #02
000144: 0f00                         |0002: return v0

    #2              : (in LExampleKt;)
      name          : 'shiftLeft'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000148:                              |[000148] ExampleKt.shiftLeft:(I)I
000158: e000 0101                    |0000: shl-int/lit8 v0, v1, #int 1 // #01
00015c: 0f00                         |0002: return v0

    #3              : (in LExampleKt;)
      name          : 'shiftRight'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000160:                              |[000160] ExampleKt.shiftRight:(I)I
000170: e100 0101                    |0000: shr-int/lit8 v0, v1, #int 1 // #01
000174: 0f00                         |0002: return v0
```

Same exact output as D8.

#### ART

We'll use the Dalvik bytecode output from the R8 example as the input to ART running on Android 10 on an x86 emulator.

```
$ adb push classes.dex /sdcard/classes.dex
$ adb shell
generic_x86:/ $ su
generic_x86:/ # dex2oat --dex-file=/sdcard/classes.dex --oat-file=/sdcard/classes.oat
generic_x86:/ # oatdump --oat-file=/sdcard/classes.oat
OatDexFile:
0: LExampleKt; (offset=0x000003c0) (type_idx=1) (Initialized) (OatClassAllCompiled)
  0: int ExampleKt.divide(int) (dex_method_idx=0)
    CODE: (code_offset=0x00001010 size_offset=0x0000100c size=15)...
      0x00001010:     89C8      mov eax, ecx
      0x00001012:   8D5001      lea edx, [eax + 1]
      0x00001015:     85C0      test eax, eax
      0x00001017:   0F4DD0      cmovnl/ge edx, eax
      0x0000101a:     D1FA      sar edx
      0x0000101c:     89D0      mov eax, edx
      0x0000101e:       C3      ret
  1: int ExampleKt.multiply(int) (dex_method_idx=1)
    CODE: (code_offset=0x00001030 size_offset=0x0000102c size=5)...
      0x00001030:     D1E1      shl ecx
      0x00001032:     89C8      mov eax, ecx
      0x00001034:       C3      ret
  2: int ExampleKt.shiftLeft(int) (dex_method_idx=2)
    CODE: (code_offset=0x00001030 size_offset=0x0000102c size=5)...
      0x00001030:     D1E1      shl ecx
      0x00001032:     89C8      mov eax, ecx
      0x00001034:       C3      ret
  3: int ExampleKt.shiftRight(int) (dex_method_idx=3)
    CODE: (code_offset=0x00001040 size_offset=0x0000103c size=5)...
      0x00001040:     D1F9      sar ecx
      0x00001042:     89C8      mov eax, ecx
      0x00001044:       C3      ret
```

_(Note: output significantly trimmed)_

The x86 assembly reveals that ART has indeed stepped in and normalized the arithmetic operations to use shifts!

First, `multiply` and `shiftLeft` now have the exact same implementation. They both use `shl` for a left bitwise shift of&nbsp;1. Beyond this, if you look at the offsets in the file (the leftmost column), they are actually the same. ART has recognized these functions have the same body when compiled into x86 assembly and has de-duplicated them.

Next, while `divide` and `shiftRight` are not the same, they do share the use of `sar` for a right bitwise shift of&nbsp;1. The four additional instructions in `divide` that precede `sar` handle the case when the input is negative by adding 1 to the value[^1].

 [^1]: -3 in binary is 0b11111101. If we attempt to divide by 2 by solely performing the right shift the result is 0b11111110 which is -2, an incorrect result. By adding 1 to -3 first we get -2 which in binary is 0b11111110. Shifted right we get 0b11111111 which is -1, the correct result.

     In terms of the actual instructions:

     * `mov eax, ecx` saves the original input argument value in `eax`.
     * `lea edx, [eax + 1]` adds 1 to the input argument and stores the result in `edx`, the register we will be shifting.
     * `test eax, eax` does a bitwise AND of the input argument against itself which results in a few registers being set based on properties of the input argument.
     * `cmovnl/ge edx, eax` then maybe overwrites `edx` (value+1) with `eax` (value) based on the result of the `test`.

     From there the instructions do a normal right shift. This is basically equivalent to `(value < 0 ? value + 1 : value) >> 1`.

Running the same commands on a Pixel 4 running Android 10 shows how ART compiles this code to ARM assembly[^2].

 [^2]: Thanks to [Sergey Vasilinets](https://twitter.com/ZelenetS) for providing this. `dex2oat` can only be run as root on modern Android versions so a normal Android install such as on my Pixel 3 can't run it.

```
OatDexFile:
0: LExampleKt; (offset=0x000005a4) (type_idx=1) (Verified) (OatClassAllCompiled)
  0: int ExampleKt.divide(int) (dex_method_idx=0)
    CODE: (code_offset=0x00001009 size_offset=0x00001004 size=10)...
      0x00001008: 0fc8      lsrs r0, r1, #31
      0x0000100a: 1841      adds r1, r0, r1
      0x0000100c: 1049      asrs r1, #1
      0x0000100e: 4608      mov r0, r1
      0x00001010: 4770      bx lr
  1: int ExampleKt.multiply(int) (dex_method_idx=1)
    CODE: (code_offset=0x00001021 size_offset=0x0000101c size=4)...
      0x00001020: 0048      lsls r0, r1, #1
      0x00001022: 4770      bx lr
  2: int ExampleKt.shiftLeft(int) (dex_method_idx=2)
    CODE: (code_offset=0x00001021 size_offset=0x0000101c size=4)...
      0x00001020: 0048      lsls r0, r1, #1
      0x00001022: 4770      bx lr
  3: int ExampleKt.shiftRight(int) (dex_method_idx=3)
    CODE: (code_offset=0x00001031 size_offset=0x0000102c size=4)...
      0x00001030: 1048      asrs r0, r1, #1
      0x00001032: 4770      bx lr
```

Once again `multiply` and `shiftLeft` both use `lsls` for a left shift and were de-duplicated and `shiftRight` uses `asrs` for a right shift. `divide` is also using `asrs` for its right shift, but it uses another right shift, `lsrs`, to handle adding 1 for negative values[^3].

 [^3]: In terms of the actual instructions:

      * `lsrs r0, r1, #31` does a logical (i.e., not sign-extending) shift of the input argument by 31 bits into `r0`. This results in 1 for negative numbers and 0 for positive numbers.
      * `adds r1, r0, r1` adds the result of the previous instruction to the input argument, effectively adding 1 to negative inputs.

     From there the instructions do a normal right shift. This is basically equivalent to `(value + (value >>> 31)) >> 1`.

With this we can now definitively say that replacing `value * 2` with `value << 1` offers no benefit. Stop doing it for arithmetic operations and reserve it only for strictly bitwise things!

However, `value / 2` and `value >> 1` still produce different assembly instructions and thus presumably have different performance characteristics. Thankfully, doing `value / 2` avoids using generic division and is still primarily based on right shift, so they're likely not that far apart in terms of performance.

### Is shift faster than division?

To determine whether a divide or shift is faster we can use the [Jetpack benchmark][bench] library.

 [bench]: https://developer.android.com/studio/profile/benchmark

```kotlin
class DivideOrShiftTest {
  @JvmField @Rule val benchmark = BenchmarkRule()

  @Test fun divide() {
    val value = "4".toInt() // Ensure not a constant.
    var result = 0
    benchmark.measureRepeated {
      result = value / 2
    }
    println(result) // Ensure D8 keeps computation.
  }

  @Test fun shift() {
    val value = "4".toInt() // Ensure not a constant.
    var result = 0
    benchmark.measureRepeated {
      result = value shr 1
    }
    println(result) // Ensure D8 keeps computation.
  }
}
```

I don't have any x86 devices but I do have an ARM-based Pixel 3 running Android 10. Here are the results:

```
android.studio.display.benchmark=4 ns DivideOrShiftTest.divide
count=4006
mean=4
median=4
min=4
standardDeviation=0

android.studio.display.benchmark=3 ns DivideOrShiftTest.shift
count=3943
mean=3
median=3
min=3
standardDeviation=0
```

There's effectively zero difference between using division versus a shift with numbers this small. Those are nanoseconds, after all. Using a negative number shows no difference in the result.

With this we can now definitely say that replacing `value / 2` with `value >> 1` offers no benefit. Stop doing it for arithmetic operations and reserve it only for strictly bitwise things!


### Can D8/R8 use this information to save APK size?

Given two different ways to express the same operations we should choose the one that has the better performance. But if both have the same performance, we should choose whichever results in a smaller APK size.

We know that `value * 2` and `value << 1` produce the same assembly from ART. Thus, if one is more space-efficient than the other in Dalvik bytecode we should unconditionally rewrite it into the smaller form. Looking at the output from D8 these produce the same size bytecode:

```
    #1              : (in LExampleKt;)
      name          : 'multiply'
      ⋮
000140: da00 0102                    |0000: mul-int/lit8 v0, v1, #int 2 // #02

    #2              : (in LExampleKt;)
      name          : 'shiftLeft'
      ⋮
000158: e000 0101                    |0000: shl-int/lit8 v0, v1, #int 1 // #01
```

While there are no gains to be had for this power of 2, the multiplication runs out of bytecode space before the shift for storing the literal value. Here's `value * 32_768` compared to `value << 15`:

```
    #1              : (in LExampleKt;)
      name          : 'multiply'
      ⋮
000128: 1400 0080 0000               |0000: const v0, #float 0.000000 // #00008000
00012e: 9201 0100                    |0003: mul-int v1, v1, v0

    #2              : (in LExampleKt;)
      name          : 'shiftLeft'
      ⋮
00015c: e000 000f                    |0000: shl-int/lit8 v0, v0, #int 15 // #0f
```

I have filed [an issue](https://issuetracker.google.com/issues/154643053) on D8 to investigate optimizing this automatically, but I strongly suspect the cases where it applies to be near zero so it's likely not worthwhile.

The output of D8 and R8 also tell us that `value / 2` and `value >> 1` cost the same in terms of Dalvik bytecode.

```
    #0              : (in LExampleKt;)
      name          : 'divide'
      ⋮
000128: db00 0102                    |0000: div-int/lit8 v0, v1, #int 2 // #02

    #2              : (in LExampleKt;)
      name          : 'shiftLeft'
      ⋮
000158: e000 0101                    |0000: shl-int/lit8 v0, v1, #int 1 // #01
```

These will also diverge in bytecode size when the literal reaches 32,768. Unconditionally replacing a power-of-two division with a right shift is never safe because of the behavior around negatives. We could do the replacement if the value was guaranteed to be non-negative, but D8 and R8 do not track the possible ranges of integer values at this time.


### Does unsigned number power-of-two division use shift?

Java bytecode lacks unsigned numbers, but you can emulate them by using the signed counterparts. In Java there are static helper methods for operating on signed types as unsigned values. Kotlin offers types like `UInt` which does similar things but completely abstracted behind a type. It's conceivable then that when using division by a power-of-two that it could be rewritten as a shift.

We can use Kotlin to model both of these cases.
```kotlin
fun javaLike(value: Int) = Integer.divideUnsigned(value, 2)
fun kotlinLike(value: UInt) = value / 2U
```

There's a few cases to look at just with how the code is compiled. We'll start with plain `kotlinc` (again with Kotlin 1.4-M1).
```
$ kotlinc Example.kt
$ javap -c ExampleKt
Compiled from "Example.kt"
public final class ExampleKt {
  public static final int javaLike(int);
    Code:
       0: iload_0
       1: iconst_2
       2: invokestatic  #12       // Method java/lang/Integer.divideUnsigned:(II)I
       5: ireturn

  public static final int kotlinLike-WZ4Q5Ns(int);
    Code:
       0: iload_0
       1: istore_1
       2: iconst_2
       3: istore_2
       4: iconst_0
       5: istore_3
       6: iload_1
       7: iload_2
       8: invokestatic  #20       // Method kotlin/UnsignedKt."uintDivide-J1ME1BU":(II)I
      11: ireturn
}
```

Kotlin does not recognize this as a power-of-two division where it could use the `iushr` bytecode. I've filed [KT-38493](https://youtrack.jetbrains.com/issue/KT-38493) to track adding this behavior.

Using `-Xuse-ir` doesn't change anything (except remove some of the load/store noise). However, targeting Java 8 does.

```
$ kotlinc -jvm-target 1.8 Example.kt
$ javap -c ExampleKt
Compiled from "Example.kt"
public final class ExampleKt {
  public static final int javaLike(int);
    Code:
       0: iload_0
       1: iconst_2
       2: invokestatic  #12       // Method java/lang/Integer.divideUnsigned:(II)I
       5: ireturn

  public static final int kotlinLike-WZ4Q5Ns(int);
    Code:
       0: iload_0
       1: iconst_2
       2: invokestatic  #12       // Method java/lang/Integer.divideUnsigned:(II)I
       5: ireturn
}
```

The `Integer.divideUnsigned` method is available as of Java 8 so it's prefered when targeting 1.8 or newer. Since this makes both function bodies identical, let's revert back to the old output just to see what happens with it in comparison.

Next up is R8. Notably different from when it was invoked above is that we include the Kotlin stdlib as an input and we also pass `--min-api 24` since `Integer.divideUnsigned` is only available on API 24 and newer.

```
$ java -jar $R8_HOME/build/libs/r8.jar \
      --lib $ANDROID_HOME/platforms/android-29/android.jar \
      --min-api 24 \
      --release \
      --pg-conf rules.txt \
      --output . \
      ExampleKt.class kotlin-stdlib.jar
$ dexdump -d classes.dex
Opened 'classes.dex', DEX version '039'
Class #0            -
  Class descriptor  : 'LExampleKt;'
  Access flags      : 0x0011 (PUBLIC FINAL)
  Superclass        : 'Ljava/lang/Object;'
  Direct methods    -
    #0              : (in LExampleKt;)
      name          : 'javaLike'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
0000f8:                              |[0000f8] ExampleKt.javaLike:(I)I
000108: 1220                         |0000: const/4 v0, #int 2 // #2
00010a: 7120 0200 0100               |0001: invoke-static {v1, v0}, Ljava/lang/Integer;.divideUnsigned:(II)I // method@0002
000110: 0a01                         |0004: move-result v1
000112: 0f01                         |0005: return v1

    #1              : (in LExampleKt;)
      name          : 'kotlinLike-WZ4Q5Ns'
      type          : '(I)I'
      access        : 0x0019 (PUBLIC STATIC FINAL)
      code          -
000114:                              |[000114] ExampleKt.kotlinLike-WZ4Q5Ns:(I)I
000124: 8160                         |0000: int-to-long v0, v6
000126: 1802 ffff ffff 0000 0000     |0001: const-wide v2, #double 0.000000 // #00000000ffffffff
000130: c020                         |0006: and-long/2addr v0, v2
000132: 1226                         |0007: const/4 v6, #int 2 // #2
000134: 8164                         |0008: int-to-long v4, v6
000136: c042                         |0009: and-long/2addr v2, v4
000138: be20                         |000a: div-long/2addr v0, v2
00013a: 8406                         |000b: long-to-int v6, v0
00013c: 0f06                         |000c: return v6
```

Kotlin has its own unsigned integer division implementation which was inlined into our function. It converts the input argument and the literal to longs, performs long division, and then converts back to int. When we eventually run them through ART they're just translated to equivalent x86 so we're going to leave this function behind. The opportunity for optimization here was already missed.

For the Java version, R8 failed to replace the `divideUnsigned` call with a shift. I've filed [issue 154712996](https://issuetracker.google.com/issues/154712996) to track this for D8 and R8.

The last opportunity to optimize this case is ART.
```
$ adb push classes.dex /sdcard/classes.dex
$ adb shell
generic_x86:/ $ su
generic_x86:/ # dex2oat --dex-file=/sdcard/classes.dex --oat-file=/sdcard/classes.oat
generic_x86:/ # oatdump --oat-file=/sdcard/classes.oat
OatDexFile:
0: LExampleKt; (offset=0x000003c0) (type_idx=1) (Initialized) (OatClassAllCompiled)
  0: int ExampleKt.javaLike(int) (dex_method_idx=0)
    CODE: (code_offset=0x00001010 size_offset=0x0000100c size=63)...
      0x00001010:         85842400E0FFFF             test eax, [esp + -8192]
        StackMap[0] (native_pc=0x1017, dex_pc=0x0, register_mask=0x0, stack_mask=0b)
      0x00001017:                     55             push ebp
      0x00001018:                 83EC18             sub esp, 24
      0x0000101b:                 890424             mov [esp], eax
      0x0000101e:     6466833D0000000000             cmpw fs:[0x0], 0  ; state_and_flags
      0x00001027:           0F8519000000             jnz/ne +25 (0x00001046)
      0x0000102d:             E800000000             call +0 (0x00001032)
      0x00001032:                     5D             pop ebp
      0x00001033:             BA02000000             mov edx, 2
      0x00001038:           8B85CE0F0000             mov eax, [ebp + 4046]
      0x0000103e:                 FF5018             call [eax + 24]
        StackMap[1] (native_pc=0x1041, dex_pc=0x1, register_mask=0x0, stack_mask=0b)
      0x00001041:                 83C418             add esp, 24
      0x00001044:                     5D             pop ebp
      0x00001045:                     C3             ret
      0x00001046:         64FF15E0020000             call fs:[0x2e0]  ; pTestSuspend
        StackMap[2] (native_pc=0x104d, dex_pc=0x0, register_mask=0x0, stack_mask=0b)
      0x0000104d:                   EBDE             jmp -34 (0x0000102d)
  1: int ExampleKt.kotlinLike-WZ4Q5Ns(int) (dex_method_idx=1)
    CODE: (code_offset=0x00001060 size_offset=0x0000105c size=67)...
      ⋮
```

ART does not intrinsify calls to `divideUnsigned` so instead we get the machinery to jump to the regular method implementation. I filed [issue 154693569](https://issuetracker.google.com/issues/154693569) to track adding the ART intrinsics for unsigned divide.

---

Well that certainly was a journey. Congrats if you made it this far (or just scrolled to the bottom). Let's summarize:

 1. ART rewrites power-of-two multiplication to left shift and power-of-two division to right shift (with a few extra instructions to handle negatives).
 2. There is no observable performance difference between a right shift and power-of-two division.
 3. There is no size difference in Dalvik bytecode between shifts and multiply/divide.
 4. Nobody optimizes unsigned division (yet), but you're probably not using it anyway.

With these facts we can answer the title of this post:

> Which is better on Android: divide by 2 or shift by 1?

Neither! So use division for arithmetic and only use shifts for actual bitwise operations. I'll be switching the AndroidX collection port from shifts to multiply and divide. See you next time.
