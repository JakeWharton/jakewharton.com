---
title: 'Nonsensical Maven is still a Gradle problem'
layout: post

categories: post
tags:
- Gradle
- Java
- Kotlin
- Android
---

There was a time when I used Maven heavily, but today all the libraries I work on build with Gradle. Even though I'm publishing with Gradle, consumers can use Gradle, Maven, Bazel, jars in `libs/` (but please don't), or anything else. That's a huge JVM ecosystem win!

In general, I don't have to think about what build system someone is using. I'm not here to debate subjective pros and cons of one versus any other. There is one notable exception, however. Maven's dependency resolution strategy is objectively bonkers. And if we want to support Maven consumers, we need to think about it.

If you already are familiar with the concept of dependency resolution, you can skip to [the nonsense](#the-nonsense).

### Dependency resolution primer

Chances are your build system of choice (or a separate dependency resolver tool) gives you a declarative way to describe your dependencies. At build time, those declarations are resolved to `.jar`s which can be put on the compiler classpath.

Sometimes we call this a _dependency tree_, but it's actually a _dependency graph_, as separate nodes can converge back to something common to both.

```
Project (build.gradle)
├── A
│   └── B
│       └── C v1.0
└── D
    └── C v1.0
```

If library `B` and library `D` agree on the version of library `C`, then that is the `.jar` version which is used. If they disagree on versions, some policy needs to decide the appropriate single version to use.

Pop quiz: If library `B` wants version 1.1 of library `C`, and library `D` wants version 1.0 of library `C`, which single version of `C` should we use?

```
Project (build.gradle)
├── A
│   └── B
│       └── C v1.1
└── D
    └── C v1.0
```

This is not a trick question. Hopefully the answer feels obvious: you use the newer version, 1.1. That version is _probably_ compatible with 1.0, so it's safe for both library `B` and library `D` to use. We can't know for sure, to be clear, but it's a safe choice. This behavior is the default in many dependency resolvers, including the one inside Gradle.

### The nonsense

When building with Maven, given two dependencies who disagree on a transitive dependency version, the default resolution strategy is... uh... let's say "interesting". From [their docs](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Transitive_Dependencies),

> Maven picks the "nearest definition". That is, it uses the version of the closest dependency to your project in the tree of dependencies. ... Note that if two dependency versions are at the same depth in the dependency tree, the first declaration wins.

So in a dependency graph, if library `B` wants version 1.1 of library `C`, and library `D` wants version 1.0 of library `C`, which single version of `C` does Maven choose?

```
Project (pom.xml)
├── A
│   └── B
│       └── C v1.1
└── D
    └── C v1.0
```

The final build will use version 1.0 of library `C`. Wat.

If library `B` was using a new API from library `C`'s version 1.1, the application will throw a `NoSuchMethodException` or the like at runtime.

As if that wasn't bad enough, disagreements which occur on the same conceptual level of the graph are resolved by whichever comes first. If our project replaces its library `A` with direct usage of library `B`, suddenly the resolved version is 1.1 because it came first.

```
Project (pom.xml)
├── B
│   └── C v1.1
└── D
    └── C v1.0
```

But if by chance library `D` was declared first in the `pom.xml` then oops! we're back to getting 1.0.

```
Project (pom.xml)
├── D
│   └── C v1.0
└── B
    └── C v1.1
```

This behavior is not user-friendly. You can always force a specific version by declaring it directly in your `pom.xml`, but that also means you take ownership of monitoring the versions requested by the entire dependency graph and ensuring you declare the one you need. Gee, that sounds like something it should do for you.

### Still a Gradle problem

So Maven has some nonsensical dependency resolution semantics. Why should you, a Gradle user, even care?

In the examples above, the version mismatches were demonstrated using peer dependencies on the Maven project. But disagreements can occur within the transitive graph of a single Gradle-built library.

If I am the author of library `A` from above, I only have a dependency on library `B` and it has a dependency on library `C`.

```
Project A (build.gradle)
└── B
    └── C v1.1
```

If I want to start using library `C`, I _may_ add my own dependency (such as if `C` is an implementation dependency of `B`) and select an older version.

```diff
 Project A (build.gradle)
 ├── B
 │   └── C v1.1
+└── C v1.0
```

I have just unknowingly created a time bomb for all of my Maven consumers.

### Not a hypothetical

Is this a frequent problem? Seems like no. Is this a real problem? Absolutely.

OkHttp 4.12 [ships with two dependencies](https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.pom): Okio 3.6 and the Kotlin stdlib 1.8.21. Okio 3.6, however, [depends on](https://repo1.maven.org/maven2/com/squareup/okio/okio-jvm/3.6.0/okio-jvm-3.6.0.pom) Kotlin stdlib 1.9.10.

```
OkHttp v4.12.0
├── Okio v3.6.0
│   └── Kotlin stdlib v1.9.10
└── Kotlin stdlib v1.8.21
```

This specific configuration is probably okay in practice, as Okio is unlikely to have used anything new. In general, however, the ability to create such a dependency graph with a mismatch is setting our Maven users up for future failure.

### Detecting from Maven

If you are a Maven user, you can eagerly detect this case by using the [Maven enforcer plugin](https://maven.apache.org/enforcer/maven-enforcer-plugin/index.html) and its built-in [dependency convergence rule](https://maven.apache.org/enforcer/enforcer-rules/dependencyConvergence.html).

A Maven project with an OkHttp 4.12 dependency will now fail like this:
```
[ERROR] Rule 0: org.apache.maven.enforcer.rules.dependency.DependencyConvergence failed with message:
[ERROR] Failed while enforcing releasability.
[ERROR]
[ERROR] Dependency convergence error for org.jetbrains.kotlin:kotlin-stdlib-jdk8:jar:1.9.10 paths to dependency are:
[ERROR] +-com.example:example:jar:1.0-SNAPSHOT
[ERROR]   +-com.squareup.okhttp3:okhttp:jar:4.12.0:compile
[ERROR]     +-com.squareup.okio:okio:jar:3.6.0:compile
[ERROR]       +-com.squareup.okio:okio-jvm:jar:3.6.0:compile
[ERROR]         +-org.jetbrains.kotlin:kotlin-stdlib-jdk8:jar:1.9.10:compile
[ERROR] and
[ERROR] +-com.example:example:jar:1.0-SNAPSHOT
[ERROR]   +-com.squareup.okhttp3:okhttp:jar:4.12.0:compile
[ERROR]     +-org.jetbrains.kotlin:kotlin-stdlib-jdk8:jar:1.8.21:compile
```

Now a Maven consumer can temporarily resolve the conflict, and go and ask the library maintainer to correct this configuration.

### Ignoring the problem with Gradle

Since Gradle is going to resolve to the newest version of a dependency, your tests end up running with the newest version rather than the declared version. As such, you can tell Gradle to replace your declared version with the resolved version when publishing.

This behavior is not Gradle's default, so we must choose it when setting up publishing. The [Gradle docs](https://docs.gradle.org/current/userguide/publishing_maven.html#publishing_maven:resolved_dependencies) has an example:

```groovy
publishing {
  publications {
    mavenJava(MavenPublication) {
      versionMapping {
        usage('java-api') {
          fromResolutionOf('runtimeClasspath')
        }
        usage('java-runtime') {
          fromResolutionResult()
        }
      }
    }
  }
}
```

There's very little harm in doing this, and it will prevent the Maven issue completely. Nice!

The tradeoff is that it somewhat undermines the versions you declare. Keep in mind, though, even if you declare a dependency version and resolve to that same version, a downstream consumer may resolve a newer version or force an older version.

For me, I want the versions which I declare to be those which are resolved, at least local to my project. So this solution isn't going to work, but it might for your projects.

(Thanks to [Paul Merlin](https://mastodon.social/@eskatos/112174733070682832) for suggesting this solution which was added after initial publishing)

### Trying to fix with Gradle

I'm going to outright dismiss "just don't use Maven" as a potential fix. There are lots of reasons not to use Maven that one can explore elsewhere. Ultimately it remains in widespread use, and you can either be sympathetic to those users or not.

Library developers using Gradle could change the default resolution strategy to [fail on version conflict](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.ResolutionStrategy.html#org.gradle.api.artifacts.ResolutionStrategy:failOnVersionConflict()). This does precisely what it says, fails your build if the transitive graph contains conflicts.

```groovy
// OkHttp's build.gradle
dependencies {
  implementation 'com.squareup.okio:okio:3.6.0'
  implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.8.21'
}

configurations.configureEach {
  resolutionStrategy.failOnVersionConflict()
}
```

Now when building we get a failure:

```
Execution failed for task ':compileJava'.
> Could not resolve all dependencies for configuration ':compileClasspath'.
   > Conflicts found for the following modules:
       - org.jetbrains.kotlin:kotlin-stdlib-common between versions 1.9.10 and 1.8.21
       - org.jetbrains.kotlin:kotlin-stdlib between versions 1.9.10 and 1.8.21
```

The failure suggests running `dependencyInsight`, which shows you a wall of text containing the subgraph of affected dependencies which led to the conflict.

```
> Task :dependencyInsight
Dependency resolution failed because of conflicts on the following modules:
   - org.jetbrains.kotlin:kotlin-stdlib-common between versions 1.9.10 and 1.8.21

org.jetbrains.kotlin:kotlin-stdlib-common:1.9.10
  Variant compile:
    | Attribute Name                 | Provided | Requested    |
    |--------------------------------|----------|--------------|
    | org.gradle.status              | release  |              |
    | org.gradle.category            | library  | library      |
    | org.gradle.libraryelements     | jar      | classes      |
    | org.gradle.usage               | java-api | java-api     |
    | org.gradle.dependency.bundling |          | external     |
    | org.gradle.jvm.environment     |          | standard-jvm |
    | org.gradle.jvm.version         |          | 21           |
   Selection reasons:
      - By conflict resolution: between versions 1.9.10 and 1.8.21

org.jetbrains.kotlin:kotlin-stdlib-common:1.9.10
+--- com.squareup.okio:okio-jvm:3.6.0
|    \--- com.squareup.okio:okio:3.6.0
|         \--- compileClasspath
\--- org.jetbrains.kotlin:kotlin-stdlib:1.9.10
     +--- compileClasspath (requested org.jetbrains.kotlin:kotlin-stdlib:1.8.21)
     +--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10
     |    \--- com.squareup.okio:okio-jvm:3.6.0 (*)
     \--- org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10
          \--- org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10 (*)
```

The fix for OkHttp is simple: upgrade to a matching version.

Unfortunately, if you upgrade to a version that's newer than your transitive dependency, the build still fails.

```
> Could not resolve all dependencies for configuration ':compileClasspath'.
   > Conflicts found for the following modules:
       - org.jetbrains.kotlin:kotlin-stdlib between versions 1.9.23 and 1.9.10
       - org.jetbrains.kotlin:kotlin-stdlib-jdk8 between versions 1.9.10 and 1.8.0
       - org.jetbrains.kotlin:kotlin-stdlib-common between versions 1.9.23 and 1.9.10
       - org.jetbrains.kotlin:kotlin-stdlib-jdk7 between versions 1.9.10 and 1.8.0
```

You have to force the use of 1.9.23 everywhere, but doing so will ironically prevent `failOnVersionConflict()` from detecting mismatches in the future.

Gradle has other mechanisms like [constraints](https://docs.gradle.org/current/userguide/rich_versions.html) and [resolution strategy callbacks](https://docs.gradle.org/current/userguide/resolution_rules.html) that have tons of power to customize dependency resolution, but none provide the ability to reject upgrades. I would love to be corrected on this, but I spent a few days searching and experimenting with no success. Instead, we have to build our own solution.

### Actually fixing with Gradle

I wrote a task which consumes the dependency graph and checks if the first-order dependencies (i.e., those your project declared directly) select the same version as they request.

```
> Task :sympathyForMrMaven FAILED
e: org.jetbrains.kotlin:kotlin-stdlib:1.8.21 changed to 1.9.10

* What went wrong:
Execution failed for task ':sympathyForMrMaven'.
> Declared dependencies were upgraded transitively. See task output above. Please update their versions.
```

When I bump my declaration to 1.9.10 to match, or even 1.9.23 which is the latest right now, the task no longer fails.

```
BUILD SUCCESSFUL in 354ms
4 actionable tasks: 4 executed
```

This is what I hacked up in Groovy _very_ quickly this morning (and to finish the damn post):

```groovy
def fail = false
def root = configuration.incoming.resolutionResult.rootComponent.get()
((ResolvedComponentResult) root).dependencies.forEach {
  if (it instanceof ResolvedDependencyResult) {
    def rdr = it as ResolvedDependencyResult
    def requested = rdr.requested
    def selected = rdr.selected
    if (requested instanceof ModuleComponentSelector) {
      def requestedVersion = (requested as ModuleComponentSelector).version
      def selectedVersion = selected.moduleVersion.version
      if (requestedVersion != selectedVersion) {
        logger.log(ERROR, "e: ${rdr.requested} changed to ${selectedVersion}")
        fail = true
      }
    }
  }
}
if (fail) {
  throw new IllegalStateException("Declared dependencies were upgraded transitively. See task output above. Please update their versions.")
}
```

This needs cleaned up before it can be used generally–sorry! In a long post about how Maven's dependency resolution is annoying, I instead became [_very_ annoyed at Gradle](https://jakewharton.com/@jw/112171457869714385) and just want to stop working on this.

Someone please change it to Java, wrap it in a task, wrap that in a `com.yourname.maven-sympathy` plugin, publish to Maven Central, and ping me to update this post. I have about 30 projects I'd love to slap it on, and hopefully other sympathetic library authors who read this post will too!
