---
title: Avoiding Vendor- and Version-Specific VM Bugs
layout: post

categories: post
tags:
- Android
- Java
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/).

The first two posts ([1](/androids-java-8-support/), [2](/androids-java-9-10-11-and-12-support/)) in this series explored how D8 is responsible for desugaring new Java language features to work on all versions of Android. Desugaring is the more interesting feature to demonstrate, but it's secondary functionality of D8. The primary responsibility is converting the stack-based Java bytecode into register-based Dalvik bytecode so that it can run on Android's VM.

At this point in Android's tenure it'd be reasonable to think that this conversion (called _dexing_) is a solved problem. During the process of building and rolling out D8, however, interesting vendor-specific and version-specific bugs in different VMs were uncovered which this post is going to explore.


### Not A Not

D8 takes compiled Java bytecode and produces equivalent functionality using Dalvik bytecode. We can see this with a simple example that uses Java's bitwise not operator.

```java
class Not {
  static void print(int value) {
    System.out.println(~value);
  }
}
```

Compiling and dumping the class file shows the bytecodes that are used to implement this feature.

```
$ javac *.java

$ javap -c *.class
class Not {
  static void print(int);
    Code:
       0: getstatic     #2      // Field java/lang/System.out:Ljava/io/PrintStream;
       3: iload_0
       4: iconst_m1
       5: ixor
       6: invokevirtual #3      // Method java/io/PrintStream.println:(I)V
       9: return
}
```

Bytecode index `3`, `4`, and `5` load the argument value onto the stack, load the constant -1, and perform a bitwise exclusive-or. If your bitwise skills are a little rusty, -1 is represented as all 1s and an exclusive-or sets a bit if and only if one of the two bits is set.

```
00010100  (value)
 xor
11111111  (-1)
 =
11101011
```

By performing an exclusive-or on a number whose bits are all set to 1, we are left with a number whose bits are the opposite of the original yielding the bitwise not.

Running this through D8 shows the operation is implemented similarly in Dalvik bytecode.

```
$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[000134] Not.print:(I)V
0000: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
0002: xor-int/lit8 v1, v1, #int -1
0004: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(I)V
0007: return-void
```

Index `0002` performs an exclusive-or on register `v1` (the argument value) with a constant of -1 and stores it back into `v1`. This is a very straightforward mapping from Java bytecode and if you didn't know any better it wouldn't be given a second thought. But its inclusion in this post should tip you off that there is more to the story.

All of the Dalvik bytecodes are [available for browsing](https://source.android.com/devices/tech/dalvik/dalvik-bytecode) on the Android developer documentation site. If you look closely, there's a unary operator section which contains a bytecode called `not-int`. Instead of doing an exclusive-or on the argument value with -1 a dedicated bitwise not bytecode could be used. This has the potential for using more efficient machine instructions and hardware in the CPU. So why isn't it being used?

The answer lies with the old `dx` tool and the fact that it also does not use the `not-int` instruction.

```
$ $ANDROID_HOME/build-tools/28.0.3/dx \
      --dex \
      --output=classes.dex \
      *.class
[000130] Not.print:(I)V
0000: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
0002: xor-int/lit8 v1, v2, #int -1
0004: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(I)V
0007: return-void
```

The old `dx` tool is hosted in `dalvik/dx/` of AOSP. If we grep its codebase, we can find the constant used for the `not-int` instruction.

```
$ grep -r -C 1 'not-int' src/com/android/dx/io
OpcodeInfo.java-522-    public static final Info NOT_INT =
OpcodeInfo.java:523:        new Info(Opcodes.NOT_INT, "not-int",
OpcodeInfo.java-524-            InstructionCodec.FORMAT_12X, IndexType.NONE);
```

So while `dx` knows that the instruction exists, when you grep its codebase for _uses_ of that constant when converting from a class file there are zero! For comparison I've also included the `if-eq` bytecode's constant.

```
$ grep -r -C 1 'NOT_INT' src/com/android/dx/cf

$ grep -r -C 1 'IF_EQ' src/com/android/dx/cf
code/RopperMachine.java-885-            case ByteOps.IFNULL: {
code/RopperMachine.java:886:                return RegOps.IF_EQ;
code/RopperMachine.java-887-            }
```

This means that the `dx` tool will never emit a `not-int` instruction no matter what Java bytecodes were used. This is unfortunate, but ultimately isn't that big of a deal.

The real problem stems from the fact that because the bytecode was never used by the canonical dexing tool, some vendors decided that they wouldn't bother supporting it in their Dalvik VM's JIT! Once D8 came along and started using the full bytecode set, JIT-compiled apps running on these specific phones would crash. As a result, D8 can't use the `not-int` instruction in this case even if it wants to.

With the introduction of the ART VM in API 21, all phones now have support for this instruction. As a result, passing `--min-api 21` to D8 will change the bytecodes used to leverage `not-int`.

```
$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --min-api 21 \
    --output . \
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[000134] Not.print:(I)V
0000: sget-object v0, Ljava/lang/System;.out:Ljava/io/PrintStream;
0002: not-int v1, v1
0003: invoke-virtual {v0, v1}, Ljava/io/PrintStream;.println:(I)V
0006: return-void
```

Index `0002` now contains the more specific instruction as we expect.

In a similar manner to how language features are desugared to work on older versions of Android, D8 can change the shape of individual bytecodes to ensure compatibility. As the ecosystem and our minimum API level rises, D8 will automatically use the more efficient bytecodes.


### Long Compare

Even when all of the instructions in use are supported, vendor-specific JITs are software like any other and can contain bugs. This happened close to home in code that was present in OkHttp and Okio.

Both libraries deal in moving and counting bytes. Their methods frequently start with a check for a negative count (which is invalid) and then a zero count (no work to do).

```java
class LongCompare {
  static void somethingWithBytes(long byteCount) {
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0");
    if (byteCount == 0) return; // Nothing to do!
    // Do something…
  }
}
```

When you compile and dex this code, the constant 0 is loaded and then two comparisons are made.

```
$ javac *.java

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[000138] LongCompare.somethingWithBytes:(J)V
0000: const-wide/16 v0, #int 0
0002: cmp-long v2, v3, v0
0004: if-ltz v2, 000b
0006: cmp-long v2, v3, v0
0008: if-nez v2, 000a
…
```

Based on these bytecodes, we can infer that `cmp-long` produces a value that's either less-than zero, zero, or greater-than zero. After each comparison, a check for less-than zero occurs and then a check for non-zero, respectively. But if a single `cmp-long` produces the comparison result, why does index `0006` perform it a second time?

The reason is that one vendor-specific JIT will crash if a non-zero check is performed immediately after a less-than zero check. This would cause the program to see impossible exceptions such as a `NullPointerException` when only dealing with `long`s.

Just in the last example, the introduction of the ART VM resolved this problem. Passing `--min-api 21` produces the more efficient sequence which only does a single `cmp-long`.

```
$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --min-api 21 \
    --output . \
    *.class

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[000138] LongCompare.somethingWithBytes:(J)V
0000: const-wide/16 v0, #int 0
0002: cmp-long v2, v2, v0
0004: if-ltz v2, 0009
0006: if-nez v2, 0008
…
```

Once again D8 changes the shape of the bytecodes it uses for the purpose of compatibility. When your application no longer supports the versions of Android which have the broken vendor implementations, the bytecode is updated to the more efficient form.

But while ART has brought a normalization to the VM across the ecosystem eliminating (or at least reducing) these vendor-specific bugs, it is not exempt from bugs itself.


### Recursion

Bugs that occur in ART itself affect specific versions of Android regardless of the vendor. As D8 evolves and changes the bytecode it emits, dormant bugs in ART can suddenly surface.

The example which demonstrates an interesting bug is admittedly very contrived, but the code was derived from a real application and distilled into a self-contained example.

```java
import java.util.List;

class Recursion {
  private void f(int x, double y, double u, double v, List<String> w) {
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    w.add(g(y, u, v));
  }

  private String g(double y, double u, double v) {
    return null;
  }
}
```

In Android 6.0 (API 23), ART's ahead-of-time (AOT) compiler added call analysis in order to perform method inlining. Due to the heavily-recursive nature of the `f` method above, the `dex2oat` compiler will actually consume all of the memory on the device during this analysis and crash. This was fixed in the next release, Android 7.0 (API 24).

When your minimum SDK is below 24, D8 will change the dex file to work around this bug. But before looking at the workaround, let's reproduce the crash.

```
$ javac *.java

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --min-api 24 \
    --output . \
    *.class
```

We pass `--min-api 24` to D8 in order to produce a dex file that does not contain the workaround for the bug. If you push this dex file to an API 23 device, `dex2oat` will refuse to compile it.

```
$ adb shell push classes.dex /sdcard

$ adb shell dex2oat --dex-file=/sdcard/classes.dex --oat-file=/sdcard/classes.oat

$ adb logcat
…
11-29 13:57:08.303  4508  4508 I dex2oat : dex2oat --dex-file=/sdcard/classes.dex --oat-file=/sdcard/classes.oat
11-29 13:57:08.306  4508  4508 W dex2oat : Failed to open .dex from file '/sdcard/classes.dex': Failed to open dex file '/sdcard/classes.dex' from memory: Unrecognized version number in /sdcard/classes.dex: 0 3 7
11-29 13:57:08.306  4508  4508 E dex2oat : Failed to open some dex files: 1
11-29 13:57:08.309  4508  4508 I dex2oat : dex2oat took 7.440ms (threads: 4)
```

The [documentation for the dex file format](https://source.android.com/devices/tech/dalvik/dex-format#dex-file-magic) defines that the first 8 bytes should be the characters `DEX`, a newline character, three number characters indicating the version, and then a null byte. Because `--min-api 24` was specified, the dex file declares version `037`. Dumping the first few bytes of the dex file confirm this.

```
$ xxd classes.dex | head -1
00000000: 6465 780a 3033 3700 e595 2d8c 49b5 d6b6  dex.037...-.I...
```

In order to get this dex file to install on an older device the version must be changed to `035`. Any hex editor can be used to do this. I used `xxd` again to convert from binary to hexadecimal, edited the hexidecimal in an editor (which I know how to exit), and then used `xxd` again to convert hexadecimal back to binary.

```
$ xxd -p classes.dex > classes.hex

$ nano classes.hex  # Change 303337 to 303335

$ xxd -p -r classes.hex > classes.dex
```

With the version changed this dex file will now compile on Android 6.0 devices but with a different result.

```
$ adb shell push classes.dex /sdcard

$ adb shell dex2oat --dex-file=/sdcard/classes.dex --oat-file=/sdcard/classes.oat
Segmentation fault
```

Whoops! We (successfully) crashed the AOT compiler. Running `dex2oat` with the same dex file on Android 7.0 or newer does not trigger the crash, as expected.

Removing the `--min-api 24` line will force D8 to insert its work around for this AOT compiler problem. Before doing so the old dex file is renamed so that we can compare the two.

```
$ mv classes.dex classes_api24.dex

$ java -jar d8.jar \
    --lib $ANDROID_HOME/platforms/android-28/android.jar \
    --release \
    --output . \
    *.class
```

Dumping the bytecodes of both shows the difference.

```
$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes_api24.dex
[000190] Recursion.f:(IDDDLjava/util/List;)V
0000: invoke-direct/range {v7, v8, v9, v10, v11, v12, v13, v14, v15}, LRecursion;.f:(IDDDLjava/util/List;)V
…
0018: invoke-direct/range {v7, v8, v9, v10, v11, v12, v13, v14, v15}, LRecursion;.f:(IDDDLjava/util/List;)V
001b: move-object v0, v7
001c: move-wide v1, v9
001d: move-wide v3, v11
001e: move-wide v5, v13
001f: invoke-direct/range {v0, v1, v2, v3, v4, v5, v6}, LRecursion;.g:(DDD)Ljava/lang/String;
0022: move-result-object v8
0023: invoke-interface {v15, v8}, Ljava/util/List;.add:(Ljava/lang/Object;)Z
0026: return-void
  catches       : (none)

$ $ANDROID_HOME/build-tools/28.0.3/dexdump -d classes.dex
[000198] Recursion.f:(IDDDLjava/util/List;)V
0000: invoke-direct/range {v7, v8, v9, v10, v11, v12, v13, v14, v15}, LRecursion;.f:(IDDDLjava/util/List;)V
…
0018: invoke-direct/range {v7, v8, v9, v10, v11, v12, v13, v14, v15}, LRecursion;.f:(IDDDLjava/util/List;)V
001b: move-object v0, v7
001c: move-wide v1, v9
001d: move-wide v3, v11
001e: move-wide v5, v13
001f: invoke-direct/range {v0, v1, v2, v3, v4, v5, v6}, LRecursion;.g:(DDD)Ljava/lang/String;
0022: move-result-object v8
0023: invoke-interface {v15, v8}, Ljava/util/List;.add:(Ljava/lang/Object;)Z
0026: return-void
0027: move-exception v8
0028: throw v8
  catches       : 1
    0x0018 - 0x001b
      Ljava/lang/Throwable; -> 0x0027
```

The contents of each version of the method are the exact same until the very end. The version which works around the bug has two extra bytecodes, `move-exception` and `throw`, and an entry in the `catches` section. This is the bytecode equivalent of a try-catch block that simply re-throws the exception. By inserting this try-catch block, the AOT compiler's call analysis for method inling is disabled.

The range of the catch block only covers the last recursive call from bytecode index `0018` to `001b`. If you were remove a single call to `f` in the original source code, the level of recursion won't be large enough to trigger the bug in the AOT compiler. Therefore the try-catch workaround only surrounds the recursive calls when they're problematic.

The same code when dexed with the old `dx` compiler will not cause a crash on Android 6.0. This is because the bytecode is less efficient and uses more registers which prevents the inlining analysis from even running.


---

The three examples above are a few cases of vendor- and version-specific bugs in Android's VMs. Just like the language feature desugaring covered in the previous posts, D8 will only apply workarounds for these bugs when necessary based on your minimum API level.

The conditionals which control whether these are applied are at the bottom of a [file named `InternalOptions.java`](https://r8.googlesource.com/r8/+/master/src/main/java/com/android/tools/r8/utils/InternalOptions.java) in the D8 codebase. Bugs in the VM aren't only found in old versions of Android. If you search for `AndroidApiLevel.Q` in that file you'll find two workarounds for VM bugs present in every version of Android (at least at time of writing).

It's important to remember that all of these problems weren't caused by D8. They were uncovered by D8 in its effort to use registers more effectively and order bytecodes more efficiently when compared to `dx`. For optimizing dex even further, we have to turn to D8's optimizing sibling, R8, which we'll start to examine in the next post.



_(This post was adapted from a part of my [Digging into D8 and R8](/digging-into-d8-and-r8) talk that was only partially presented. Watch the video and look out for future blog posts for more content like this.)_