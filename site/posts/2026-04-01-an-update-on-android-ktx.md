---
title: "An update on Android KTX"

tags:
- Android
---

Eight years ago [we launched a library of Kotlin extensions](https://android-developers.googleblog.com/2018/02/introducing-android-ktx-even-sweeter.html) for the Android platform, Android&nbsp;KTX.
In the time since, the library became Core&nbsp;KTX, and numerous other KTX libraries were written for other AndroidX libraries.
And while KTX's approach to adding Kotlin niceties was built on a strong technology foundation, we’ve made the difficult decision to begin winding down our KTX extension libraries[^1].

[^1]: A parody of [Stadia's death](https://blog.google/products-and-platforms/products/stadia/message-on-stadia-streaming-strategy/), and many, _many_ others.

That's right folks–toss another entry on the Killed By Google board!

Despite the date this is no joke.

However, mourn not, friends.
The KTX libraries were killed because the adoption of Kotlin has been such a resounding success. All extensions have now been merged directly into their respective main library.
Woo!

Below is a table of every library which had a `-ktx` module and the first version where it became empty and thus obsolete.

| KTX library                             | Obsolete in version    |
|-----------------------------------------|------------------------|
| activity-ktx                            | 1.9.0                  |
| appsearch-ktx                           | None, empty[^5]        |
| collection-ktx                          | 1.3.0                  |
| concurrent-futures-ktx                  | 1.4.0-alpha01[^2][^3]  |
| core-ktx                                | 1.19.0-alpha01[^2][^3] |
| dynamicanimation-ktx                    | 1.2.0-alpha01[^2][^3]  |
| fragment-ktx                            | 1.9.0-alpha01[^2][^3]  |
| lifecycle-livedata-ktx                  | 2.7.0                  |
| lifecycle-livedata-core-ktx             | 2.8.0                  |
| lifecycle-reactivestreams-ktx           | 2.6.0                  |
| lifecycle-runtime-ktx                   | 2.8.0                  |
| lifecycle-viewmodel-ktx                 | 2.8.0                  |
| loader-ktx                              | 1.2.0-alpha01[^2][^3]  |
| navigation-common-ktx                   | 2.4.0                  |
| navigation-fragment-ktx                 | 2.4.0                  |
| navigation-runtime-ktx                  | 2.4.0                  |
| navigation-ui-ktx                       | 2.4.0                  |
| paging-common-ktx                       | 3.0.0                  |
| paging-runtime-ktx                      | 3.0.0                  |
| paging-rxjava2-ktx                      | 3.0.0                  |
| palette-ktx                             | 1.1.0-alpha01[^2][^3]  |
| preference-ktx                          | 1.3.0-alpha01[^2][^3]  |
| savedstate-ktx                          | 1.3.0                  |
| security-crypto-ktx                     | None, deprecated[^4]   |
| sqlite-ktx                              | 2.7.0-alpha03[^2][^3]  |
| tracing-ktx                             | 1.3.0                  |
| transition-ktx                          | 1.8.0-alpha01[^2][^3]  |
| watchface-complications-data-source-ktx | 1.4.0-alpha01[^2][^3]  |
| work-runtime-ktx                        | 2.9.0                  |

[^2]: As of April 1st, 2026, this version has not yet been released. I will update the table once that occurs in the coming weeks.

[^3]: Libraries with only an alpha version listed do not have a stable release yet. I will update the table once the library goes stable in the coming months.

[^4]: This library has all public API (including its KTX) deprecated. As a result, it will not be migrated. You should eliminate your use of the entire library regardless of KTX usage.

[^5]: This library has a KTX module, but it's always been empty.

There is a [feature request](https://issuetracker.google.com/issues/498266563) on Lint to provide a warning when you are declaring a KTX library equal to or newer than when it became obsolete.
Hopefully this will be implemented and can aid in migrating your codebase over time.

I had the privilege of [starting the KTX libraries](https://github.com/android/android-ktx/commit/f76bda89bcf2f7779b92152fa30351ddb3d07b14).
And I also had the privilege of [eliminating the last of them](https://android-review.googlesource.com/q/hashtag:"ktxterminate")!

Huge thanks to Chris&nbsp;Banes and Romain&nbsp;Guy who built that first Android KTX library alongside me.
Thanks to Aurimas&nbsp;Liutikas and Alan&nbsp;Viverette for putting the infrastructure in AndroidX so it could move into AOSP, grow into multiple libraries, and ultimately become obsolete as the main libraries became Kotlin-first.
Thanks to Marcello&nbsp;Galhardo for getting the ball rolling on the final elimination.
And finally, thanks to the ~157 contributors[^6] both inside Google and externally who helped build them out over the years.

[^6]: Computed via `git rev-list --all --pretty="%an" -- "*/*ktx*" | grep -v "commit " | grep -iv "treehugger" | sort | uniq | wc -l`.
