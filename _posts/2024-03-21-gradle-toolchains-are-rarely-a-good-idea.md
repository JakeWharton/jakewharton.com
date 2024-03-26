---
title: 'Gradle toolchains are rarely a good idea'
layout: post

categories: post
tags:
- Gradle
- Java
- Kotlin
- Android
---

The [last post](/kotlins-jdk-release-compatibility-flag/) featured some Kotlin code inadvertently targeting a new Java API when the build JDK was bumped to 21. This can be solved with the `-Xjdk-release` Kotlin compiler flag, or by using Gradle toolchains to build with an old JDK.

If you read the [Gradle docs](https://docs.gradle.org/current/userguide/building_java_projects.html#sec:java_cross_compilation)…

> Using Java toolchains is a preferred way to target a language version

…or the [Android docs](https://developer.android.com/build/jdks#toolchain)…

> We recommend that you always specify the Java toolchain

…you wouldn't be blamed for thinking Java toolchains are the way to go!

However, Java toolchains are rarely a good idea. Let's look at why.

### Bad docs

Last week I released a new version of [Retrofit](https://github.com/square/retrofit) which uses a Java toolchain to target Java 8. Its use of toolchains was contributed a while ago, and I simply forgot to remove it. As a consequence, [its Javadoc](https://square.github.io/retrofit/2.x/retrofit/) was built using JDK 8 and is thus not searchable. Searchable Javadoc came in [JEP 225](https://openjdk.org/jeps/225) with JDK 9.

The next release of Retrofit will be made without a toolchain and with the latest JDK. Its docs will have all the Javadoc advancements from the last 10 years including search and better modern HTML/CSS.

### Resource ignorance

Old JVMs were somewhat notorious for being ignorant to resource limitations imposed by the system. The rise of containers, especially on CI systems, means your process resource limits are different from those of the host OS. JDK 10 kicked things into high gear with [cgroups support](https://bugs.openjdk.org/browse/JDK-8146115) and JDK 15 [extended that to cgroups2](https://bugs.openjdk.org/browse/JDK-8230305).

Both of those changes were backported to the 8 and 11 branches, but since Gradle toolchains will use an already-installed JDK if available you have to have kept your JDK 8 and/or JDK 11 up-to-date. Have you?

Not to stray too far off-topic, but if you installed it with SDKMAN! or similar JDK management tools there's a good chance it's wildly out of date. I keep all my JDKs up-to-date by installing them through [a Homebrew tap](https://github.com/mdogan/homebrew-zulu) which itself updates automatically using the Azul Zulu API. As long as I do a `brew upgrade` every so often, each major JDK release that I have installed will be updated.

Without a Java toolchain, a modern JDK (even an outdated patch release of one) will honor resource limits and perform much better in containerized environments.

### Compiler bugs

All software has bugs, and sometimes the JVM, the Java compiler, or both have bugs. When you are using a 10-year-old version of the JVM and Java compiler, you run a much greater risk of compiler bugs, especially around features introduced near to that release.

There were many compilation problems around lambdas which were introduced in Java 8. If you are using the Java compiler from JDK 8 to target Java 8 JVMs you can still run into those bugs. Even if you are keeping your JDK 8 up-to-date many fixes are not backported. You [can find ones](https://bugs.openjdk.org/browse/JDK-8182401) on the issue tracker without much effort.

Now is the Java compiler in JDK 22 completely bug-free? No. But is using the Java compiler from JDK 22 on sources targeting Java 8 using only Java 8 language features much safer than using one from JDK 8? Absolutely.

### Worse performance

Oracle and other large JVM shops devote lots of person-hours to making the JVM faster. We have newer garbage collectors that use less memory and consume less CPU. Work that happened on startup gets deferred to first-use to try and spread the cost out over the lifetime of the process. Algorithms and in-memory representations are specialized for common cases.

A language compiler is basically a worst-case scenario for the JVM. Endless string manipulation, object creation, and so so many maps. These areas receive many improvements over the years. My favorite of which is that strings which are ASCII-based suddenly occupy half as much memory in Java 9 than in Java 8. You know what's often entirely ASCII? Java and Kotlin source code!

### Not needed for cross-compilation

Using the Java compiler from JDK 8 I can set `-source` and `-target` to "1.7" to compile a class that works on a Java 7 JVM. This does not prevent me from using Java 8 APIs, however. You have to add `-bootclasspath` with a pointer to a JDK 7 runtime (`rt.jar`) so that the compiler knows what APIs are available in Java 7. You could alternatively use a tool like [Animal Sniffer](https://www.mojohaus.org/animal-sniffer/) to validate that no APIs newer than Java 7 were used. In this world, just compiling with JDK 7 to target Java 7 might actually just be easier.

In JDK 9, however, this all changed. The compiler now contains a record of all public APIs from every Java version going back to Java 8. It also allows specifying a single compiler flag, `--release`, which sets the source code language version, the target bytecode version, and the available runtime APIs to the specified release. There is simply no value in compiling with an older JDK to target an older JVM anymore.

### Wasted disk space

All those JDKs needlessly take up space in your home directory. Each JDK is a few hundred MiB. By default, Gradle will try to match an existing JDK when a toolchain is requested. Project owners can specify additional attributes such as the JDK vendor which might cause existing JDKs to not match. This means even though one project forced you to install Eclipse Temurin JDK 8, another might force Azul Zulu JDK 8. So not only do you now have a bunch of old JDKs, you have two or three copies of each. My JDK cache in `~/.gradle` is nearly 2 GiB.

### Not the Gradle JVM

Toolchains are only used for tasks that create a new JVM. That means compilation (of Java or Kotlin) and running unit tests. They do not control the JVM that is used for running the actual Gradle build or any of the plugins therein. If you have minimum requirements there, or in other JVM-based tools which are invoked by the Gradle build, the toolchain does not help you.

If your build already has a minimum JDK requirement then why force installation of old JDKs given the newer one is already available on disk, can cross-compile perfectly, has fewer compiler bugs, builds faster, and respects system CPU and memory limits more effectively?

### Not all bad

I want to stress that toolchains are unequivocally not a good idea _for compilation_. They still have utility elsewhere, however.

Retrofit has runtime behavior that changes based on the JVM version on which it's running. (This is because until Java 16 it took various different hacks to support invoking default methods through a [`Proxy`](https://docs.oracle.com/en%2Fjava%2Fjavase%2F22%2Fdocs%2Fapi%2F%2F/java.base/java/lang/reflect/Proxy.html).) That code needs to be tested on different JVM versions. As a result, we [compile with the latest Java, but test through the lowest-supported Java](/build-on-latest-java-test-through-lowest-java/) using toolchains on the `Test` task. No need to worry about the user having weird old JDKs for Java 14 because it's now installed on-demand when the full test suite is run.

Some tools that dip into JDK internals regularly break on newer versions of the compiler because they rely on unstable APIs. I'm thinking about things like [Google Java Format](https://github.com/google/google-java-format) or [Error-Prone](https://errorprone.info/). No need to hold the rest of your project from enjoying the latest JDK, if those tools are run via a `JavaExec` task you can use a toolchain to keep them on an older JDK until a newer version is available.

### What do I do?

Use the `--release` flag if you're compiling Java! Gradle [exposes a property](https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:release) for it now.

Use the `-Xjdk-release` flag if you're compiling Kotlin. Future versions of the Kotlin Gradle plugin will expose a nice DSL property for it.

If you're [targeting Android](https://developer.android.com/build/jdks#target-compat) (with Java, Kotlin, or both) you need only specify the `sourceCompatibility` (for Java) and `jvmTarget` (for Kotlin). You don't need the `targetCompatibility` as it will default to match the `sourceCompatibility`.

No matter what the Gradle or Android docs tell you, don't use a toolchain! Save toolchains for JVM unit tests or incompatible tools.
