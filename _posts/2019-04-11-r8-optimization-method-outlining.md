---
title: 'R8 Optimization: Method Outlining'
layout: post

categories: post
tags:
- Android
- R8
---
 
> Note: This post is part of a series on D8 and R8, Android's new dexer and optimizer, respectively. For an intro to D8 read ["Android's Java 8 support"](/androids-java-8-support/). For an intro to R8 read ["R8 Optimization: Staticization"](/r8-optimization-staticization/).

I recently wrote about [the economics of generated code](/the-economics-of-generated-code/) which talked about performing optimizations to generated code that aren't worthwhile in manually-written code. While the examples in that post were motivated by changes to code generators that I had worked on in the past, it also resulted in some new changes being made.

One change proposed to [Moshi](https://github.com/square/moshi/), a JSON serializer, replaced its generated strings with `StringBuilder` to de-duplicate the constant parts. Each non-null property in your JSON model generates an exception to ensure non-null values are read from the JSON.

```diff
 name = stringAdapter.fromJson(reader) ?:
     throw JsonDataException(
-        "Non-null value 'name' was null at ${reader.path}")
+        StringBuilder("Non-null value '").append("name")
+            .append("' was null at ").append(reader.path).toString())
```

A second exception is generated when that non-null property lacks a default value and no value was present in the JSON.

```diff
 return Person(
   name = name ?: throw JsonDataException(
-      "Required property 'name' missing at ${reader.path}"),
+      StringBuilder("Required property '").append("name")
+          .append("' missing at ").append(reader.path).toString()),
```

These two diffs are the result of applying the advice from that post.

Each of these exceptions are generated for every property in the type. This means if you have a type with 10 properties you get 20 exceptions generated (assuming they're non-null and don't have default values). This winds up creating a lot of `StringBuilder` bytecode!

One way to reduce this bytecode bloat is to generate a private method which takes four arguments (prefix, name, suffix, path) and returns the final string. This was proposed as part of the change to Moshi. We ultimately duplicated the code instead of generating a method because it ends up optimizing to a smaller APK thanks to R8. Let's find out why.


### Representative Example

Instead of dealing with Moshi, kapt, and generated Kotlin directly, it's easier to work with a representative example. To start with, we need some JSON model objects. In order to require both of the `StringBuilder` usages from above, each property has a non-null type and has no default value.

```kotlin
data class User(
  val id: String,
  val username: String,
  val displayName: String,
  val email: String,
  val created: OffsetDateTime,
  val isPublic: Boolean
)

data class Tweet(
  val id: String,
  val userId: String,
  val content: String,
  val created: OffsetDateTime
)
```

When used with Moshi, these types would be annotated with `@JsonClass` which causes the annotation processor to generate code. That code then interacts with Moshi's `JsonReader` type to parse the values of each property. We can replicate this using Android's built-in `JsonReader` type and writing the generated code by hand.

```kotlin
object TweetParser {
  fun fromJson(reader: JsonReader): Tweet {
    var id: String? = null
    // other properties…

    reader.beginObject()
    while (reader.peek() != JsonToken.END_OBJECT) {
      when (reader.nextName()) {
        "id" -> id = reader.nextString() ?:
            throw IllegalStateException(
                StringBuilder("Non-null value '").append("id")
                    .append("' was null at").append(reader).toString())
        // other properties…
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return Tweet(
      id = id ?: throw IllegalStateException(
          StringBuilder("Required property '").append("id")
             .append("' missing at ").append(reader).toString()),
      // other properties…
    )
  }
}
```

This is the version for `Tweet` showing only one property. You would do the same for the `userId`, `content` and `created` properties, and create a similar type for parsing `User`[^1].

[^1]: The full example code is available at [gist.github.com/JakeWharton/6d08b7fb74c320b048db68e21912d878](https://gist.github.com/JakeWharton/6d08b7fb74c320b048db68e21912d878)

If you compile with `kotlinc`, dex with D8, and dump the bytecode with `dexdump` you'll see the `StringBuilder` code repeated many times.

```
0181: new-instance v1, Ljava/lang/IllegalStateException;
0183: new-instance v4, Ljava/lang/StringBuilder;
0185: invoke-direct {v4, v3}, Ljava/lang/StringBuilder;.<init>:(Ljava/lang/String;)V
0188: invoke-virtual {v4, v12}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
018b: invoke-virtual {v4, v2}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
018e: invoke-virtual {v4, v0}, Ljava/lang/StringBuilder;.append:(Ljava/lang/Object;)Ljava/lang/StringBuilder;
0191: invoke-virtual {v4}, Ljava/lang/StringBuilder;.toString:()Ljava/lang/String;
0194: move-result-object v0
0195: invoke-direct {v1, v0}, Ljava/lang/IllegalStateException;.<init>:(Ljava/lang/String;)V
0198: check-cast v1, Ljava/lang/Throwable;
019a: throw v1
```

This bytecode sequence weighs less than generating the single string so its a net win no matter what, but this still feels like a waste. Generating a method with this code in each parser type would reduce its impact. So why did we elect not to?


### Outlining

Most of the posts in this R8 series have touched on _inlining_ in one way or another. This optimization is when a method is small enough and/or called infrequently enough that it becomes beneficial to copy the method body contents to the call site and remove the method. _Outlining_ is the opposite optimization where common bytecode sequences are identified and extracted to a shared method.

Before running R8, let's add a `main` function which uses our parsers and can serve as an entry point for optimization.

```kotlin
fun main() {
  println(TweetParser.fromJson(JsonReader(StringReader(""))))
  println(UserParser.fromJson(JsonReader(StringReader(""))))
}
```

We don't need real data because we're not executing the code. This is just enough to ensure R8 keeps the codepaths we care about. With some simple rules to keep the `main` method, let's see what R8 does.

```
$ cat rules.txt
-keepclasseswithmembers class * {
  public static void main(...);
}
-dontobfuscate

$ java -jar r8.jar \
      --lib $ANDROID_HOME/platforms/android-28/android.jar \
      --release \
      --output . \
      --pg-conf rules.txt \
      *.class
```

Dumping the output of R8 shows a very different picture for the exception code compared to what D8 produced.

```diff
 0181: new-instance v1, Ljava/lang/IllegalStateException;
-0183: new-instance v4, Ljava/lang/StringBuilder;
-0185: invoke-direct {v4, v3}, Ljava/lang/StringBuilder;.<init>:(Ljava/lang/String;)V
-0188: invoke-virtual {v4, v12}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
-018b: invoke-virtual {v4, v2}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;
-018e: invoke-virtual {v4, v0}, Ljava/lang/StringBuilder;.append:(Ljava/lang/Object;)Ljava/lang/StringBuilder;
-0191: invoke-virtual {v4}, Ljava/lang/StringBuilder;.toString:()Ljava/lang/String;
+0183: invoke-static {v3, v12, v2, v0}, Lcom/android/tools/r8/GeneratedOutlineSupport;.outline0:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
 0194: move-result-object v0
 0195: invoke-direct {v1, v0}, Ljava/lang/IllegalStateException;.<init>:(Ljava/lang/String;)V
-0198: check-cast v1, Ljava/lang/Throwable;
 019a: throw v1
```

The outlining optimization has recognized that the `StringBuilder` code is repeated many times. The bytecode sequence is de-duplicated to the `outline0` method on this `com.android.tools.r8.GeneratedOutlineSupport` class. Every occurrence of the bytecode sequence is replaced with a call to this new method.

Taking a look at the new method shows the common `StringBuilder` code.

```
[000eb4] com.android.tools.r8.GeneratedOutlineSupport.outline0:(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
0000: new-instance v0, Ljava/lang/StringBuilder
0002: invoke-direct {v0, v1}, Ljava/lang/StringBuilder;.<init>:(Ljava/lang/String;)V
0005: invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder
0008: invoke-virtual {v0, v3}, Ljava/lang/StringBuilder;.append:(Ljava/lang/String;)Ljava/lang/StringBuilder
000b: invoke-virtual {v0, v4}, Ljava/lang/StringBuilder;.append:(Ljava/lang/Object;)Ljava/lang/StringBuilder
000e: invoke-virtual {v0}, Ljava/lang/StringBuilder;.toString:()Ljava/lang/String
0011: move-result-object v1
0012: return-object v1
```

R8 has created the helper method which we were considering adding ourselves!

I specifically chose to use two types in the example which together have 10 properties resulting in 20 `StringBuilder` usages. This is the lower bound of duplicate sequences that R8 will consider outlining. The duplicated bytecode must also be between 3 and 99 bytes.

If Moshi generated a private `StringBuidler` helper method our example would still have two copies. You would need 20 JSON model objects before R8 stepped in and de-duplicated the helper method. By electing to duplicate the `StringBuilder` code, only 20 _properties_ are needed in any number of JSON model objects before R8 outlining kicks in. Once that happens we only pay for the code once no matter how many JSON model objects and properties are in use.


---

Outlining works really well with generated code since it tends to produce repeated patterns. In examples like the one above, you can avoid putting a helper function in your runtime library and instead rely on R8 to de-duplicate bytecode when it's repeated enough. And because R8 is doing whole-program analysis, unrelated code which happens to have the same bytecode patterns participate in the de-duplication.

It's also interesting to think about how this interacts with Kotlin's `inline` function modifier. The more you use inline functions (and especially if you invoke inline functions inside other inline functions) the more likely you are to have R8 outline some of the function body back into a regular method. Make sure that you're using `inline` for things like `reified` generics or to avoid allocating lambda objects as it's intended.

In the [previous post about R8](/r8-optimization-class-constant-operations/) I teased that the next post (aka this one) would cover an optimization that created `const-class` bytecodes. After writing two posts outside of this series on generated code and having the discussion on the Moshi change, however, it felt like a natural progression to cover outlining. With outlining out of the way the next R8 post will get back on track with producing `const-class` bytecodes.