---
title: 'R8 Optimization: Lambda Groups'
layout: post

categories: post
tags:
- Android
- R8
---

> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

Lambda usage in Kotlin feels more pervasive than Java because of the functional nature of the Kotlin standard library. Some lambdas are merely syntactic constructs that are eliminated at compile-time through the use of `inline` functions. The rest materialize into whole classes for use at runtime.

The mechanisms by which lambdas work was covered in the [Android Java 8 support](/androids-java-8-support/) post, but here's a quick refresher:

 * `javac` hoists lambda bodies to a package-private method and writes an `invoke-dynamic` bytecode for the target lambda type at the call-site. The JVM spins a class at runtime of the desired type and invokes the package-private method in the method body. Android does not ship this runtime support, so D8 performs a compile-time transformation to a class which implements the desired type and which invokes the package-private method.
 * `kotlinc` skips the `invoke-dynamic` bytecode (even when targeting Java 8+) and generates full classes directly.

Here's two Kotlin classes and some lambda usage that we can experiment with.

```kotlin
class Employee(
  val id: String,
  val joined: LocalDate,
  val managerId: String?
)

class EmployeeRepository(val allEmployees: () -> Sequence<Employee>) {
  fun joinedAfter(date: LocalDate) =
      allEmployees()
          .filter { it.joined >= date }
          .toList()

  fun reports(manager: Employee) =
      allEmployees()
          .filter { it.managerId == manager.id }
          .toList()
}
```

The `EmployeeRepository` class accepts a lambda which produces a sequence of employees and exposes two functions for listing the employees who joined after a particular date and those who report to a particular employee. Both functions use a lambda to filter the sequence to the desired items before converting to a list.

Kotlin's approach to lambdas is immediately visible after compiling this class.

```
$ kotlinc EmployeeRepository.kt
$ ls *.class
Employee.class
EmployeeRepository.class
EmployeeRepository$joinedAfter$1.class
EmployeeRepository$reports$1.class
```

Each lambda has a unique name formed by joining the enclosing class name, enclosing function name, and a monotonic value.


### Kotlin Lambdas and D8

To establish a baseline of what ends up in our APK, let's run these classfiles through D8.

```
$ java -jar $R8_HOME/build/libs/d8.jar \
      --lib $ANDROID_HOME/platforms/android-29/android.jar \
      --release \
      --output . \
      *.class
```

You can dump the whole output with `dexdump -d classes.dex`, but let's focus on the bodies of the `joinedAfter` and `reports` functions.

```
[000590] EmployeeRepository.joinedAfter:(Ljava/time/LocalDate;)Ljava/util/List;
0000: iget-object v0, v2, LEmployeeRepository;.allEmployees:Lkotlin/jvm/functions/Function0;
0002: invoke-interface {v0}, Lkotlin/jvm/functions/Function0;.invoke:()Ljava/lang/Object;
0005: move-result-object v0
0006: new-instance v1, LEmployeeRepository$joinedAfter$1;
0008: invoke-direct {v1, v3}, LEmployeeRepository$joinedAfter$1;.<init>:(Ljava/time/LocalDate;)V
000b: invoke-static {v0, v1}, Lkotlin/sequences/SequencesKt;.filter:(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
000e: move-result-object v0
000f: invoke-static {v0}, Lkotlin/sequences/SequencesKt;.toList:(Lkotlin/sequences/Sequence;)Ljava/util/List;
0012: move-result-object v0
0013: return-object v0

[0005dc] EmployeeRepository.reports:(LEmployee;)Ljava/util/List;
0000: iget-object v0, v2, LEmployeeRepository;.allEmployees:Lkotlin/jvm/functions/Function0;
0002: invoke-interface {v0}, Lkotlin/jvm/functions/Function0;.invoke:()Ljava/lang/Object;
0005: move-result-object v0
0006: new-instance v1, LEmployeeRepository$reports$1;
0008: invoke-direct {v1, v3}, LEmployeeRepository$reports$1;.<init>:(LEmployee;)V
000b: invoke-static {v0, v1}, Lkotlin/sequences/SequencesKt;.filter:(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
000e: move-result-object v0
000f: invoke-static {v0}, Lkotlin/sequences/SequencesKt;.toList:(Lkotlin/sequences/Sequence;)Ljava/util/List;
0012: move-result-object v0
0013: return-object v0
```

There's a lot going on here, but each function is almost identical so we can break both down at once:
 * `0000`-`0005` gets the `Sequence<Employee>` by invoking the `allEmployees` lambda.
 * `0006` creates an instance of the respective lambda class for each function.
 * `0008` calls the lambda class constructor, passing in either the date or manager argument as the sole parameter.
 * `000b`-`000e` calls `filter` on the sequence passing in the lambda instance.
 * `000f`-`0012` calls `toList` on the filtered sequence.
 * `0013` returns the list.

If we looked at the lambda classes we would find each implementing the `Function1` interface, having a field of type `LocalDate` or `Employee`, having a constructor which accepts a parameter and sets its field, and having an `invoke` method with the body of the lambda.

D8 performs a straightforward translation of the Java bytecode into the equivalent Dalvik bytecode. It's only when we break out R8 do interesting things start to happen.


### Kotlin Lambdas and R8

Since we have no actual usage of these APIs, they need explicitly kept or R8 will produce an empty dex file.

```
-keep class Employee { *; }
-keep class EmployeeRepository { *; }
-dontobfuscate
```

With our two classes kept, let's run R8 and see what changes.

```
$ java -jar $R8_HOME/build/libs/r8.jar \
      --lib $ANDROID_HOME/platforms/android-29/android.jar \
      --release \
      --output . \
      --pg-conf rules.txt \
      *.class kotlin-stdlib-*.jar
```

We can see what has changed in the bodies of the `joinedAfter` and `reports` functions.

```diff
 [000dd4] EmployeeRepository.joinedAfter:(Ljava/time/LocalDate;)Ljava/util/List;
 0000: iget-object v0, v3, LEmployeeRepository;.allEmployees:Lkotlin/jvm/functions/Function0;
 0002: invoke-interface {v0}, Lkotlin/jvm/functions/Function0;.invoke:()Ljava/lang/Object;
 0005: move-result-object v0
-0006: new-instance v1, LEmployeeRepository$joinedAfter$1;
-0008: invoke-direct {v1, v3}, LEmployeeRepository$joinedAfter$1;.<init>:(Ljava/time/LocalDate;)V
+0006: new-instance v1, L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;
+0008: const/4 v2, #int 0
+0009: invoke-direct {v1, v2, v4}, L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;.<init>:(ILjava/lang/Object;)V
 000d: invoke-static {v0, v1}, Lkotlin/sequences/SequencesKt;.filter:(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
 0010: move-result-object v0
 0011: invoke-static {v0}, Lkotlin/sequences/SequencesKt;.toList:(Lkotlin/sequences/Sequence;)Ljava/util/List;
 0014: move-result-object v0
 0015: return-object v0

 [000e34] EmployeeRepository.reports:(LEmployee;)Ljava/util/List;
 0000: iget-object v0, v3, LEmployeeRepository;.allEmployees:Lkotlin/jvm/functions/Function0;
 0002: invoke-interface {v0}, Lkotlin/jvm/functions/Function0;.invoke:()Ljava/lang/Object;
 0005: move-result-object v0
-0006: new-instance v1, LEmployeeRepository$reports$1;
-0008: invoke-direct {v1, v3}, LEmployeeRepository$reports$1;.<init>:(LEmployee;)V
+0006: new-instance v1, L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;
+0008: const/4 v2, #int 1
+0009: invoke-direct {v1, v2, v4}, L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;.<init>:(ILjava/lang/Object;)V
 000d: invoke-static {v0, v1}, Lkotlin/sequences/SequencesKt;.filter:(Lkotlin/sequences/Sequence;Lkotlin/jvm/functions/Function1;)Lkotlin/sequences/Sequence;
 0010: move-result-object v0
 0011: invoke-static {v0}, Lkotlin/sequences/SequencesKt;.toList:(Lkotlin/sequences/Sequence;)Ljava/util/List;
 0014: move-result-object v0
 0015: return-object v0
```

Let's break down the new bytecode:
 * `0006` creates an instance of a class named `-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA`. And, notably, both functions are creating an instance of the **same class** now.
 * `0008` stores an integer value of 0 for `joinedAfter` and 1 for `reports`.
 * `0009` call the class constructor and passes the integer and either the date or manager (but as an `Object`).

Both functions are now instantiating the same class for their lambda. Let's peek at that class.

```
Class #15            -
  Class descriptor  : 'L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;'
  Access flags      : 0x0011 (PUBLIC FINAL)
  Interfaces        -
    #0              : 'Lkotlin/jvm/functions/Function1;'
  Instance fields   -
    #0              : (in L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;)
      name          : '$capture$0'
      type          : 'Ljava/lang/Object;'
      access        : 0x1011 (PUBLIC FINAL SYNTHETIC)
    #1              : (in L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;)
      name          : '$id$'
      type          : 'I'
      access        : 0x1011 (PUBLIC FINAL SYNTHETIC)
  Direct methods    -
    #0              : (in L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;)
      name          : '<init>'
      type          : '(ILjava/lang/Object;)V'
      access        : 0x10001 (PUBLIC CONSTRUCTOR)
      code          -
[000db0] -$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA.<init>:(ILjava/lang/Object;)V
0000: iput v1, v0, L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;.$id$:I
0002: iput-object v2, v0, L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;.$capture$0:Ljava/lang/Object;
0004: return-void
```

This output tells us that the class implements the `Function1` interface, has two fields: an object and integer `id`, and has a constructor which accepts an object and integer and assigns the two fields.

Now let's look at the implementation of its`invoke` function.

```
[000d14] -$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA.invoke:(Ljava/lang/Object;)Ljava/lang/Object;
0000: iget v0, v4, L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;.$id$:I
0002: iget-object v1, v4, L-$$LambdaGroup$ks$D2r6uJKXMyXfodlTO7Kw1WcCloA;.$capture$0:Ljava/lang/Object;
0004: if-eqz v0, 002c
0006: const/4 v2, #int 1
0007: if-ne v0, v2, 002a

000a: check-cast v1, LEmployee;
 ⋮
0029: return-object v5

002a: const/4 v5, #int 0
002b: throw v5

002c: check-cast v0, Ljava/time/LocalDate;
 ⋮
0044: return-object v5
```

I've trimmed quite a lot, but let's break it down:

 * `0000` loads the integer `id` value from the field.
 * `0002` loads the object value from the field.
 * `0004` checks if the `id` is zero and if so jumps to `002c`.
 * `0006`-`0007` checks if the `id` is _not_ one and if so jumps to `002a`.
 * `000a`-`0029` casts the object to `Employee` and runs the code from the `reports` lambda body. Remember, this codepath is taken if the previous comparison of `id != 1` _fails_.
 * `002a`-`002a` causes a `NullPointerException`. Remember, this codepath is taken if `id` is not zero and not one.
 * `002c`-`0044` casts the object to `LocalDate` and runs the code from the `joinedAfter` lambda body. Remember, this codepath is taken if `id` is zero.

It can be hard to follow exactly what this transformation means solely by looking at Dalvik bytecode. We can make the equivalent transformation in source code to illustrate it more clearly.

```diff
 class EmployeeRepository(val allEmployees: () -> Sequence<Employee>) {
   fun joinedAfter(date: LocalDate) =
       allEmployees()
-          .filter { it.joined >= date }
+          .fitler(MyLambdaGroup(date, 0))
           .toList()

   fun reports(manager: Employee) =
       allEmployees()
-          .filter { it.managerId == manager.id }
+          .filter(MyLambdaGroup(manager, 1))
           .toList()
 }
+
+private class MyLambdaGroup(
+  private val capture0: Any?,
+  private val id: Int
+) : (Employee) -> Boolean {
+  override fun invoke(employee: Employee): Boolean
+    return when (id) {
+      0 -> employee.joinedAfter >= (capture0 as LocalDate)
+      1 -> employee.managerId == (capture0 as Employee).id
+      else -> throw NullPointerException()
+    }
+  }
+}
```

The two lambdas which would have produced two classes have been replaced by a single class with an integer discriminator for its behavior. By merging the bodies of the lambdas, the number of classes in the APK can be reduced.

This only works because the two lambdas have the same shape. They do not need to be _exactly_ the same as we can see in our example. One lambda captures a `LocalDate` but the other captures an `Employee`. Since both only capture a single value they have the same shape and can be merged into this single "lambda group" class.


### Java Lambdas and R8

Let's rewrite our repository in Java and see what happens.

```java
final class EmployeeRepository {
  private final Function0<Sequence<Employee>>allEmployees;

  EmployeeRepository(Function0<Sequence<Employee>> allEmployees) {
    this.allEmployees = allEmployees;
  }

  List<Employee> joinedAfter(LocalDate date) {
    return SequencesKt.toList(
      SequencesKt.filter(
          allEmployees.invoke(),
          e -> e.getJoined().compareTo(date) >= 0));
  }

  List<Employee> reports(Employee manager) {
    return SequencesKt.toList(
      SequencesKt.filter(
          allEmployees.invoke(),
          e -> Objects.equals(e.getManagerId(), manager.getId())));
  }
}
```

We're using Kotlin's `Function0` instead of `Supplier`, `Sequence` instead of `Stream`, and sequence extensions as static helpers to keep the two examples as close to each other as possible. We can compile with `javac` and reuse the same R8 invocation.

```
$ rm EmployeeRepository*.class
$ javac -cp . EmployeeRepository.class
$ java -jar $R8_HOME/build/libs/r8.jar \
      --lib $ANDROID_HOME/platforms/android-29/android.jar \
      --release \
      --output . \
      --pg-conf rules.txt \
      *.class kotlin-stdlib-*.jar
```

The `joinedAfter` and `reports` function bodies should look the same as when they were written in Kotlin, right?

```
[000d2c] EmployeeRepository.joinedAfter:(Ljava/time/LocalDate;)Ljava/util/List;
 ⋮
0008: new-instance v1, L-$$Lambda$EmployeeRepository$RwNrgP_DBeZWqltgaXgoLCrPfqI;
000a: invoke-direct {v1, v4}, L-$$Lambda$EmployeeRepository$RwNrgP_DBeZWqltgaXgoLCrPfqI;.<init>:(Ljava/time/LocalDate;)V
 ⋮

[000d80] EmployeeRepository.reports:(LEmployee;)Ljava/util/List;
 ⋮
0008: new-instance v1, L-$$Lambda$EmployeeRepository$JjZ4a6TbrR3768PIUyNflFlLVF8;
000a: invoke-direct {v1, v4}, L-$$Lambda$EmployeeRepository$JjZ4a6TbrR3768PIUyNflFlLVF8;.<init>:(LEmployee;)V
 ⋮
```

They do not! Each implementation is calling into its own lambda class rather than using a lambda group.

As far as I can tell, there's no technical limitation as to why this would only work for Kotlin lambdas but not Java lambdas. The work just hasn't been done yet. [Issue 153773246](https://issuetracker.google.com/issues/153773246) tracks adding support for merging Java lambdas into lambda groups.

---

By merging lambdas of the same shape together, R8 reduces the APK size impact and runtime classloading burden at the expense of increasing the method body of the lambda.

While the optimization does run on the entire app, by default merging will only occur within a package. This ensures any package-private methods or types used in the lambda body are accessible. Add the `-allowaccessmodification` directive to your shrinker rules to enable R8 to globally merge lambdas by increasing the visibility of referenced methods and types when needed.

You may have noticed that the names of the classes generated for Java lambdas and lambda groups appear to have some kind of hash in them. In the next post we're going to dig into the unique naming of these classes.
