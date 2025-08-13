---
title: Something Beta This Way Comes!
layout: post

categories: post
tags:
- Android
- ActionBarSherlock

lead: The ActionBarSherlock 4.0 betas are here. Go break things!
source: http://beta.abs.io/
---

Some implementation details…

There are 6 base activities, 4 of which are the core library and one in each of two plugins.

 * Core: `SherlockActivity`, `SherlockPreferenceActivity`, `SherlockListActivity`, and `SherlockExpandableListActivity` - Former two should be obvious, latter two shouldn’t really be used anymore. Try fragments.
 * Compat-Lib: `FragmentActivity` - Modified version of the official library to support having an action bar by default.
 * Maps: `SherlockMapActivity` - Since referencing the base class MapActivity requires compiling with the Google APIs this is in its own plugin. Remember: This does not work like `FragmentMapActivity` from v3.x.

Everything has been moved into the `com.actionbarsherlock.*` package tree. Stay away from `com.actionbarsherlock.internal.*`. Check your imports.

Again, as with v3.x, the default options menu methods have been marked final in the base activities so that you cannot use them erroneously. A majority of the supposed bugs that I receive have to do with incorrect imports. Check your imports and check the samples before filing a bug or emailing the mailing list.

Don’t file a bug or suggestion on anything related to the compat-lib plugin that does not directly relate to this library. As nice as it was to upstream some bugfixes I am not doing that anymore. File them on [b.android.com](http://b.android.com).

`SherlockPreferenceActivity` does not have fragment or loader support like v3.x did. No I will not enable it. Yes I’ll look at porting `PreferenceFragment` in the future. Don’t ask for an ETA.

There are bugs and missing features. Check [abs.io/4](http://abs.io/4) before reporting anything! Check the samples. If a sample is missing, take a few minutes to write it.

Pay attention for new betas. Check the website often. You can even follow the site’s repository on GitHub for better notification: [github.com/JakeWharton/beta.abs.io](https://github.com/JakeWharton/beta.abs.io).

Want fixes sooner? Check the [`4.0-wip`](http://abs.io/b/4.0-wip) branch. You’ll have to build the plugins yourself though if changes were made. Please don’t ask me how. Maven, SDK deployer, and mvn clean package.

Try everything. Write a new app, port an old app, write more samples. Do something. Don’t complain if you jump on the final release and find bugs without having trying the betas.

Use `Theme.Sherlock`. Use `Theme.Sherlock`!

There is no light theme… yet. Use black for testing. Don’t complain and don’t bother implementing it. A light and a light/dark action bar theme will be present in the release candidate.

Things are broken. Most is working. Try before you buy. All sales are final.

I’ll leave you with a semi-related, partially-humorous quote from Equilibrium (which is actually from W. Yeats)

> But I, being poor, have only my dreams. I have spread my dreams under your feet. Tread softly because you tread on my dreams.

**How to report bugs:**

Fix it yourself and send a pull request…

Ok, you don’t *have* to do that but I’ll seriously love you for it.

Create a new issue on GitHub, include as much description, code, and images as humanly possible to make your problem apparent to someone who has never done Android. It’s not that don’t understand your problem, it’s that I don’t want to have to spent extra time deciphering it or have any doubt about what you think the problem is.

I’ll do my best to thank you no matter how severe of a bug you find :)
