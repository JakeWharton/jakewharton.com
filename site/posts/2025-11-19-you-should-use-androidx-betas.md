---
title: 'You should use AndroidX betas'

tags:
- Android
---

Did you know the versioning of AndroidX libraries and their stability guarantees are different from most libraries?
Their betas and RCs are actually production-ready, and you should be using them!

In a “normal” library, such as the ones I release, features are added and known bugs are fixed to produce a stable release which might be released as version 1.2.0.
If any bugs are found in that release, they get fixed and put into a version 1.2.1.
If new APIs are added, the next version becomes 1.3.0. This is basic [semantic versioning](https://semver.org/).

AndroidX does not do versioning this way.
When a library has its features added and its known bugs fixed they promote that library to beta01.
This artifact is now API stable!
Don’t believe me?
This is [documented in their guidelines](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/docs/versioning.md#prerelease).
They also have tooling which validates that you cannot break APIs or even introduce new APIs once an artifact has reached beta.

Thus, when AndroidX releases a 1.2.0-beta01 of some library, it is equivalent to a normal library releasing a 1.2.0.
This is still semantic versioning, but it’s a more strict subset that imposes restrictions on prerelease versions.

Why do they do this?
The motivations are simple: they want the stable versions to be _extremely_ stable.
That is to say, to have most of the bugs that would otherwise necessitate subsequent patch releases to be caught in the ramp-up to 1.2.0.

Here’s all those words in chart form:

| Normal library | AndroidX library              |
|----------------|-------------------------------|
| 1.2.0-RC       | 1.2.0-alpha01                 |
| 1.2.0          | 1.2.0-beta01                  |
| 1.2.1          | 1.2.0-beta02 (etc.)           |
| 1.2.2          | 1.2.0-rc01 (etc.)             |
|                | 1.2.0 (same bits as final RC) |

Wondering if anyone else uses these betas?
All of Google’s first-party apps ship against the code in AndroidX HEAD.
Not only are they relying on these beta and RC versions, they build, test, and ship with the alpha versions and random commits in-between.
By the time a library even reaches `-beta01` it has already been widely tested and deployed.
AndroidX is to Google’s apps as what all of your `util-` and `common-` modules are to your `app`: shared code which is part of their codebase.

In the past, at Cash App, we've had to occasionally bump to a beta to work around a bug or get access to a new feature.

* There was a bug in the graphics shape library stable version that was breaking the rounded corners of our bottom sheet decoration.
* The collection library’s primitive specializations of `ScatterMap` had a bug which caused deleted values to be returned during insertion.
* A handful of Compose issues, whether in the runtime, foundation, or UI around recomposition problems, layout problems, retained state problems, etc.

In all those cases we reported bugs (or found existing bugs) and bumped to the next version’s betas or RCs in the meantime.

Sticking to stable versions is the easy play, but there’s a cost to that too.
One of the tradeoffs made in AndroidX’s choice to version this way is that stable versions are much farther apart.
Let’s say you’re on Compose UI 1.8, and you find a bug when bumping to Compose UI 1.9.
Not only are you stuck on 1.8 (assuming it has no workaround), but you now have to wait _months_ for Compose UI 1.10.
And then you hope the cycle doesn’t repeat.
If you found that same bug in the Compose UI 1.9 betas, however, then it’s fixed in a few weeks, and you can upgrade months sooner.

Choosing to do this doesn’t mean unleashing prerelease chaos across the hundreds of AndroidX libraries all at once.
You can gradually try this out–that's what we're doing.
Libraries which are mature (collection, core, activity, etc.) mean there’s simply not a lot of changes happening, so the risk is low.
Libraries which are wildly load-bearing (Compose runtime, foundation, and UI) are also good candidates since you really need to find any bugs in them as soon as possible.

If you've put the necessary testing infrastructure in place, you're already set up to do this with confidence.
Comprehensive unit tests ensure correct behavior.
Screenshot tests ensure correct pixels.
And instrumented tests, end-to-end tests, and/or manual QA testing is the reliability backstop ensuring nothing falls through the cracks.

No matter what, as AndroidX library users, we have a shared responsibility to report bugs upstream so that they get fixed.
You cannot assume that someone else will find them.
At Cash App, we have a very large codebase, and we do lots of interesting things with these libraries.
In order to really maximize the benefits of the betas, we plan to continue to find and report any bugs found.
This helps us stay on the path of upgradability, but also helps everyone else using these libraries.
And hey, I think you should consider doing the same!
