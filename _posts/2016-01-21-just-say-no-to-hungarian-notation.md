---
title: Just Say mNo to Hungarian Notation
layout: post

categories: post
tags:
- Android

lead: Hungarian notation is a plague on Android development. Its proliferation is nothing more than a misinterpretation and justification an erroneous copy/paste.
---

Every day new Java code is written for Android apps and libraries which is plagued with an infectious disease: Hungarian notation.

The proliferation of Hungarian notation on Android is an accident and its continued justification erroneous. Let's dispel its common means of advocacy:

 *  "**The Android Java style guide recommends its use**"

     There is no such thing as an Android Java style guide that provides any guidance on how *you* should write Java code. Most people referencing this non-existent style guide are referring to the [style guide for contributions to the Android Open Source Project (AOSP)](http://s.android.com/source/code-style.html#follow-field-naming-conventions).

     You are not writing code for AOSP so you do not need to follow their style guide.

     If you're working on code that might someday live in AOSP you don't even need to follow this style guide. Almost all of the Java libraries imported by AOSP do not follow it, and even some of the ones developed inside of AOSP don't either.

 *  "**The Android samples use it**"

    These samples started life in the platform inside of AOSP so they adhere to the AOSP style. For those which did not come from AOSP, the author either incorrectly believes the other points of advocation in this post or simply forget to *correct* their style when writing the sample.

 *  "**The extra information helps in code review**"

    The 'm' or 's' prefix on name indicates a private/package instance field or private/package static field, respectively, where this would otherwise not be known in code review. This assumes the field isn't visible in the change, since then its visibility would obviously be known regardless.

    Before I attempt to refute this, let's define Hungarian notation. [According to Wikipedia](https://en.wikipedia.org/wiki/Hungarian_notation#Systems_vs._Apps_Hungarian), there are two types of Hungarian notations:

    * System notation encoded the data type of the variable in its name. A user ID that was a `long` represented in Java would name a variable `lUserId` to indicate both usage and type information.
    * Apps notation encoded the semantic use of the variable rather than it's logical use or purpose. A variable for storing private information had a prefix (like `mUserId`) whereas a variable for storing public information had another prefix, or none whatsoever.

    So when you see the usage of a field, which piece of information is more important for the review: the visibility of that field or the type of that field?

    The visibility is a *useless* attribute to care about in a code review. The field is already present and available for use, and presumably its visibility was code-reviewed in a previous change. The type of a field, however, has a direct impact on *how* that field can being used in the change. The correct methods to call, the position in arguments, and the methods which can be called all are directly related to its type.

    Not only is advocating for 'apps' Hungarian wrong because it's not useful, but it's doubly wrong since 'system' Hungarian would provide more relevant info. That's not to say you should use 'system', both the type and visibility of a field changes and you will forget to update the name. It's not hard to find [static `mContext` fields](https://github.com/square/leakcanary/blob/4950e1756c79fba871f524d5d9c47ed9322b23b3/leakcanary-android/src/main/java/com/squareup/leakcanary/AndroidExcludedRefs.java#L263-L269), after all.

 *  "**The extra information helps in development**"

    Android Studio and IntelliJ IDEA visually distinguish field names based on membership (instance or static):

    <img src="/static/post-image/hungarian-idea.png" width="283">

    IDEs will enforce correct membership, visibility, and types by default so a naming convention isn't going to add anything here. A popup showing all three properties (and more) of a field is also just a keypress away.

 *  "**I want to write Java code like Google does**"

    While Android and AOSP are part of the company, Google explicitly and actively forbids Hungarian notation [in their Java style guide](https://google.github.io/styleguide/javaguide.html#s5.1-identifier-names). This public Java style guideline is the formalization of long-standing internal conventions.

    Android had originated outside of Google and the team early on chose to host the Hungarian disease. Changing it at this point would be needless churn and cause many conflicts across branches and third-party partners.

With your continued support and activism on this topic, this disease can be eradicated in our lifetime.

`mFriends` don't let `sFriends` use Hungarian notation!
