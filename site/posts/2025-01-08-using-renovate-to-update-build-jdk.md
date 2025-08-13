---
title: 'Using Renovate to update build JDK'
layout: post

categories: post
tags:
- Gradle
- Java
- Kotlin
- Android
---

You want to be using the latest JDK for development. 
[Don't use Gradle toolchains](/gradle-toolchains-are-rarely-a-good-idea/), they'll needlessly force you to use old JDKs.
You can still [target and test on old JVM versions](/build-on-latest-java-test-through-lowest-java/) but develop with the latest and greatest.
Java and the JDK are literally built for this.

Locally this hasn't been a problem.
Homebrew (or your favorite equivalent) will keep your default JDK at the latest.
Keeping my GitHub actions up-to-date, however, frequently slips my mind.
I find projects using 19 or 20 simply because I haven't touched the CI build in the two years since 19 or 20 was the latest.

We're already using Renovate to keep dependencies up to date.
With a little extra programming in JSON _(wince)_ we can have the JDK version updated to latest as well.

First, migrate the existing build JDK version in your GitHub Action to a `.github/.java-version` file[^1].

[^1]: I'm putting the `.java-version` file into the `.github/` folder because I don't want to force this version on people using `jenv` or the like. The whole point of this setup is you can build with _any_ version of Java newer than our very, _very_ old baseline of Java 8 (although things like Gradle have a higher minimum requirement).

```
21
```

Next, change the `setup-java` action to use this file rather than a hard-coded version.

```diff
 - uses: actions/setup-java@v4
   with:
     distribution: 'zulu'
-    java-version: 21
+    java-version-file: .github/.java-version
```

Finally, in your `renovate.json5`[^2], add a custom manager to update this file[^3].

[^2]: JSON5 is just JSON with fewer quotes, more trailing commas, and actual comments. You don't have to migrate to use this functionality (but you should!).

[^3]: You can adapt the file-matching regex and string-matching regex to target the GitHub Action `.yaml`s directly if you want. I choose to use the `.java-version` file so that I can install multiple older JDKs with a second `setup-java` (which won't be updated) for [testing with multiple JDKs](/build-on-latest-java-test-through-lowest-java/).

```json5
ignorePresets: [
  // Ensure we get the latest version and are not pinned to old versions.
  'workarounds:javaLTSVersions',
],
customManagers: [
  // Update .java-version file with the latest JDK version.
  {
    customType: 'regex',
    fileMatch: [
      '\\.java-version$',
    ],
    matchStrings: [
      '(?<currentValue>.*)\\n',
    ],
    datasourceTemplate: 'java-version',
    depNameTemplate: 'java',
    // Only write the major version.
    extractVersionTemplate: '^(?<version>\\d+)',
  },
],
```

Commit, push, and wait for Renovate to send you a PR[^4].
Now your CI build automatically tracks the latest JDK.

[^4]: Here's an example sequence of PRs which did this migration: [Switch to `.java-version`](https://github.com/cashapp/turbine/pull/374/files), [add custom Renovate config](https://github.com/cashapp/turbine/pull/376/files), and [Renovate's automatic JDK bump](https://github.com/cashapp/turbine/pull/377).