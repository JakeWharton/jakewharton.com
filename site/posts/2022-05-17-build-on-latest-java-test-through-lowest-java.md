---
title: 'Build on latest Java, test through lowest Java'
layout: post

categories: post
---

In the past, when a new version of Java was released, I would add that version to our open source project's CI builds.

```diff
 strategy:
   matrix:
     java-version:
       - 8
       - 9
         ⋮
       - 17
+      - 18
```

This ensures that each project can be built and its tests pass on every major version.

But this makes no sense! No user is building these projects on different versions. No user is building these projects at all. Consumers are using the pre-built `.jar` which we ship to Maven Central built on a single version.

Testing on every version, however, is something extremely valuable. Thankfully, [Gradle toolchains](https://docs.gradle.org/current/userguide/toolchains.html) let us retain this while still only building&nbsp;once.

First, CI only has to build on a single version. We choose the latest because Java has excellent cross-compilation capabilities, and we want to be using the latest tools.

```diff
 - uses: actions/setup-java@v2
   with:
     distribution: 'zulu'
-    java-version: ${​{ matrix.java-version }}
+    java-version: 18
```

Second, unchanged from before, we still target whichever Java version is the lowest supported through either the `--release` flag or `sourceCompatibility`/`targetCompatibility` [per the Gradle docs](https://docs.gradle.org/7.4/userguide/building_java_projects.html#sec:java_cross_compilation).

And finally, we set up tests to run on every supported version.

```groovy
// Normal test task runs on compile JDK.
(8..17).each { majorVersion ->
  def jdkTest = tasks.register("testJdk$majorVersion", Test) {
    javaLauncher = javaToolchains.launcherFor {
      languageVersion = JavaLanguageVersion.of(majorVersion)
    }

    description = "Runs the test suite on JDK $majorVersion"
    group = LifecycleBasePlugin.VERIFICATION_GROUP

    // Copy inputs from normal Test task.
    def testTask = tasks.getByName("test")
    classpath = testTask.classpath
    testClassesDirs = testTask.testClassesDirs
  }
  tasks.named("check").configure { dependsOn(jdkTest) }
}
```

This setup reduces CI burden since we only compile the main and test sources once but execute the tests on every supported version from latest to lowest.

```
Verification tasks
------------------
check - Runs all checks.
test - Runs the test suite.
testJdk10 - Runs the test suite on JDK 10
testJdk11 - Runs the test suite on JDK 11
testJdk12 - Runs the test suite on JDK 12
testJdk13 - Runs the test suite on JDK 13
testJdk14 - Runs the test suite on JDK 14
testJdk15 - Runs the test suite on JDK 15
testJdk16 - Runs the test suite on JDK 16
testJdk17 - Runs the test suite on JDK 17
testJdk8 - Runs the test suite on JDK 8
testJdk9 - Runs the test suite on JDK 9
```

For projects using [multi-release jars](https://openjdk.java.net/jeps/238), this compilation and testing setup is essential since the source sets require compiling with newer versions but testing through a lower version bound.

So if adding Java versions to a CI matrix is something you've been doing, consider switching to compile with a single Java version and instead varying your test execution instead. And if you only build and test on a single version today, adding this can ensure correctness on all versions that you support.

Not every project needs to test on multiple versions. If your code is mostly algorithmic you won't gain much from doing this. But if you vary behavior based on Java version, conditionally leverage APIs on newer versions, or interact with non-public APIs then this is a best practice.

---

P.S. Are you an Android developer? You probably keep your `compileSdk` high, your `minSdk` low(-ish), and execute instrumentation tests on a few versions between those two. Great news, you're already following this advice as it's always been the norm!
