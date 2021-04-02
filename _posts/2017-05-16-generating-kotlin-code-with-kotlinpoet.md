---
title: Generating Kotlin code with KotinPoet
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/generating-kotlin-code-with-kotlinpoet/

categories: post
tags:
- Kotlin
- Code Generation
- "Square Open Source ♥s Kotlin"
---

Java code generation has become a popular solution to simplifying library code. [Dagger](https://github.com/google/dagger/) generates interface implementations, [Butter Knife](https://github.com/JakeWharton/butterknife/) generates Android UI boilerplate, and [Wire](https://github.com/square/wire) generates implementations of value classes for binary encoding of data.

Despite Kotlin’s strong interop with Java, the generated Java code for these libraries can feel foreign and convention-violating as they’re targeted at Java consumers and lack Kotlin features.

Today we’re happy to announce [KotlinPoet](http://github.com/square/kotlinpoet), a library for generating Kotlin code!

Square’s history of code generation started with JavaWriter–a linear code generator that required emitting from top to bottom. We collaborated with Google’s Dagger team on its successor, [JavaPoet](https://github.com/square/javapoet), which took the concept further in providing builders and an immutable model of the generated code. KotlinPoet builds on the success of JavaPoet by providing similar models for creating Kotlin:

```kotlin
val greeterClass = ClassName.get("", "Greeter")
val kotlinFile = KotlinFile.builder("", "HelloWorld")
    .addType(TypeSpec.classBuilder("Greeter")
        .primaryConstructor(FunSpec.constructorBuilder()
            .addParameter(String::class, "name")
            .build())
        .addProperty(PropertySpec.builder(String::class, "name")
            .initializer("name")
            .build())
        .addFun(FunSpec.builder("greet")
            .addStatement("println(%S)", "Hello, \$name")
            .build())
        .build())
    .addFun(FunSpec.builder("main")
        .addParameter(ArrayTypeName.of(String::class), "args")
        .addStatement("%T(args[0]).greet()", greeterClass)
        .build())
    .build()
```

The above code produces the following Kotlin:

```kotlin
class Greeter(name: String) {
  val name: String = name
  fun greet() {
    println("Hello, $name")
  }
}
fun main(args: Array<String>) {
  Greeter(args[0]).greet()
}
```

Generating Kotlin also comes with an advantage over generating Java: JavaScript and native are available as first-party compilation targets. This allows you to use the same tool and the same generated code for multiple platforms.

[KotlinPoet](http://github.com/square/kotlinpoet) is currently an early-access release. Not every language feature and syntax is covered yet, but it’s enough to get started and make public. We’re looking forward to seeing what you build with this library!

This post concludes Square’s “[Square Open Source ♥s Kotlin](https://developer.squareup.com/blog/square-open-source-s-kotlin series.
