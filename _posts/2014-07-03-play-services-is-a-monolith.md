---
title: Play Services 5.0 Is A Monolith Abomination
layout: post

categories: post
tags:
- Android

lead: Monolithic libraries are rarely the answer and when abused cause much more harm than small, module artifacts. Most people think of Guava, but there's a new kid on the block who's twice as bad.
image: /static/post-image/play-services-deps.png
---

[Guava][2] is a monolithic library, but that's not necessarily a bad thing. Nobody thinks twice when bundling it for the JVM. In the world of Android the mention of Guava has a bit of a negative stigma due to the dex file format's method limit and a concern about bloating APK size. The latter is no longer a valid argument. The dex method limit is a hard 64k limit to which Guava contributes just over 14k methods. 20% of this hard limit vanishes when you include Guava.

Sounds scary, right? It isn't.

Google Play Services 5.0 which [just launched][1] contributes over <strong>twenty thousand</strong> methods to your app. 20k+. One third of the limit! Now <em>that</em> is scary.

The Play Services library includes proprietary functionality built on the normal Android APIs and a separate APK downloaded on all devices with the Play Store. Some of the services it provides are invaluable. Like Guava it is also a monolothic library but it <em>is</em> a bad thing in this case.

A lot of really cool functionality is being put in Play Services. You'll have a hard time making a compelling app that lives in the Google Play ecosystem without it. You should want to put it in your applications and not have to worry about the overhead it brings.

Most of the library's offerings are very disparate, having only the fact that they're by Google as a common thread. This screams for small, modular artifacts which can be composed!

<strong>Google, it's time to unbundle.</strong> [All the cool kids are doing it][4]. *(Spoiler alert: [it happened][7])*

At worst, we specify a few dependencies manually:

```groovy
dependencies {
  compile 'com.google.android.gms:play-services-ads:5.0.+'
  compile 'com.google.android.gms:play-services-analytics:5.0.+'
  compile 'com.google.android.gms:play-services-games:5.0.+'
}
```

Best case would be a plugin that provided a clear DSL to what you were getting and offered easier configuration of the various components.

```groovy
apply plugin: 'com.google.playservices'

playServices {
  version '5.0.+'
  components 'ads', 'analytics', 'games'
}
```

(You can even still provide the "fat" jar in both the dependency management world and the people who like manual dependency management.)

ProGuard is not the answer. Yes, for release builds it's nice to strip out any methods which are not being used. However, this is not justification for having large chunks of unused code as dependencies. Besides, if you read [my post on a simulator][3] you know that we deserve a faster development build pipeline which removes steps, not adds them.

It's not going to be a walk in the park but the packages inside Play Services are surprisingly well-configured to partitioning:

[![Play Services Dependency Diagram](/static/post-image/play-services-deps.png)](/static/post-image/play-services-deps.png)

*(Top-left: Games, top-center: Drive, middle-left: Plus, middle: common, middle-right: Maps, bottom: Ads)*

Here's Guava for comparison which has less clear partition lines:

[![Guava Dependency Diagram](/static/post-image/guava-deps.png)](/static/post-image/guava-deps.png)

----

Here's how the method counts were determined:

    $ curl 'http://search.maven.org/remotecontent?filepath=com/google/guava/guava/17.0/guava-17.0.jar' > guava.jar
    $ ~/android-sdk/build-tools/20.0.0/dx --dex --output guava.dex guava.jar
    $ dex-method-count guava.dex
    14824

    $ cp ~/android-sdk/extras/google/m2repository/com/google/android/gms/play-services/5.0.77/play-services-5.0.77.aar .
    $ unzip play-services-5.0.77.aar
    $ ~/android-sdk/build-tools/20.0.0/dx --dex --output play-services.dex classes.jar
    $ dex-method-count play-services.dex
    20298

And the full by-package breakdown of Play Services:

    $ dex-method-count-by-package play-services.dex
    20298 com
    20298 com.google
    207   com.google.ads
    169   com.google.ads.mediation
    73    com.google.ads.mediation.admob
    62    com.google.ads.mediation.customevent
    20188 com.google.android
    20188 com.google.android.gms
    2     com.google.android.gms.actions
    480   com.google.android.gms.ads
    135   com.google.android.gms.ads.doubleclick
    25    com.google.android.gms.ads.identifier
    88    com.google.android.gms.ads.mediation
    4     com.google.android.gms.ads.mediation.admob
    73    com.google.android.gms.ads.mediation.customevent
    26    com.google.android.gms.ads.purchase
    118   com.google.android.gms.ads.search
    866   com.google.android.gms.analytics
    52    com.google.android.gms.analytics.ecommerce
    10    com.google.android.gms.appindexing
    151   com.google.android.gms.appstate
    80    com.google.android.gms.auth
    644   com.google.android.gms.cast
    1026  com.google.android.gms.common
    12    com.google.android.gms.common.annotation
    382   com.google.android.gms.common.api
    235   com.google.android.gms.common.data
    202   com.google.android.gms.common.images
    126   com.google.android.gms.common.internal
    126   com.google.android.gms.common.internal.safeparcel
    1940  com.google.android.gms.drive
    87    com.google.android.gms.drive.events
    897   com.google.android.gms.drive.internal
    241   com.google.android.gms.drive.metadata
    202   com.google.android.gms.drive.metadata.internal
    205   com.google.android.gms.drive.query
    151   com.google.android.gms.drive.query.internal
    451   com.google.android.gms.drive.realtime
    451   com.google.android.gms.drive.realtime.internal
    123   com.google.android.gms.drive.realtime.internal.event
    38    com.google.android.gms.drive.widget
    332   com.google.android.gms.dynamic
    4534  com.google.android.gms.games
    73    com.google.android.gms.games.achievement
    113   com.google.android.gms.games.event
    2956  com.google.android.gms.games.internal
    858   com.google.android.gms.games.internal.api
    43    com.google.android.gms.games.internal.constants
    8     com.google.android.gms.games.internal.data
    31    com.google.android.gms.games.internal.events
    9     com.google.android.gms.games.internal.experience
    215   com.google.android.gms.games.internal.game
    56    com.google.android.gms.games.internal.multiplayer
    23    com.google.android.gms.games.internal.notification
    80    com.google.android.gms.games.internal.player
    86    com.google.android.gms.games.internal.request
    256   com.google.android.gms.games.leaderboard
    640   com.google.android.gms.games.multiplayer
    239   com.google.android.gms.games.multiplayer.realtime
    256   com.google.android.gms.games.multiplayer.turnbased
    213   com.google.android.gms.games.quest
    150   com.google.android.gms.games.request
    210   com.google.android.gms.games.snapshot
    47    com.google.android.gms.gcm
    111   com.google.android.gms.identity
    111   com.google.android.gms.identity.intents
    62    com.google.android.gms.identity.intents.model
    5760  com.google.android.gms.internal
    295   com.google.android.gms.location
    2342  com.google.android.gms.maps
    804   com.google.android.gms.maps.internal
    1068  com.google.android.gms.maps.model
    483   com.google.android.gms.maps.model.internal
    14    com.google.android.gms.panorama
    902   com.google.android.gms.plus
    352   com.google.android.gms.plus.internal
    316   com.google.android.gms.plus.model
    192   com.google.android.gms.plus.model.moments
    126   com.google.android.gms.plus.model.people
    33    com.google.android.gms.security
    1367  com.google.android.gms.tagmanager
    867   com.google.android.gms.wallet
    376   com.google.android.gms.wallet.fragment
    143   com.google.android.gms.wallet.wobs
    1011  com.google.android.gms.wearable
    714   com.google.android.gms.wearable.internal

You can grab these two scripts from here: [gist.github.com/JakeWharton/6002797](https://gist.github.com/JakeWharton/6002797)

The dependency graphs were generated using [degraph][5] and [yEd][6]. Download the `.graphml` for [Play Services](/static/files/play-services-5.graphml) and [Guava](/static/files/guava-17.graphml).


 [1]: http://android-developers.blogspot.com/2014/07/google-play-services-5.html
 [2]: https://code.google.com/p/guava-libraries/
 [3]: http://jakewharton.com/android-needs-a-simulator/
 [4]: http://thenextweb.com/socialmedia/2014/05/06/large-tech-companies-hopping-app-unbundling-trend/
 [5]: https://github.com/schauder/degraph
 [6]: http://www.yworks.com/en/products_yed_about.html
 [7]: http://android-developers.blogspot.com/2014/12/google-play-services-and-dex-method.html
