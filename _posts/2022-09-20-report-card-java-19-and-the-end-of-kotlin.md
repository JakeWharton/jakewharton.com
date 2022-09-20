---
title: 'Report card: Java 19 and the end of Kotlin'
layout: post

categories: post
tags:
- Java
- Kotlin
---

Three years ago I gave the talk ["What's new in Java 19: The end of Kotlin?"](/whats-new-in-java-19-the-end-of-kotlin/) which forecasted what a future Java language would look like in September 2022 when Java 19 was released.
Check your calendars, folks. It's September 2022 right now and Java 19 was released today!

As expected my predictions were not perfect, but I'm pretty happy with the results.
Let's check in with each feature and see how my predictions fared report-card style[^1].

[^1]: A is perfect, F is fail, and there is no E.


### Local methods

This feature allows for methods to be declared inside of other methods making them effectively private to that method.

```java
public static boolean anyMatch(Graph graph, Predicate<Node> predicate) {
  var seen = new HashSet<Node>();
  
  boolean hasMatch(Node node) {
    if (!seen.add(node)) return false; // already seen
    if (predicate.test(node)) return true; // match!
    return node.getNodes().stream().anyMatch(n -> hasMatch(n));
  }
  
  return hasMatch(getRoot());
}
```

**Grade: F** 🔴

Working support for local methods was added to a branch in Project Amber in October 2019.
It seemed like a slam dunk, but a JEP for the feature was never created.
The branch still sits in the Project Amber repo unchanged in three years.

If I had to guess, all eyes in Amber are focused on pattern matching and its related features.
Hopefully someday local methods can be picked back up as a proposed feature.


### Text blocks

A multiline string literal for when one line just isn't enough.

```java
System.out.println("""
  SELECT *
  FROM users
  WHERE name LIKE 'Jake %'
""");
```

**Grade: A** 🟢

[Delivered in Java 15](https://openjdk.org/jeps/378).


### Records

A read-only type that exists solely for carrying data with strong, semantic names.

```java
record Person(String name, int age) { }
```

**Grade: A** 🟢

[Delivered in Java 16](https://openjdk.org/jeps/395).


### Sealed hierarchies

Define the list of permitted subtypes of your class or interface and prevent any others.

```java
sealed interface Developer { }
record Person(String name, int age) extends Developer { }
record Business(String name) extends Developer { }
```

**Grade: A** 🟢

[Delivered in Java 17](https://openjdk.org/jeps/409).


### Type patterns

Declare a new name to bind when a type test succeeds.

```java
Object o = 1;
if (o instanceof Integer i) {
  System.out.println(i + 1);
}
```

**Grade: A** 🟢

[Delivered in Java 16](https://openjdk.org/jeps/394) for `instanceof`.
[Third preview in Java 19](https://openjdk.org/jeps/427) for use in a `switch`.


### Record patterns

Bind the component parts of a record type to local names.

```java
Developer alice = Person("Alice", 12);
switch (alice) {
  case Person(var name, var age) -> // ...
}
```

**Grade: C** 🟠

[First preview in Java 19](https://openjdk.org/jeps/405)

Just now starting in preview and does not include things like the use of an underscore (`_`) as a wildcard or syntax for destructors.


### Virtual threads

All of your blocking calls with none of the blocking.

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
  for (int count = 10; count > 0; count--) {
    executor.submit(() -> {
      Thread.sleep(100 * count);
      System.out.println(count)
    });
  });
}
```

**Grade: B** 🟡

[First preview in Java 19](https://openjdk.org/jeps/425).

Nice to see it just make the cut, although expect a few previews to be needed before it's stable.

---

All things considered I think this is a passing report card (despite failing one elective).

The next three years of Java will hopefully see the completion of the items above as well as see larger efforts like Project Panama and Project Valhalla start to come to fruition.
It's a great time to be a Java developer.

To the surprise of no one, Kotlin did not end. 
It continued to evolve in the last three years with language features such as context receivers, sealed interfaces, and exhaustive-by-default.
It's also a great time to be a Kotlin developer.

But in the end I think we can all agree on one thing: there's no such thing as OpenJDK LTS and the best long-term version of the JDK is always the latest one. Welcome to my hill. **Update to Java 19 today!**
