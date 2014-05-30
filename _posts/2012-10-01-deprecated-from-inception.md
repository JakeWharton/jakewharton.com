---
title: Deprecated From Inception
layout: post

categories: post
tags:
- Android
- ActionBarSherlock

lead: Despite being designed to normalize the action bar API and theming across all modern versions of Android, the library has been deprecated from day one.
---

Stop using ActionBarSherlock!  Well... eventually.

If you are writing an application right now with a `minSdkVersion` lower than 14 you should be using it. I am not saying this just as the developer of the library, but as someone who likes to minimize wasted time.

Writing applications is hard and nobody wants to spend time writing boilerplate code. It sucks valuable engineering time away from what is most important in your app: the content. Throw in ActionBarSherlock and a slew of other open source libraries to bootstrap your development so you can make the best app possible.

In order to have pleasant longevity, libraries you integrate into your application should be as small and modular as possible. Inevitably new libraries will be released and some will have features that you wish to integrate at the cost of others. If a library is small, these replacements can be seamless (and often times drop-in).

ActionBarSherlock is very much the opposite of a small, modular library. When you decide to include ActionBarSherlock in your app you are committing to using it for the foreseeable future. Or are you?

By design, ActionBarSherlock uses the native action bar API and theming. Yes you have to use types that exist in different packages and duplicate theme attributes but the names of classes, methods, and attributes mirror their native counterparts exactly. In fact, you have to go out of your way in order to find things that are not API-compatible with the native action bar.

Some day, market saturation and the demands of your application will necessitate updating your `minSdkVersion` to 14 (or higher).

If you are using a different action bar library or you have rolled your own then you will either have zero work ahead of you (which means you will still be using your crappy implementation) or you have a lot of work ahead of you (migrating to use the native components). Both of these situations are not ideal and I can think of handfuls of massively popular apps who will someday find themselves in this predicament.

On the other hand, if you happen to be using ActionBarSherlock, all you have to do is switch from using the custom types back to the native types. This switch is so easy that 99% of it can be scripted. It boils down to changing a few imports, replacing calls to `getSupportActionBar` with `getActionBar`, and using a `Holo` parent theme rather than a `Sherlock` one.

**This is the entire purpose of the library. It has been designed for this use case specifically. It has been deprecated from inception with the intention that you could some day throw it away.**

The process also works in reverse, in fact. At AnDevCon III I gave a talk where I migrated multiple code examples written for the native action bar to work with ActionBarSherlock. The entire process took less than a few minutes and the result was code that only ran on ~8% of devices (at the time) now being able to be run on about 91% of devices.

Google has announced that they are working on a library that will backport a subset of the action bar. I was disappointed in this anouncement for two reasons: it was announced 12 months too late (and remains yet unreleased) and it is wasting engineering time of a talented developer. There are so many gaping holes in Android development where their time could be better spent. If they wanted to come out with a backport then it should have been done at the same time as ICS dropped.

Let us hope they honor the notion of deprecation from inception as well, otherwise they will only make things worse.

*Follow the discussion on [Google+][1] and [Reddit][2].*

 [1]: https://plus.google.com/u/1/108284392618554783657/posts/SA1KF2uHBnM
 [2]: http://www.reddit.com/r/androiddev/comments/10rybj/actionbarsherlock_deprecated_from_inception/
