---
title: The Android Build System Is Broken
layout: post

categories: post
tags:
- Android

lead: As the complexity of Android projects increase, tolerance for the build
      system goes to zero.
---

The blessed souls in the Android world with regard to compilation are Eclipse
and ant. Both serve admirably if you have a small-to-medium-sized app. You might
even pull in a library project and a jar or two. This works great. As the
complexity grows beyond this, however, both of these players break down and you
are left to fend for yourself.

Advanced configurations which are designed to make it more efficient for you,
the developer, end up causing unnecessary strain because the build system
cannot handle it. When you have multiple modules for production and
development versions of your app so that both can be installed at once and
library projects that depend on library projects that depend on library
projects&mdash;all of which have different overlapping jar
dependencies&mdash;you are on your own.

Why is this? Well it is mostly because I have been lying to you. **Android does
not have a build system.**

What Android has is a scripting language that has been shoved unceremoniously
into XML, a default configuration that attempts to cover all of your use cases,
and an IDE whose configuration attempts to mirror the scripting language
configuration but has only marginal integration.

This is awful.

The Android community should settle for nothing short of the following:

 1. *Dependency management* - You should never have to copy a jar or library
    project into your tree. Version number differences should automatically
    be resolved. Transitive dependencies should be recursively pulled in.
 2. *Build order* - Multi-module builds are a directed, acyclic graph and the
    order of their compilation can be determined by the build system. If you
    add a dependency between two modules the order should automatically change
    to accommodate.
 3. *Non-Android projects* - Modules should not have to be Android library
    projects to be part of the build path. Pure Java projects (and anything
    that compiles to class files) must be supported.
 4. *Seamless IDE integration* - Changes to configurations should be reflected
    in both command-line builds and IDE builds without any additional effort.

Could all of this be accomplished with ant and Eclipse? Maybe. Should it be
attempted? Absolutely not.

I use maven and IntelliJ IDEA for all of my projects and while it solves all of
the requirements listed above, it does not feel perfect. At Square we are
currently using ant (with a lot of customization) and IntelliJ IDEA. I think
almost everyone on the team would agree that it feels far from perfect but it
works well enough. Results are hard to argue with but a resounding endorsement
this is not.

A build system should empower, not constrain. It should enable, not restrict. It
should be dynamic, not rigid.

The bottom line is that whether you use maven, sbt, or gradle we all lose
because Google is advocating and supporting ant and the Eclipse plugin.

We finally have an operating system that has been refined at an amazing level of
detail. We have tooling around developing and debugging applications to an
unparalleled depth. We deserve a build system with the same attention to detail.

*Follow discussion on [Google+][1] or [Reddit][2]*

**Update:** Be sure you check out Xavier Durochet's reply on the Google+ thread
(approximately 17 comments down).


 [1]: https://plus.google.com/108284392618554783657/posts/KuWYBtLKtSE
 [2]: http://www.reddit.com/r/androiddev/comments/wztec/the_android_build_system_is_broken/
