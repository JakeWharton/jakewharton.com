---
title: ActionBarSherlock - A Love Story (Part 1)
layout: post

categories: post
tags:
- Android
- ActionBarSherlock

lead: I have never formally introduced ActionBarSherlock in a blog post.
source: http://actionbarsherlock.com

redirect_from:
- /post/13619849069/actionbarsherlock-a-love-story-part-1
---

The library has been out for nearly 9 months already and has [seen 22 releases](https://github.com/JakeWharton/ActionBarSherlock/tags) across 3 major versions. Fast approaching is the next major release, version 4.0. This will bring the full functionaity of Ice Cream Sandwich’s action bar to all relevant APIs and will be what I consider the first true release. I’m going to be writing a series of posts which talks about some various development decisions and the reasoning behind them as well as a bit of the history of the library. The series will culminate in the formal announcement and release of v4.0 some time in the near future. So strap in and hold on, with this first post I’m just going to tear off the band-aid…

I am officially dropping 1.6 support from ActionBarSherlock 4.0. This post originally was going to be [a call for arguments](https://twitter.com/JakeWharton/status/142402144874663936) against this, but with the release of the [latest platform distributions](http://developer.android.com/resources/dashboard/platform-versions.html) I’m rounding 1.6’s share down to 1% which was my mental event horizon for its support.

This will likely upset a few people. I know of a lot of apps leveraging my library that still support 1.6. I even have [an implementer who supports 1.5](https://market.android.com/details?id=com.strategiesinsoftware.erg) because he has an extremely niche market with a bunch of users who own the Motorola i1!

I’ve said time and time again to developers that as long as the compatibility library supported 1.6 then so would I. For ActionBarSherlock 3.x this was an important thing because the library was so tightly integrated with the compat lib that it actually became a near drop-in replacement for it since its classes were included.

**This will all change with ABS 4.0.** I have chosen to take the library in a completely separate direction. While the next post will talk about the trials and tribulations of dealing with having a library which extends (and effectively replaces) the official compat lib, all we need to know for now is that the core library will now have zero dependencies.

Since the library is no longer immediately dependent on the compatibility library my justification for forcing support for Android 1.6 is no longer there. Now this is not to say that ActionBarSherlock 4.0 won’t be supporting tight integration with the compat lib—it most certainly will. I have instead simplified the main component of the library, the action bar, to stand alone.

As such, we can now focus solely on the betterment of the functionality that we’ve all come for. And with that comes waving a fond farewell to the version which I consider to be Android’s true “1.0”, API level 4.

To those that support Android 1.6 I commend you. It is in all sense of the word a bastard version. Development on Android was exploding and 1.5’s shortcomings were vastly documented (I’ll spare the links here) so 1.6 brought a bit of fresh air. In my opinion it was the first version of the platform that had the future in mind. Unfortunately for it, so were the next two API levels, the now defunct 2.0 and 2.0.1. Stability finally started to arrive in API 7, Android 2.1, which is to be the new minimum target of the library. This period of unrest saw the death of my primary argument against support 1.6, its classloader.

Android’s 1.6 classloader is an over-eager know-it-all. It seeks out and checks every method call present in your loaded (key word!) classes. This means that even if you’ve blocked out a section in a check against `Build.VERSION.SDK_INT`, Android 1.6 will check every method inside. This can be easily remedied by surrounding these calls in [concise static inner-classes](https://github.com/JakeWharton/ActionBarSherlock/blob/b0043b245eac671646b019dbbff55b2a4ec278c6/library/src/com/actionbarsherlock/internal/view/menu/ActionMenuPresenter.java#L121-126) which is annoying, but doable. Problems arise when you [need to call the superclasses version of a method you have implicitly overriden](https://github.com/JakeWharton/ActionBarSherlock/blob/da0bfadd1d546f97b92d9a93d028d2ac0113b49f/library/src/android/support/v4/app/FragmentActivity.java#L1010-1013). You’re out of luck.

It turns out that the latter comes into play a lot in the ICS version of the action bar. Things such as accessibility and configuration changes on views are unable to call up to their superclass implementations. While probably not a show-stopper, couple this with constantly having to battle the former situation and it becomes a seemingly neverending battle of abstraction to these static classes.

My choice to drop Android 1.6 mostly comes out of wanting to be able to more rapidly develop and ship the library. I charge you, the reader (and hopefully ActionBarSherlock user), with the responsibility of determining whether maintaining support for 1.6 is feasible and also implementing it if you deem it so.

I’m sure some users will be still be angered by this decision despite the above explanation. Having developed, supported, and maintained this library for 9 months completely in my spare time and never charging a penny, I say, “[Show me the money.](http://www.youtube.com/watch?v=OaiSHcHM0PA)” If you want to sponsor development of 1.6 support I will make every effort possible towards the effort. However, I’d rather you forked it, did it yourself, and sent a pull request… or better yet, just let it die.

Pour one out for Android 1.6 and all the users of it.

*In the next installment of this series I will be talking about the history of ActionBarSherlock and how version 4.0 is a return to its roots.*
