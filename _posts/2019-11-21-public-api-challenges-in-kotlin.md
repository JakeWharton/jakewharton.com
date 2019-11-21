---
title: 'Public API challenges in Kotlin'
layout: post

categories: post
tags:
- Kotlin
---

Kotlin is justifiably lauded for its language features compared to today's Java. It has constructs which allow expressing common patterns with more concise alternatives. An overused example in every intro-to-Kotlin talk or blog post is comparing a Java "POJO" to a Kotlin `data class`.

Here's yet another one of those comparisons, but bear with me as it will be used to illustrate the points in this post.

```java
public final class Person {
  private final @NonNull String name;
  private final int age;

  public Person(@NonNull String name, int age) {
    this.name = name;
    this.age = age;
  }

  public @NonNull String getName() { return name; }
  public int getAge() { return age; }

  @Override public String toString() {
    return "Person(name=" + name + ", age=" + age + ')'
  }
  @Override public boolean equals(@Nullable Object o) {
    if (o == this) return true;
    if (!(o instanceof Person)) return false;
    Person other = (Person) o;
    return name.equals(other.name)
        && age == other.age
  }
  @Override public int hashCode() {
    return Objects.hash(name, age);
  }
}
```
```kotlin
data class Person(
  val name: String,
  val age: Int
)
```

Let us assume that this `Person` type is exposed in a library. As a result, evolving its public API needs to be done in a way that's source and binary-compatible with previous versions. This post will cover some of the challenges of porting a library containing types like `Person` from Java to Kotlin while maintaining the required flexibility and exposing the correct conventions to each language.


### Binary Compatibility

What changes are necessary in order to add a new property, `nickname`, to `Person` in a binary-compatible way?

For the manually-written Java type we add a new field, getter, and constructor parameter. In order to maintain compatibility, we retain the old constructor signature for old callers.

```diff
 public final class Person {
   private final @NonNull String name;
+  private final @Nullable String nickname;
   private final int age;
 
-  public Person(@NonNull String name, int age) {
+  public Person(@NonNull String name, @Nullable String nickname, int age) {
     this.name = name;
+    this.nickname = nickname;
     this.age = age;
   }
 
+  public Person(@NonNull String name, int age) {
+    this(name, null, age);
+  }
+
   public @NonNull String getName() { return name; }
+  public @Nullable String getNickname() { return nickname; }
   public int getAge() { return age; }

   @Override public String toString() {
-   return "Person(name=" + name + ", age=" + age + ')'
+   return "Person(name=" + name + ", nickname=" + nickname + ", age=" + age + ')'
   }
   @Override public boolean equals(@Nullable Object o) {
     if (o == this) return true;
     if (!(o instanceof Person)) return false;
     Person other = (Person) o;
     return name.equals(other.name)
+        && Objects.equals(nickname, other.nickname)
         && age == other.age
   }
   @Override public int hashCode() {
-    return Objects.hash(name, age);
+    return Objects.hash(name, nickname, age);
   }
 }
```

So tedious!

The Kotlin class only needs a new property and the secondary constructor for compatibility.

```diff
 data class Person(
   val name: String,
+  val nickname: String?,
   val age: Int
-)
+) {
+  constructor(name: String, age: Int) : this(name, null, age)
+}
```

Much nicer, right? Unfortunately we have created two backwards-incompatible changes in the Kotlin version despite our efforts.

#### Destructuring Functions

For each property defined in the primary constructor, a data class will generate a `componentN()` function to facilitate [destructuring declarations](https://kotlinlang.org/docs/reference/multi-declarations.html). We can see these by running `javap` on the original Kotlin version of `Person`:

```
$ javap Person.class
Compiled from "Person.kt"
public final class Person {
  public final java.lang.String getName();
  public final int getAge();
  public final java.lang.String component1();
  public final int component2();
   ⋮
```

Adding the `nickname` property in the middle of the primary constructor causes these component methods to shift incompatibly.

```diff
 public final class Person {
   public final java.lang.String getName();
+  public final java.lang.String getNickname();
   public final int getAge();
   public final java.lang.String component1();
-  public final int component2();
+  public final java.lang.String component2();
+  public final int component3();
    ⋮
```

Consumers who are destructuring `Person` will receive a `NoSuchMethodError` at runtime unless they also recompile their code.

We can work around this by only adding new properties at the end of the primary constructor. This will ensure that existing component methods do not change their return type.

A nice property of being forced to only append properties is that we can rely on default values and the `@JvmOverloads` annotation to avoid having to manually write secondary constructors.

```diff
-data class Person(
+data class Person @JvmOverloads constructor(
   val name: String,
   val age: Int,
+  val nickname: String? = null
 )
```

The downside of this approach is that you can no longer control the order of properties.


#### Copy Functions

In addition to the component functions, two `copy` functions are also generated automatically.

```
$ javap Person.class
Compiled from "Person.kt"
public final class Person {
   ⋮
  public final Person copy(java.lang.String, int);
  public static Person copy$default(Person, java.lang.String, int, int, java.lang.Object);
   ⋮
```

These support creating a new instance of a `Person` while also updating a subset of its properties (e.g., `alice.copy(age = 99)`).

Unfortunately, adding the `nickname` property changes the signature of both of these methods breaking compatibility.

```diff
public final class Person {
    ⋮
-  public final Person copy(java.lang.String, int);
+  public final Person copy(java.lang.String, java.lang.String, int);
-  public static Person copy$default(Person, java.lang.String, int, int, java.lang.Object);
+  public static Person copy$default(Person, java.lang.String, java.lang.String, int, int, java.lang.Object);
    ⋮
```

Even if you are only appending properties to avoid breaking the component functions, these two signatures **always** change. The use of `@JvmOverloads` on the primary constructor does not propagate to the `copy` functions. Any consumers using `copy` will now receive a `NoSuchMethodError` at runtime.


#### Mitigation: No `data`

The only real way to avoid these binary-incompatibilities for public API is to avoid the `data` modifier from the start and implement `equals`, `hashCode`, and `toString` yourself. Adding `nickname` to a non-data class can be now done in a fully-compatible way.

```kotlin
class Person(
  val name: String,
  val nickname: String?,
  val age: Int
) {
  // ...
  
  constructor(name: String, age: Int) : this(name, null, age)

  override fun toString() = "Person(name=$name, nickname=$nickname, age=$age)"
  override fun equals(other: Any?) = other is Person
      && name == other.name
      && nickname == other.nickname
      && age == other.age
  override fun hashCode() = Objects.hash(name, nickname, age)
}
```

You can implement the `componentN()` functions yourself to support destructuring. If you plan to add properties in the middle of the list, however, it may not make sense for the type to support destructuring.

The `copy` method can also be written manually, but evolving it compatibly is tricky. The simplest way is to maintain all of the old versions of the function but mark them as `@Deprecated(level=HIDDEN)`. This will keep their methods in the bytecode for old callers, but prevent new users from calling anything but the latest version.

```kotlin
class Person(
  val name: String,
  val nickname: String?,
  val age: Int
) {
  // ...

  @Deprecated("", level = HIDDEN) // For binary compatibility.
  fun copy(name: String = this.name, age: Int = this.age) =
      copy(name = name, age = age) // Calls the function below.

  fun copy(name: String = this.name, nickname: String? = this.nickname, age: Int = this.age) =
      Person(name, nickname, age)
}
```


### Interop Compatibility

Another part of compatibility when migrating the `Person` library from Java to Kotlin is maintaining correct conventions for the API exposed to each language.

To avoid the explosion of constructors in Java, the `Person` type would traditionally hide its constructor and expose a nested `Builder` class. This not only allows adding new properties without a concern of binary compatibility, but allows properties to be supplied in any order and for partially-constructed instances to be passed around.

```diff
 public final class Person {
   ⋮
 
-  public Person(@NonNull String name, @Nullable String nickname, int age) {
+  private Person(@NonNull String name, @Nullable String nickname, int age) {
     this.name = name;
     this.nickname = nickname;
     this.age = age;
   }
 
   ⋮
+
+  public static final class Builder {
+    private String name;
+    private String nickname;
+    private int age;
+
+    public Builder setName(String name) { this.name = name; }
+    public Builder setNickname(String nickname) { this.nickname = nickname; }
+    public Builder setAge(int age) { this.age = age; }
+
+    public Person build() {
+      return new Person(requireNonNull(name), nickname, age);
+    }
+  }
 }
```

Creating the builder in Kotlin is nearly identical.

```diff
-class Person(
+class Person private constructor(
   val name: String,
   val nickname: String?,
   val age: Int
 ) {
   override fun toString() = TODO()
   override fun equals(other: Any) = TODO()
   override fun hashCode() = TODO()
+   
+  class Builder {
+    private var name: String? = null
+    private var nickname: String? = null
+    private var age: Int = 0
+
+    fun setName(name: String?) = apply { this.name = name }
+    fun setNickname(nickname: String?) = apply { this.nickname = nickname }
+    fun setAge(age: Int) = apply { this.age = age }
+
+    fun build() = Person(name!!, nickname, age)
+  }
 }
```

Nothing too interesting here, but by supporting Java we're starting to create problems for Kotlin.


#### Builder Boilerplate

A builder is usually a mutable(ish) version of an immutable type that also is responsible for validating any invariants (such as, in this case, that name is not null). It can be tempting to rewrite it in Kotlin as public `var`s to avoid the manual setter boilerplate.

```diff
 class Builder {
-  private var name: String? = null
+  var name: String? = null
-  private var nickname: String? = null
+  var nickname: String? = null
-  private var age: Int = 0
+  var age: Int = 0
 
-  fun setName(name: String?) = apply { this.name = name }
-  fun setNickname(nickname: String?) = apply { this.nickname = nickname }
-  fun setAge(age: Int) = apply { this.age = age }
-
   fun build() = Person(name!!, nickname, age)
 }
```

Unfortunately, doing so would be incorrect. The return type of the generated setters are now `void` instead of `Builder`.

Without a language change to allow property setters to return values, we are forced to use setter functions. I tend to keep the public `var` but hide its `void`-returning setter from Java with the `@JvmSynthetic` annotation. This allows Kotlin users to still get full usage of the property for reading and writing.

```diff
 class Builder {
-  private var name: String? = null
+  @set:JvmSynthetic // Hide 'void' setter from Java
+  var name: String? = null
-  private var nickname: String? = null
+  @set:JvmSynthetic // Hide 'void' setter from Java
+  var nickname: String? = null
-  private var age: Int = 0
+  @set:JvmSynthetic // Hide 'void' setter from Java
+  var age: Int = 0
 
   fun setName(name: String?) = apply { this.name = name }
   fun setNickname(nickname: String?) = apply { this.nickname = nickname }
   fun setAge(age: Int) = apply { this.age = age }
 
   fun build() = Person(name!!, nickname, age)
 }
```

There is no annotation to hide the setter functions from Kotlin callers. While not essential, they're far better served by mutating the properties in an `apply { }` block.


#### Constructor

By virtue of making the primary constructor private we've removed the idiomatic means of creating a `Person` for Kotlin. Instead of a builder, Kotlin prefers default parameter values and named arguments. The `@JvmSynthetic` annotation can't be used to hide constructors from Java, so we need to purse a different approach.

There is a convention of defining a top-level function whose name is the same as a type which we can use to replicate the constructor.

```kotlin
fun Person(name: String, nickname: String? = null, age: Int): Person {
  return Person(name, nickname, age)
}
```

Since this is a regular function and not a constructor, we can hide it from Java with `@JvmSynthetic`.

```diff
+@JvmSynthetic // Hide from Java callers who should use Builder.
 fun Person(name: String, nickname: String? = null, age: Int): Person {
  ⋮
```

Once again, however, we've fallen into a binary compatibility trap. This signature has the same problem as the `copy` function that was generated for a data class.

Thankfully, since we wrote this function, the same mitigation trick can be used as outlined above for a manually-written `copy`. That is, we maintain the old versions of the function and mark them as `@Deprecated(level=HIDDEN)`.

These factory functions have no way of enforcing only named-parameter usage. As a result, they are vulnerable to source-incompatibility issues as arguments change position.

There's also the problem of having to duplicate default values in each of these factory functions and the builder. A best practice would be to maintain defaults in private constants that could be re-used, but that requires additional discipline and continues to add boilerplate.


#### Mitigation: Factory DSL?

While currently unconventional, another potential workaround for the constructor problem is to change from a function-like syntax to a DSL-like syntax leveraging the `Builder`.

```kotlin
@JvmSynthetic // Hide from Java callers who should use Builder.
fun Person(initializer: Person.Builder.() -> Unit): Person {
  return Person.Builder().apply(initializer).build()
}
```

Creation of an instance now looks more like inline-JSON.

```kotlin
val alice = Person {
  name = "Alice Alison"
  age = 99
}
```

This also has the advantage of re-using any default values from the builder allowing them to be localized in one place.

DSLs tend to have specialized usage and are do not currently have widespread usage as factories. Their ability to enforce named usage and maintain source and binary compatibility as properties are introduced makes them an attractive solution, however.


### Summary

Using Kotlin types whose properties will change over time in public API requires extra care to maintain source and binary compatibility as well as an idiomatic API for each language.

* Avoid using the `data` modifier. Instead, implement `equals`, `hashCode`, and `toString` yourself for these value-based types.
* Expose a builder for Java callers. Public `var`s are not enough, fluent setters need to be written.
* Hide constructors and be mindful of factory function binary compatibility. Reusing the builders for a DSL-factory may be a way to avoid this.

If your type is not going to change its properties over time (like a 2D point) you can ignore this advice and stick with a simple `data class`.

---

Here is the final `Person` declaration for the public API of a library:

```kotlin
class Person private constructor(
  val name: String,
  val nickname: String?,
  val age: Int
) {
  override fun toString() = "Person(name=$name, nickname=$nickname, age=$age)"
  override fun equals(other: Any?) = other is Person
      && name == other.name
      && nickname == other.nickname
      && age == other.age
  override fun hashCode() = Objects.hash(name, nickname, age)

  class Builder {
    @set:JvmSynthetic // Hide 'void' setter from Java
    var name: String? = null
    @set:JvmSynthetic // Hide 'void' setter from Java
    var nickname: String? = null
    @set:JvmSynthetic // Hide 'void' setter from Java
    var age: Int = 0

    fun setName(name: String?) = apply { this.name = name }
    fun setNickname(nickname: String?) = apply { this.nickname = nickname }
    fun setAge(age: Int) = apply { this.age = age }

    fun build() = Person(name!!, nickname, age)
  }
}

@JvmSynthetic // Hide from Java callers who should use Builder.
fun Person(initializer: Person.Builder.() -> Unit): Person {
  return Person.Builder().apply(initializer).build()
}
```

Quite the distance from the simple `data class` version, but it's at least safe to change over time.

Future versions of Kotlin will stabilize compiler plugins allowing these patterns to be placed behind annotations or custom modifiers.

```kotlin
// Hypothetical 'value' on 'class' provides generated 'equals',
// 'hashCode', and 'toString' similar to 'data'.
value class Person private constructor(
  val name: String,
  val nickname: String? = null,
  val age: Int
) {
  // Hypothetical 'builder' on nested 'class' exposes mutable
  // versions of primary constructor properties.
  builder class Builder
}
```

This will eliminate the boilerplate required to create Kotlin types suitable for evolving in public APIs.