---
title: ActionBarSherlock - A Love Story (Part 2)
layout: post

categories: post
tags:
- Android
- ActionBarSherlock

lead: ActionBarSherlock is currently sitting right smack in the middle of the third major version with development on the fourth underway.
---

Despite this, however, most users probably have never used the first or second versions, let alone the “lost” third version which was scrapped just hours from release. In future posts we’ll look forward to where version four will take us. For this post, however, we’ll be taking a quick look back at the origins of the library.

Like most libraries, ActionBarSherlock was birthed out of personal necessity. With my recent migration of the majority of our servers at work to VMWare and the vSphere platform in January 2011, I wanted an app which allowed me to view essential VM information and perform quick vMotions from my phone. At this time there was only two apps of exceptionally inferior quality on the Android Market which offered such functionality—neither being open source. It’s easy to guess what happened next: I began down the path of writing my own.

Writing an app which interfaces with vCenter Server (essentially vSphere’s coordination hub) is no trivial task. I spent a month porting the Java SDK to run on Android. During this process of ripping and replacing I ended up writing my own SOAP client which married aggressive caching with lazy loading objects. During this I discovered a lot of fun little-known facts about Android and Dalvik (Did you know that when reflecting on a class’ properties Android will return them in alphabetical order while desktop Java returns them in declaration order?). After about one month I had a mostly-working API wrapper which led to the next logical step, creating the application shell.

At that time, and much to my amusement today, I thought [GreenDroid](http://greendroid.cyrilmottier.com/) to be the end-all, be-all library for implementing the common user interface patterns easily. If you’re following the timeline in your head, you might have already guessed that the Honeycomb SDK had landed this very same week and I chose to be forward thinking and support both phones and tablets with a single APK. This would, however, necessitate a bit of work since we were pre-Android Compatibility Library. I set off to adopt a proxy the action bar API of both GreenDroid and Honeycomb in a single custom API.

The [first version](https://github.com/JakeWharton/ActionBarSherlock/tree/1.0.0) was completed in one day. It proxied only the methods which I needed and required you implement two static inner-classes to handle the pre- and post-Honeycomb configurations. I [discovered](https://github.com/cyrilmottier/GreenDroid/issues/18) that both GreenDroid and Android used the `getActionBar()` method name which required a last-minute switch to [Hameno’s fork](https://github.com/hameno/GreenDroid) where it was changed to `getGDActionBar()`. I had also discovered the compatibility library had launched and hastily slapped a mention of compatibility in the README file.

If you followed the link above you’ll notice that the entire library was [a single class file](https://github.com/JakeWharton/ActionBarSherlock/blob/1.0.0/ActionBarSherlock.java#files) and [a rudimentary sample](https://github.com/JakeWharton/ActionBarSherlock/blob/1.0.0/sample/src/com/jakewharton/android/actionbarsherlock/sample/HelloActionBarActivity.java#files)&mdash;a far cry from what it’s grown into today. Not very impressive, comprehensive, or even useful. I still dealt with menu inflation and action item creation manually!

The [second version](https://github.com/JakeWharton/ActionBarSherlock/tree/2.0.0) was released the next day, a complete rewrite. I came to realize that GreenDroid just wasn’t going to cut it despite its numerous beautiful widgets and added better support for implementing your own “pre-Honeycomb” handler. [Android-ActionBar](https://github.com/johannilsson/android-actionbar) support was added, my new library of choice for the vSphere client app (you almost forgot, didn’t you).

Over the next few weeks I managed to get simple screens of the application working such as listing VMs, viewing their information, and browsing things like the datacenter objects and datastores. As more screens were introduced, a more dynamic action bar was required and as such I implemented more and more of the native ActionBar API. Two weeks after 2.0.0, I released [version 2.1.0](https://github.com/JakeWharton/ActionBarSherlock/blob/2.1.0/CHANGELOG.md#readme) which represented the first step towards where the library exists today. The compatibility was a required dependency, Maven was adopted as the build and release system, and APIs such as list navigation, menu inflation, and Fragment support was added.

At this point there were a handful of users who knew of the library and likely even less using it, progress on the VM app had stalled because of issues in getting my custom SOAP client working properly, and I had become aware of just how limiting my custom API really was. The following code snippet was taken from one of the samples of version 2.1.1:

```java
ActionBarSherlock.from(this)
    .with(savedInstanceState)
    .layout(R.layout.activity_hello)
    .menu(R.menu.hello)
    .homeAsUp(true)
    .title(R.string.hello)
    .handleCustom(ActionBarForAndroidActionBar.Handler.class)
    .attach();
```

While there was nothing fundamentally wrong with this API in the context of my app, requests were coming in for the support of other action bar features. Constantly implementing methods for every item was clearly going to create a mess of a library. I chose once again to embark on a rewrite to afford a more flexible model.

Over the next month I reworked the entire library to be based on interfaces which provided a single action bar feature. This way, you could drop-in whatever third-party action bar library you wanted and only implement the feature interfaces in its handler that the library supported. On May 12th, 2011 [the code](https://github.com/JakeWharton/ActionBarSherlock/tree/83283d9f3eecc79762060f3dbcdbf09975c890f2) was feature complete and ready for a 3.0.0 release. This tree, `83283d9f`, is the “lost” 3.0.

At some point during that evening&mdash;and I have no recolection of the exact moment&mdash;I had an epiphany.

> If I am providing an API for various action bar methods through a custom class, why am I not just providing the full API through a `getSupportActionBar()` method exactly like the compatibility library that I’m already dependent on?

It turns out that adapting my code to provide the full action bar API was the easy part and didn’t take long. The majority of the next month was spent working with Johan Nilsson to [expand Android-ActionBar’s feature set](https://github.com/johannilsson/android-actionbar/pull/25) to very nearly match that of Honeycomb.

Finally, on June 5th, 2011, version 3.0.0 was released which fully internalized the Android-ActionBar sources for a seamless mirroring of the native API on Android 1.6 and newer.

Releases on the 3.x branch [came steadily](https://github.com/JakeWharton/ActionBarSherlock/tags) over the next few months culminating in [the release of 3.5.0](https://twitter.com/#!/JakeWharton/status/148585409708965889) last night. If you’re reading this, you likely came aboard the ActionBarSherlock ship sometime during this time and probably reasonably familiar with how it operates. Through the support and contributions of the community it’s become quite a useful library—even solving problems such as supporting MapViews in fragments and preference activities.

I want to especially thank [Cyril Mottier](https://github.com/cyrilmottier) of GreenDroid fame and [Johan Nilsson](https://github.com/johannilsson) of Android-ActionBar fame. Despite now being my competition (not really), without their efforts the library would likely not exist. Thank you to all of the users who sent in pull requests. Thank you [Chris Banes](https://github.com/chrisbanes) for fleshing out device support and providing tiny bug fixes and enhancements.

Thank you to all of the implementations. [SeriesGuide](https://market.android.com/details?id=com.battlelancer.seriesguide), [RateBeer](https://market.android.com/details?id=com.ratebeer.android), [FriendCaster](https://market.android.com/details?id=uk.co.senab.blueNotifyFree), [Minus](https://market.android.com/details?id=com.minus.android), [Cargo Decoder](https://market.android.com/details?id=com.strategiesinsoftware.erg), [Folder Organizer](https://market.android.com/details?id=com.abcOrganizer), [mAnalytics](https://market.android.com/details?id=com.mugitek.analytics), [Traktoid](https://market.android.com/details?id=com.florianmski.tracktoid), [CrossFit Travel](https://market.android.com/details?id=com.agilevent.crossfittravel), [BubbleUPnP](https://market.android.com/details?id=com.bubblesoft.android.bubbleupnp), [Bird Bar](https://market.android.com/details?id=com.mikedg.android.bar.lite), and the many, many more! *(I’m working on a webapp to organize all of the implementations)*

Now, for the second time, did you remember this blog post was about a vSphere application? The explosion of interest in ActionBarSherlock as well some of my other projects overwhelmed my free time and coupled with my unstable SOAP client I never was able to make any more progress. In fact, I have never written an application using any of my own libraries. *…yet!*

This week we’re not pouring one out, but rather I raise my glass to all of you, the ActionBarSherlock community. Cheers! See you at 4.0.

*In the next installment of this series I will begin to talk about where version 4.0 will take us and how it is a return to its roots.*
