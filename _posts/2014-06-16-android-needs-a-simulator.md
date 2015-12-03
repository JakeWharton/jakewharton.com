---
title: Android Needs A Simulator, Not An Emulator
layout: post

categories: post
tags:
- Android

lead: The emulator quality is terrible, the AVD manager is weak, and third-party solutions like Genymotion are mediocre at best. Developers need an Android simulator.
image: /static/post-image/emulator-m3.png
---

Two years ago I wrote [a blog post][1] complaining that the Android build system was broken. At the time, Eclipse ADT and Ant were the blessed solutions and they just hadn't scaled with the platform. Third-party solutions existed for both tooling and IDE but they always felt a bit illegitimate and at risk for problems. My post joined the cries of others who knew that something *had* to be done.

[Xavier Ducrohet][2] swooped in and dropped a bomb on the [resulting Google+ thread][3]: "We are looking at revamping the whole thing".

In the two years since he and the tools team have transformed the landscape of how Android development is done. A first-party Gradle plugin now provides the powerful and dynamic platform on which any app of quality is built. Ownership of the Android plugin inside IntelliJ IDEA (with a sprinkle of branding) yields a development environment that moves mountains for you.

Neither the Gradle plugin nor the IntelliJ IDEA plugin (known bundled as Android Studio) are at a v1.0 yet. They're both still beta (albeit arguably in the sense that GMail was circa 2008).

Why was all of this important and why is it important for Google moving forward?

Developers are the top of the funnel for Android's continued success. Without quality development tools there are no quality apps, without quality apps there are no quality users, and without quality users the developers will flee. Half of the developer flow in this funnel comes from the tools and the other half from APIs. This post is about the former.

---

<img src="/static/post-image/emulator-m3.png" width="300" class="pull-right">

My first exposure to Android was the M3 pre-release SDK's emulator *(pictured)*. The emulator started up quickly and was responsive. Each successive release up to and beyond version 1.0 added much needed functionality to both the OS and the emulator. And in each successive release the emulator slowed.

Android's pubescent period (otherwise known as Honeycomb) and its eventual emergence into adulthood had a devestating effect on emulator performance. Despite our development environments becoming more powerful, two factors outpaced Moore's law:

 1. Support of tablets and advances in screen technology meant that devices were gaining a lot of pixels.
 2. A more advanced graphics pipeline pushed rendering from software down into the hardware. This brought great performance at the expense of internal complexity.

Working with Intel, Google eventually [released an x86 version][4] of the emulator which eliminated ARM emulation and leveraged virtualization technologies built-in to CPUs. This was better. In fact, it was so much better that a lot of people were satisfied &mdash; myself included.

Recently, a company called [Genymotion][5] was formed around the work of a project that compiled Android to run in a VirtualBox VM. They not only delivered an experience that was faster and simpler than the x86 emulator, but they provided much-needed tools for modern app development. Easier sensor controls, touchscreen input using a remote device, and screen capture support for both images and video are just some of these features which really make the product shine.

Hopefully none of what I've covered is news to you. But now I am ready to start talking about this post's true topic:

<img src="/static/post-image/avd-manager.png" width="300" class="pull-right">

**All existing emulator solutions are terrible.**

The management interface for creating, configuring, and starting emulators is a minimumly-viable Swing app. While the low quality list view is actually fine, the configuration pane is a mess *(pictured)*.

This screen lacks any design to facilitate the correct behavior. The instruction set is usually defaulted to ARM which has to be fully emulated (very slow). Use of the host GPU is defaulted to off which means that the display pipeline will not be hardware accelerated (again, very slow). Unless you've been Googling passive-aggressive phrases about the emulator speed you might never even cross [HAXM][6].

The pain does not cease once the emulator is running (whether using optimal settings or not). Each instance requires significant system resources and the performce of the contained OS will vary wildly. Instances will occasionally hang, crash, or disappear from `adb`'s visibility requiring manual restarts. Controls and interfaces to a few of the sensor are present, but they are far from comprehensive.

Genymotion is a step up from the first-party offering. It has fewer options for configuration because it is already set up for optimal performance. As previously mentioned, its sensor and developer controls are much more rich and useful.

The initial downside of Genymotion is the required user account and strange pricing of commercial licenses (and lack of a site license). They also are not without problems which plague the actual use of the emulator. The VirtualBox images occasionally get corrupted or stuck which require a trip in the depths of your filesystem for manual purging. The free license cripples functionality that would otherwise exist if they hadn't explicitly disabled it (screenshot, recording). Their pricing model for commercial use also does not reflect the amount of utility you actually receive.

We put up with these solutions because they are an improvement compared to what came prior. However, they are nowhere near what we truly need or deserve.

---

**Android needs a simulator for day-to-day development and testing.**

> sim·u·la·tor  */ˈsimyəˌlātər/*
>
> A machine with a similar set of controls designed to provide a realistic imitation of the operation of a vehicle, aircraft, or other complex system, used for training purposes.

A simulator is a shim that sits between the Android operating system runtime and the computer's running operating system. It bridges the two into a single unit which behaves closely to how a real device or full emulator would at a fraction of the overhead.

The most well known simulator to any Android developer is probably (and ironically) the one that iOS develoers use from Apple. The iPhone and iPad simulators allow quick, easy, and lightweight execution of in-development apps. If you haven't seen this simulator in action, I would encourage you to take a two-minute tour of one before continuing this post.

What does a simulator buy us that a traditional emulator does not?

 * Creating, configuration, and running simualtors becomes about the runtime, not the right configuration of options for optimal performance.
 * Instrumentation tests gain stability and speed which thus massively increases their utility. A simulator that runs headless as part of the normal build means your build can run these tests no differently than it compiles the Java sources.
 * The need for a separate, JVM-based unit test solution diminishes drastically. Even more exciting is that the need for a third-party testing solution like Robolectric dimishes with it. When a headless simulator is part of your build (and with an [upcoming test runner diversification][10]), unit tests on the JVM become a first-party delight.
 * The lack of an emulated architecture layer, the overhead of a whole OS running, and the need for build steps like packaging (more on that to follow) means you are able to develop and deploy at a speed which just isn't possible in the current setup.

There always will be a need for a proper emulator for acceptance testing your application in an environment that behaves exactly like a device. For day-to-day development this is simply not needed. Developer productivity will rise dramatically and the simplicity through which testing can now be done will encourage their use and with any luck improve overall app quality.

---

Android actually already has two simulators which are each powerful in different ways, but nowhere near powerful enough. Before we talk about them, let's cover why a simulator is a perfect fit for Android development.

Apps are text files of Java The Language™, compiled with `javac` to JVM bytecode, transformed with `dex` to Dalvik bytecode, zipped up into an `.apk`, and signed using `jarsigner`. Other tools like `zipalign` and ProGuard are optionally a part of this toolchain but since they aren't usually used in development we can safely ignore them. Prior to the invocation of `javac`, all of the resources of an app must be parsed with `aapt` for code generation and special encoding of some files. This is a lot of steps!

Using a simulator would reduce this to a single step: compilation with `javac`. We are already relying on the JVM for running our IDE, our build system, and our compilation. Why aren't we leveraging it for running a simulated OS?

Ok so I glossed over two other toolchain components we'd need to run our class files in a JVM-hosted simulator:

 1. A modified `aapt` whose only responsibility was generation of `R` files would still be needed. Thankfully the slower operations this resource step performs (image optimization and text file encoding) wouldn't be needed. XML files can be read on-the-fly as text by the simulator. Images don't need optimized since they are just being displayed from the local filesystem.
 2. A signing key is required for the OS to verify installation and grant special permissions. Rather than having to actually sign anything, a simple certificate can be created from the keystore and included as a string.

Imagine how quick the time between modifying your source code and running the application becomes when the only steps needed are a resource scan, `javac`, and copying a string. Oh, but do you have a ton of dependencies? Not a problem since all that's needed is appending the file path of the `.jar` file onto the JVM classpath.

The prospect of this "exploded" `.apk` application should get you seriously excited.

Even more exciting is that there are already two simulators which work with these exploded apps. The first and most well known is [Robolectric][7], a tool for running unit tests on the JVM. The second is named "[layoutlib][8]" which is far less known but is used daily by every Android developer.

 1. Robolectric runs a compiled version of the OS in a separate classloader using techniques like bytecode rewriting and proxies. This puts most of the real OS infrasture at your disposal for unit testing code paths of your app that have to touch Android code. People often abuse Robolectric for testing the wrong things but it usually works because the real OS code is used by default.

    It takes about two seconds for Robolectric to initialize. Most of this time is creating the custom classloader and initializing all the proxy classes. Once running application code is loaded into the classloader and run like normal Java code. The resources are lazily resolved directly from the source files.
 2. "layoutlib" is a module whose purpose is to run view code on the JVM including parsing layout XML and loading resources. If you've ever used the layout designer or layout preview in either Eclipse or IntelliJ IDEA/Android Studio then you have used this library.

     Running your view code (including custom views) is done like any other Java code. The classes inside the library fake out the `Context` and the resource loading it brings. The rendering pipeline is also simply mapped into normal Java rendering primitives so you can see real-time updates of your layouts.

Both of these libaries use very clever techniques to simulate parts of Android to great success. Neither one is suited to running an application during development which is what we are after.

---

There are hurdles to be tackled in building a simulator that can host development applications. If you'll remember from above, we already have a pared down version of `aapt`, a simple representation of the signing key, and are leveraging `javac` and the JVM classpath for loading code and libraries. Let's enumerate what else is required &mdash; none of which are insurmountable.

 *  Native code has to be compiled for x86 in order to be run. Aside from the regular pain of JNI there should not be too much trouble here.
 *  The graphics pipeline of the OS needs hooked in to the host. I won't pretend to know a lot about what would be required in this area. Native code covers software rendering and hooking up OpenGL should facilitate hardware rendering.
 *  A variety of interfaces with hardware need replicated or faked. Some have obvious native equivalents on the host like bluetooth and networking. Some can simply be fixed to default values like the accelerometer and compass. Others can not be marked as present or just emulated.

Thankfully these are all solvable problems. Each one just needs the right person with the time and effort to tackle it. However, therein lies another problem.

The single greatest hurdle to the creation of the simulator we deserve is the time, effort, and desire required to build and maintain it. Sorry, tools team! I'm told [they're hiring][9].

*Follow the discussion on [Google+][11] and [Reddit][12].*








 [1]: http://jakewharton.com/the-android-build-system-is-broken/
 [2]: https://google.com/+XavierDucrohet
 [3]: https://plus.google.com/+JakeWharton/posts/KuWYBtLKtSE
 [4]: http://android-developers.blogspot.com/2012/04/faster-emulator-with-better-hardware.html
 [5]: http://www.genymotion.com/
 [6]: https://software.intel.com/en-us/android/articles/intel-hardware-accelerated-execution-manager
 [7]: http://robolectric.org/
 [8]: https://android.googlesource.com/platform/frameworks/base/+/master/tools/layoutlib
 [9]: https://twitter.com/droidxav/status/476181900357169152
 [10]: https://android.googlesource.com/platform/frameworks/testing/+/master/androidtestlib/
 [11]: https://plus.google.com/108284392618554783657/posts/FYxnhQuEyoq
 [12]: http://www.reddit.com/r/androiddev/comments/28al7i/android_needs_a_simulator_not_an_emulator/
