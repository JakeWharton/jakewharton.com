---
title: ActionBarSherlock - A Love Story (Part 3)
layout: post

categories: post
tags:
- Android
- ActionBarSherlock

lead: ActionBarSherlock is dead. Long live ActionBarSherlock.
---

I am talking, of course, about version 3 and version 4, respectively. And I’m also lying a bit because I won’t just be abandoning the 3.x users either. I’ll give you a two-month deprecation window from version 4’s release. Because…

**ActionBarSherlock v4 is coming and it is awesome.**

Now I realize that I am a bit biased, but let me explain how this version is the first version that I think I will be truly proud of.

 1. No more shuffling between native and custom implementations.

    Google’s support library operates in this way in that it makes no attempt to use any native implementations even if they exist. It is far easier and more stable to keep all of the functionality in the library. Plus, the Android 4.0 action bar has been designed to accommodate every conceivable screen size that the platform can run on so why should we continue bothering to switch to the native implementation?

    Additionally, this change became more and more of an apparent need rather than a choice due to changes in Android 4.0’s `MenuItem` interface.

 2. The support library classes are no longer included in the core of the library.

    Though somewhat ironic based on the last point, the decision to allow the library to stand alone was made in order to accommodate developers who were uncomfortable using a custom built version of the support library (or who even didn’t use it at all).

    A version of the support library will be provided as a plugin .jar that has been modified to add ActionBarSherlock support. The changes to the library will be kept at a minimum and will not include any unrelated fixes. File bugs on b.android.com for that, please.

    **WARNING:** This means that if you are using `FragmentMapActivity` or using fragments in `SherlockPreferenceActivity` you WILL have to change your implementation or create your own versions of these base classes. I will no longer be maintaining support for these.

 3. Extending from a custom base activity is no longer required (but still recommended).

    Similar to how ActionBarSherlock v1 and v2 operated, you can perform static attachment of the action bar to your activities. This allows for the use of alternate base activities such as those provided by other third-party libraries (e.g., RoboGuice).

    The added side-effect of this is that all of the interaction logic has been placed within this single class which is also the one used by the base activities. This means that whether you do use a base activity or choose to interact with the static attachment you are afforded the full API.

 4. Fully mirrored theming support to mimic the native action bar.

    Forget the ‘ab’-prefixed attributes of v3.x, v4 now allows for defining proper styles for the action bar, action mode, and various other sub-components of the action views.

 5. It is the Ice Cream Sandwich action bar!

    …but you probably knew that already.

     Split action bar, action modes, action providers, condensed tab navigation, and so much more!

I have been working on this for nearly 8 weeks now so it’s easy for me to get excited. Starting tomorrow the version 4 beta will be officially announced and detailed in a much more technical manner so that you can begin testing and hopefully join in the excitement.

As it stands now there are still large bugs and “bugs” with version 4. You can find them [under milestone 4.0.0](https://github.com/JakeWharton/ActionBarSherlock/issues?milestone=4&state=open) on the GitHub issue tracker. As always, code contributions are welcomed and encouraged.

There is no timeline yet for the final release. There will be one or two release candidates before which is when I will be working with a few devs on real implementations to determine any problems that exist. If everything goes smoothly the final release will not be far behind that.

Thank you everyone for your support thus far. Happy new year to all.
