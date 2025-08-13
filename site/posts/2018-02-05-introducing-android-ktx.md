---
title: "Introducing Android KTX: Even Sweeter Kotlin Development for Android"
layout: post

external: true
blog: Android Developers Blog
blog_link: https://android-developers.googleblog.com/2018/02/introducing-android-ktx-even-sweeter.html

categories: post
tags:
- Android
- Kotlin
---

_Written with [Florina Muntenescu](https://twitter.com/FMuntenescu) and [James Lau](https://twitter.com/jmslau)_

Today, we are announcing the preview of Android KTX - a set of extensions designed to make writing Kotlin code for Android more concise, idiomatic, and pleasant. Android KTX provides a nice API layer on top of both Android framework and Support Library to make writing your Kotlin code more natural.

The portion of Android KTX that covers the Android framework is now available in our [GitHub repo](https://github.com/android/android-ktx/). We invite you to try it out to give us your feedback and contributions. The other parts of Android KTX that cover the Android Support Library will be available in upcoming Support Library releases.

https://www.youtube.com/watch?v=kmvS3sZF_y0

Let's take a look at some examples of how Android KTX can help you write more natural and concise Kotlin code.

### Code Samples Using Android KTX

#### String to Uri

Let's start with this simple example. Normally, you'd call `Uri.parse(uriString)`. Android KTX adds an extension function to the String class that allows you to convert strings to URIs more naturally.

```kotlin
// Kotlin
val uri = Uri.parse(myUristring)
```
```kotlin
// Kotlin with Android KTX
val uri = myUriString.toUri()
```

#### Edit SharedPreferences

Editing SharedPreferences is a very common use case. The code using Android KTX is slightly shorter and more natural to read and write.

```kotlin
// Kotlin
sharedPreferences.edit()
    .putBoolean(key, value)
    .apply()
```
```kotlin
// Kotlin with Android KTX
sharedPreferences.edit { 
    putBoolean(key, value) 
}
```

#### Translating path difference

In the code below, we translate the difference between two paths by 100px.

```kotlin
// Kotlin
val pathDifference = Path(myPath1).apply {
   op(myPath2, Path.Op.DIFFERENCE)
}

val myPaint = Paint()

canvas.apply {
   val checkpoint = save()
   translate(0F, 100F)
   drawPath(pathDifference, myPaint)
   restoreToCount(checkpoint)
}
```
```kotlin
// Kotlin with Android KTX
val pathDifference = myPath1 - myPath2

canvas.withTranslation(y = 100F) {
   drawPath(pathDifference, myPaint)
}
```

#### Action on View onPreDraw

This example triggers an action with a View's onPreDraw callback. Without Android KTX, there is quite a bit of code you need to write.

```kotlin
// Kotlin
view.viewTreeObserver.addOnPreDrawListener(
    object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            actionToBeTriggered()
            return true
        }
    })
```
```kotlin
view.doOnPreDraw { actionToBeTriggered() }
```

There are many more places where Android KTX can simplify your code. You can read the [full API reference documentation](https://android.github.io/android-ktx/core-ktx/) on GitHub.


### Getting Started

To start using Android KTX in your Android Kotlin projects, add the following to your app module's `build.gradle` file:

```groovy
repositories {
    google()
}

dependencies {
    // Android KTX for framework API
    implementation 'androidx.core:core-ktx:0.1'
    ...
}
```

Then, after you sync your project, the extensions appear automatically in the IDE's auto-complete list. Selecting an extension automatically adds the necessary import statement to your file.

Beware that the APIs are likely to change during the preview period. If you decide to use it in your projects, you should expect breaking changes before we reach the stable version.


### androidx: Hello World!

You may notice that Android KTX uses package names that begin with `androidx`. This is a new package name prefix that we will be using in future versions of Android Support Library. We hope the division between `android.*` and `androidx.*` makes it more obvious which APIs are bundled with the platform, and which are static libraries for app developers that work across different versions of Android.


### What's Next?

Today's preview launch is only the beginning. Over the next few months, we will iterate on the API as we incorporate your [feedback and contributions](https://github.com/android/android-ktx#how-to-contribute). When the API has stabilized and we can commit to API compatibility, we plan to release Android KTX as part of the Android Support Library.

We look forward to building Android KTX together with you. Happy Kotlin-ing!