---
title: Announcing ActionBarSherlock Version 4.0
layout: post

categories: post
tags:
- Android
- ActionBarSherlock

lead: After months of development and amazing feedback from developers the first official release of version 4 is available.
image: /static/post-image/actionbarsherlock-logo.png
source: http://actionbarsherlock.com
---

It's been approximately three months since the Android 4.0 Ice Cream Sandwich source code landed on the Android Open Source Projects's servers. Since then I have spent countless nights developing what I described in an [earlier blog post][1] as the "first true release" of ActionBarSherlock: version 4.0. As of 11:57:57PM PST I have finally tagged and released this new revolutionary version of the library into the wild for your consumption.

This date of release was chosen because of its historical significance within the scope of ActionBarSherlock. Exactly one year ago the first version, 1.0, was tagged and released. If you read the [history lesson blog post][3] you'll know that this version only lasted 24 hours before 2.0 came along but it still represents the very first milestone. Version 4.0 is another huge milestone so the fact that it is occurring on the same date should help convey its significance.

For those who are not aware, version 4.0 is a feature-complete backport of the Android 4.0 action bar and its supporting widgets to Android 2.x and up. This allows applications to integrate a 100% API and theme-compatible action bar with extremely little effort. Rather than worrying about interfacing with a custom third-party action bar or adding features to the ActionBarCompat sample, developers can now drop in ActionBarSherlock and focus on the most important part of their app, the content!

In the next 48 hours I plan to fill out the release a bit with a proper migration guide for applications coming from the v3.x branch along with some minor updates to the [website][4] with new screenshots. Savy developers should already have all they need, however. The library is [available for download][5] on the website along with its sample applications--the source code to which is included in the download.

For those who would like to stay up-to-date with the library in a more automated fashion you can download its samples from the newly re-branded Play Store:

 * [ActionBarSherlock: Demos Sample][9]
 * [ActionBarSherlock: Fragments Sample][10]
 * [ActionBarSherlock: RoboGuice Sample][11]

I would like to issue a special thank you to all the developers with whom I have worked closely with on the development of this release and for all of the detailed feedback, bug reporting, and (my personal favorite) pull requests. Some of these developers even have already released applications which are using early versions of the library and I urge you to show them your support as well:

 * [SeriesGuide][12] (well, the [beta][13] is)
 * [GitHub Gaug.es][14]
 * [Mentions][15]

If you release an application that uses ActionBarSherlock version 4 I would love to hear about it. Please contact me via [Twitter][6], [Google+][7], or [email][8].

-----

*Quick follow up:* It should be noted that some bugs still exist and may rear their heads depending on how you use the library. The library is under constant development and I will do my best to get updates and fixes out ASAP. The library is certainly more stable than not and I could not in good conscience allow anyone to develop using v3.5.x any longer. I'm hoping that the increased exposure that should come with the final release will help attract more developer attention which should aid in resolving bugs more quickly.

**Please use the Google group for all library-related discussions @ [abs.io/forum][18]**

Also, pull requests save lives so send them!

&nbsp;

![ActionBarSherlock Logo][16] ![Happy Birthday to me!][2] ![Ice Cream Sandwich][17]




 [1]: /actionbarsherlock-a-love-story-part-1
 [2]: /static/post-image/forever-alone-birthday.jpg
 [3]: /actionbarsherlock-a-love-story-part-2
 [4]: http://actionbarsherlock.com/
 [5]: http://actionbarsherlock.com/download.html
 [6]: http://twitter.com/JakeWharton
 [7]: http://profiles.google.com/jakewharton
 [8]: mailto:jakewharton@gmail.com
 [9]: https://play.google.com/store/apps/details?id=com.actionbarsherlock.sample.demos
 [10]: https://play.google.com/store/apps/details?id=com.actionbarsherlock.sample.fragments
 [11]: https://play.google.com/store/apps/details?id=com.actionbarsherlock.sample.roboguice
 [12]: https://play.google.com/store/apps/details?id=com.battlelancer.seriesguide
 [13]: https://play.google.com/store/apps/details?id=com.battlelancer.seriesguide.beta
 [14]: https://play.google.com/store/apps/details?id=com.github.mobile.gauges
 [15]: https://play.google.com/store/apps/details?id=com.androiduipatterns.mentionobserver
 [16]: /static/post-image/actionbarsherlock-logo.png
 [17]: /static/post-image/android-ice-cream-sandwich.jpg
 [18]: http://abs.io/forum
