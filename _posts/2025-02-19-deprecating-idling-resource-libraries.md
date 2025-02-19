---
title: 'Deprecating idling resource libraries'
layout: post

categories: post
tags:
- Android
---

When [Espresso](https://developer.android.com/training/testing/espresso) was made public a decade ago, one of its banner features was the "idling resource" concept.
This monitored the main thread and any background thread pools to prevent your test from progressing until the app became idle.
Waiting until idle generally increased the stability of tests since at that point the UI should be stable.

We released [RxIdler](https://github.com/square/RxIdler) and [okhttp-idling-resource](https://github.com/JakeWharton/okhttp-idling-resource/) for monitoring RxJava schedulers and OkHttp's dispatcher, respectively.
Today I am deprecating both libraries.
In the years since their release, I have become disillusioned with the idling resource mechanism–and I'm not alone.

Like using `R.id` to target views, idling resources expose the internals of your application to the testing framework in a way that no real user can match.
The point of building tests in [the robot pattern](/testing-robots/) was to describe interaction at a high-level.
If you can't read a UI test to someone over the phone interacting with the real app then it probably encodes implementation detail. 

> "Okay dad, now wait for OkHttp's `Dispatcher` to report itself as idle before clicking 'continue'."

Yeah… no.

What do we do as real users?
We wait until some UI condition is met which signals our ability to progress.

> "Okay dad, now wait for the 'continue' button to turn green before clicking it."

Much better.

We don't care _how_ the application is performing the work nor the means by which it signals the UI that it is complete.
Moreover, test failures that occur based on condition waits are failures which can occur in the wild.

I've been sitting on these deprecations and this blog post for a few years now.
Telling you to switch to a new technique without actually demonstrating it is not great.
Turns out that around the same time [Google was also changing their tune](https://medium.com/androiddevelopers/alternatives-to-idling-resources-in-compose-tests-8ae71f9fc473) on idling resources.
That guidance has since been promoted to [the official documentation](https://developer.android.com/training/testing/instrumented-tests/stability#prevent-synchronization) as well.
These links demonstrate how to wait on conditions using new built-in Compose testing APIs.

![Flow chart showing 'click on button' pointing to 'is the condition met'. Its 'no' branch recurses onto itself. The 'yes' branch points to 'Assert text is displayed'. (Image courtesy developer.android.com)](/static/post-image/condition-wait.png)

For View-based layouts, you can write a custom `ViewAction` that loops on yielding to the main thread, checking the condition, and then either breaking or looping.
Yes I know I'm still not _really_ demonstrating how to do this for views.
Sorry!

Both of these idling resource libraries are stable and reliable.
They haven't needed any commits or releases in years.
If you are relying on them today then absolutely nothing is changing for you.
Deprecation is a signal to new users that this is not the recommended approach.
And it's a nudge to existing users that they can migrate at their own pace to a superior solution.
