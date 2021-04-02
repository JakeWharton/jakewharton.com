---
title: An Optional's place in Kotlin
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/an-optionals-place-in-kotlin

categories: post
tags:
- Kotlin
- "Square Open Source ♥s Kotlin"
---

With nullability being a first-class citizen in Kotlin’s type system, the need for an `Optional` type seems all but diminished. Just because you can explicitly express nullability, however, does not mean that null is always allowed.

For example, Retrofit provides adapters for RxJava 1.x and 2.x which allow modeling your requests as a single-element stream.

```kotlin
interface MyApiService {
  @GET("/api/user/settings")
  fun userSettings(): Observable<Settings>
}
```

RxJava 2 differs from RxJava 1 in that it does not allow null in its streams. If we’re using RxJava 2 and the converter for `Settings` returns null an exception will occur. Thus, in order to represent the absence of a value for the response body inside this stream we need an abstraction like `Optional`.

[Retrofit 2.3.0](https://github.com/square/retrofit/blob/master/CHANGELOG.md#version-230-2017-05-13) introduces two new _delegating_ converters for the `Optional` types in Guava and Java 8. Unlike the other converters for libraries like Moshi, Gson, and Wire, these new ones are different in that they don’t actually convert bytes to objects. Instead, they delegate to other converters for processing the bytes and then wrap the potentially-nullable result into an `Optional`.

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://example.com")
    .addConverterFactory(Java8OptionalConverterFactory.create())
    .addConverterFactory(WireConverterFactory.create())
    .addCallAdapter(RxJava2CallAdapterFactory.create())
    .build()
```

With one of the `Optional` converters added alongside a serialization converter, requests whose bodies may deserialize to null can be changed to instead return an `Optional`.

```kotlin
interface MyApiService {
  @GET("/api/user/settings")
  fun userSettings(): Observable<Optional<Settings>>
}
```

Today’s [Retrofit 2.3.0](https://github.com/square/retrofit/blob/master/CHANGELOG.md#version-230-2017-05-13) release contains the same JSR 305 annotations for explicit nullability in Java and Kotlin as [our recent Okio and OkHttp releases](https://developer.squareup.com/blog/rolling-out-nullable). As we’ve seen above, though, just having these annotations​ or a type system that can model nullability sometimes is not enough. For these cases, in Java and Kotlin alike, the use of `Optional` has its place.

This post is part of Square’s “[Square Open Source ♥s Kotlin](https://developer.squareup.com/blog/square-open-source-s-kotlin series.