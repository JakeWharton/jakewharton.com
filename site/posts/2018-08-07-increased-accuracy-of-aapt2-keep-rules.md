---
title: Increased accuracy of aapt2 "keep" rules
layout: post

categories: post
tags:
- Java

lead: "The `aapt2` tool packages Android application resources but also generates \"keep\" rules for ProGuard or R8. Starting with AGP 3.3.0-alpha05 the rules allow more unused code to be removed from your release APK."
---

The `aapt2` tool packages your Android application resources into the format used at runtime. It also generates "keep" rules for ProGuard or R8 so that the types referenced inside of your resources do not get removed. Views referenced only in layout XML, action providers referenced only in menu XML, and broadcast receivers referenced only in the manifest XML are some examples of types that would otherwise be removed from the final APK were it not for these rules.

Prior to version 3.3.0-alpha05 of the Android Gradle plugin, `aapt2` would generate "keep" rules for the constructors of these types using an argument wildcard. Some rules for an application class, activity class, and view reference look like this:
```
# Referenced at frontend/android/build/intermediates/merged_manifests/release/AndroidManifest.xml:20
-keep class com.jakewharton.sdksearch.SdkSearchApplication { <init>(...); }
# Referenced at frontend/android/build/intermediates/merged_manifests/release/AndroidManifest.xml:28
-keep class com.jakewharton.sdksearch.ui.MainActivity { <init>(...); }
# Referenced at search/ui-android/build/intermediates/packaged_res/release/layout/search.xml:57
-keep class android.support.v7.widget.RecyclerView { <init>(...); }
```

Dumping the methods of the release APK we get:
```
com.jakewharton.sdksearch.SdkSearchApplication <init>()
com.jakewharton.sdksearch.ui.MainActivity <init>()
android.support.v7.widget.RecyclerView <init>(Context)
android.support.v7.widget.RecyclerView <init>(Context, AttributeSet)
android.support.v7.widget.RecyclerView <init>(Context, AttributeSet, int)
```

`SdkSearchApplication` and `MainActivity` contain only a default constructor but `RecyclerView` contains three. As far as the reflective lookup is concerned, only one constructor will be used. For types in the manifest the default (no-argument) constructor is used. For types in a layout XML file the two-arg `Context`+`AttributeSet` constructor is invoked by `LayoutInflater`. By generating rules with `<init>(...)` we are forcing every constructor to be retained despite only needing one.

Starting with version 3.3.0-alpha05 of the Android Gradle plugin, a new version of `aapt2` is used which generates more precise rules that reference only the exact constructor which the reflective lookup will use.
```
# Referenced at frontend/android/build/intermediates/merged_manifests/release/AndroidManifest.xml:20
-keep class com.jakewharton.sdksearch.SdkSearchApplication { <init>(); }
# Referenced at frontend/android/build/intermediates/merged_manifests/release/AndroidManifest.xml:28
-keep class com.jakewharton.sdksearch.ui.MainActivity { <init>(); }
# Referenced at search/ui-android/build/intermediates/packaged_res/release/layout/search.xml:57
-keep class android.support.v7.widget.RecyclerView { <init>(android.content.Context, android.util.AttributeSet); }
```

Dumping the methods of the release APK again now shows:
```
com.jakewharton.sdksearch.SdkSearchApplication <init>()
com.jakewharton.sdksearch.ui.MainActivity <init>()
android.support.v7.widget.RecyclerView <init>(Context, AttributeSet)
android.support.v7.widget.RecyclerView <init>(Context, AttributeSet, int)
```

The `<init>(Context)` of `RecyclerView` is no longer present! That constructor used to be forced into the release APK despite never actually being used. The three-argument constructor is still kept is because the two-argument one delegates to it:
```java
public RecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
}
```
If optimization is also enabled and there are no other uses of that three-argument constructor it may get inlined–something that couldn't have happened with the old rules. 

This seems like a small change, and it mostly is. Application, activity, and action provider subtypes tend to only have the one constructor so their counts are unlikely to change. View subtypes, however, very frequently have three or four constructors and you will likely now see two or three of those being removed. In the scope of an entire APK that allows on the order of tens or hundreds of methods to be removed which were _needlessly_ being kept. As the specificity of "keep" rules increases it not only reduces the raw number of methods that wind up in the final APK, but often allows optimization passes to have a greater effect.

If you find any bugs with the new rules, please report them on the [Android issue tracker](https://issuetracker.google.com/issues/new?component=192709).