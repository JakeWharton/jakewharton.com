---
title: 'Multiplatform Compose and Gradle module metadata abuse'
layout: post

categories: post
---

My primary work project for the better part of a year (named Redwood) is built on top of Compose[^1] and runs on every platform that Kotlin supports. This of course means Android, but we also have Compose running on iOS, the web, the JVM, and all other native targets. It's truly a multiplatform Compose project[^2].

[^1]: Obligatory: [I mean Compose and **NOT** Compose UI][1]!
[1]: /a-jetpack-compose-by-any-other-name/
[^2]: Continuing with the poor naming surrounding Compose, JetBrains has a project called "Compose Multiplatform" which is not fully multiplatform nor fully ports Compose UI to each supported platform. Our project is "just" the Compose runtime (not Compose UI) but running fully multiplatform.

Getting Compose to run on all these platforms isn't as hard as you would think. The Compose runtime is written as multiplatform Kotlin code but Google only ships it compiled for Android. JetBrains goes farther by shipping versions compiled for the web and for the JVM. We simply go the whole distance and compile it for every Kotlin target, while also shipping it as a single Kotlin multiplatform artifact.

For a year this worked fine. However, Compose UI recently went stable which meant our Android engineers were eager to start using it in the main app (as opposed to just samples). Upon Compose UI's introduction D8 fails with a duplicate class error:

```
> Duplicate class androidx.compose.runtime.AbstractApplier found in
  redwood-compose-runtime (app.cash.redwood:compose-runtime-android:0.1.0-square.15) and
  runtime-1.0.0-runtime (androidx.compose.runtime:runtime:1.0.0)
```

The `androidx.compose.*` types are compiled into Redwood's multiplatform Compose runtime artifact. Compose UI depends on the official Compose runtime for Android which also contains these types. Since the two artifacts have different Maven coordinates, Gradle allows both to be included in the app which eventually causes D8 to complain[^3].

[^3]: Unlike the JVM whose classpath is a set of jars which each contain classes where the first wins, Android's classpath is a single set of classes in which duplicates are not supported (because of the dex file format).

Redwood was already building Compose from the same git SHAs as Google's release builds. Ideally we could use our own builds for every platform _except_ Android, and then point at Google's artifact solely for Android. This would allow Gradle to see the two projects as sharing a common dependency thereby de-duplicating the Compose runtime classes.

### Gradle module metadata

The mechanism by which Kotlin multiplatform artifacts resolve the correct dependency is through Gradle's [module metadata format][2].

[2]: https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html

> Gradle Module Metadata is a unique format aimed at improving dependency resolution by making it multi-platform and variant-aware.

The module metadata is a JSON document which describes the supported platforms through key/value attributes. For Redwood's Compose runtime the module metadata looks roughly like this:

```json
{
  "component": {
    "group": "app.cash.redwood",
    "module": "compose-runtime",
    "version": "0.1.0-square.15"
  },
  "variants": [
    {
      "name": "releaseApiElements-published",
      "attributes": {
        "org.gradle.usage": "java-api",
        "org.jetbrains.kotlin.platform.type": "androidJvm"
      },
      "available-at": {
        "url": "../../compose-runtime-android/0.1.0-square.15/compose-runtime-android-0.1.0-square.15.module",
        "group": "app.cash.redwood",
        "module": "compose-runtime-android",
        "version": "0.1.0-square.15"
      }
    },
    {
      "name": "iosArm64ApiElements-published",
      "attributes": {
        "artifactType": "org.jetbrains.kotlin.klib",
        "org.gradle.usage": "kotlin-api",
        "org.jetbrains.kotlin.native.target": "ios_arm64",
        "org.jetbrains.kotlin.platform.type": "native"
      },
      "available-at": {
        "url": "../../compose-runtime-iosarm64/0.1.0-square.15/compose-runtime-iosarm64-0.1.0-square.15.module",
        "group": "app.cash.redwood",
        "module": "compose-runtime-iosarm64",
        "version": "0.1.0-square.15"
      }
    },
    ...
  ]
}
```

When a 64-bit iOS ARM target consumes the `app.cash.redwood:compose-runtime` dependency, Gradle will parse this JSON file and actually resolve the `app.cash.redwood:compose-runtime-iosarm64` artifact. It behaves somewhat like an HTTP 302 redirect by replacing the user-friendly Maven coordinate with the canonical platform-specific coordinate.

For an Android consumer the artifact redirect resolves to `app.cash.redwood:compose-runtime-android` which is one of the offending artifact coordinates seen in the duplicate class error from D8. As I mentioned above, what we want is to have this variant redirect to Google's build of the Compose runtime and not our own.

We could try to alter the values in the `available-at` object to point to Google's artifact, but according to the [Gradle module metadata spec](https://github.com/gradle/gradle/blob/master/subprojects/docs/src/docs/design/gradle-module-metadata-latest-specification.md#available-at-value) the `url` key must also point to a metadata file which is something Google does not ship.

Thankfully, just below `available-at` in the spec, the [`dependencies` array](https://github.com/gradle/gradle/blob/master/subprojects/docs/src/docs/design/gradle-module-metadata-latest-specification.md#dependencies-value) affords the ability to point at arbitrary Maven coordinates. This would allow us to define a variant with no `available-at` but a single `dependency` item to the associated Google Compose runtime artifact.
```diff
 {
   "name": "releaseApiElements-published",
   "attributes": {
     "org.gradle.usage": "java-api",
     "org.jetbrains.kotlin.platform.type": "androidJvm"
   },
-  "available-at": {
-    "url": "../../compose-runtime-android/0.1.0-square.15/compose-runtime-android-0.1.0-square.15.module",
-    "group": "app.cash.redwood",
-    "module": "compose-runtime-android",
-    "version": "0.1.0-square.15"
-  }
+  "dependencies": [
+    {
+      "group": "androidx.compose.runtime",
+      "module": "runtime",
+      "version": {
+        "prefers": "1.0.4"
+      }
+    }
+  ]
 }
```

But the module metadata file is entirely generated by Gradle based on project information. How can we modify it to change the output of only a single variant?

### Modifying Gradle module metadata

Spoiler alert: You can't. At least not using any stable APIs that Gradle provides[^4].

[^4]: As of Gradle 7.2.

The best (only?) mechanism that I've found is to hook into the module metadata file generation task and perform text-based modification of the JSON immediately after it is generated.

First, we define a text file which contains the expected JSON contents to be replaced[^5].

[^5]: Omitted from the earlier example, some variants have both an "api" and "runtime" entry.

```json
    {
      "name": "releaseApiElements-published",
      "attributes": {
        "org.gradle.usage": "java-api",
        "org.jetbrains.kotlin.platform.type": "androidJvm"
      },
      "available-at": {
        "url": "../../compose-runtime-android/{REDWOOD_VERSION}/compose-runtime-android-{REDWOOD_VERSION}.module",
        "group": "app.cash.redwood",
        "module": "compose-runtime-android",
        "version": "{REDWOOD_VERSION}"
      }
    },
    {
      "name": "releaseRuntimeElements-published",
      "attributes": {
        "org.gradle.usage": "java-runtime",
        "org.jetbrains.kotlin.platform.type": "androidJvm"
      },
      "available-at": {
        "url": "../../compose-runtime-android/{REDWOOD_VERSION}/compose-runtime-android-{REDWOOD_VERSION}.module",
        "group": "app.cash.redwood",
        "module": "compose-runtime-android",
        "version": "{REDWOOD_VERSION}"
      }
    },
```

Notice how the `{REDWOOD_VERSION}` placeholder is used to minimize changes to this file over time.

Next, define the replacement JSON in another file.
```json
    {
      "name": "releaseApiElements-published",
      "attributes": {
        "org.gradle.usage": "java-api",
        "org.jetbrains.kotlin.platform.type": "androidJvm"
      },
      "dependencies": [
        {
          "group": "androidx.compose.runtime",
          "module": "runtime",
          "version": {
            "prefers": "{COMPOSE_VERSION}"
          }
        }
      ]
    },
    {
      "name": "releaseRuntimeElements-published",
      "attributes": {
        "org.gradle.usage": "java-runtime",
        "org.jetbrains.kotlin.platform.type": "androidJvm"
      },
      "dependencies": [
        {
          "group": "androidx.compose.runtime",
          "module": "runtime",
          "version": {
            "prefers": "{COMPOSE_VERSION}"
          }
        }
      ]
    },
```

Once again we use a special string `{COMPOSE_VERSION}` to minimize the need to change this file as we update to new Compose versions.

Finally, perform this text-based substitution immediately after the file is generated. Here the `{REDWOOD_VERSION}` and `{COMPOSE_VERSION}` placeholders are replaced with their real values.

```groovy
tasks.named("generateMetadataFileForKotlinMultiplatformPublication").configure {
  doLast {
    String find = file('module_find.txt').text.replace('{REDWOOD_VERSION}', version)
    String replace = file('module_replace.txt').text.replace('{COMPOSE_VERSION}', versions.compose)

    File file = outputFile.get().getAsFile()
    String text = file.text

    int start = text.indexOf(find)
    if (start == -1) {
      throw new RuntimeException("Unable to locate module_find.txt contents in module JSON ($file)")
    }
    int end = start + find.length()

    String newText = text.substring(0, start) + replace + text.substring(end)
    file.text = newText
  }
}
```

This is some very hacky code, but any unexpected changes to the module metadata format will cause a build failure allowing you to reevaluate the approach. Perhaps in the future Gradle will support this type of transformation [with a stable public API](https://github.com/gradle/gradle/issues/18862).

This simple text substitution solves the original duplicate class problem today. And it does so in a way which does not require the consumer to understand the nuances of how the Compose runtime is built.

---

Despite solving the issue for Android builds, we still have the duplicate class problem for the other platforms on which multiple Compose-based projects can be used. If you happened to use Redwood on the JVM with JetBrains' Compose for Desktop you would have two copies of the Compose runtime (potentially built from different versions). The same is true for targeting the web and using JetBrains' Compose for Web.

Google really should be shipping the Compose runtime as a proper multiplatform artifact for all Kotlin targets to remedy this situation. Unfortunately their Kotlin multiplatform story is a few years behind the community's need and the prospect of this happening anytime soon is very unlikely. The best we can hope for now is JetBrains to ship a proper multiplatform artifact of the Compose runtime with the same versioning as Google's and using this hack to point the Android variant at Google's binary. Then everyone in the multiplatform Compose space could standardize on their artifacts.

Until then, however, we'll continue the imperfect practice of building our own Compose runtime for Redwood and pointing to Google's artifact for Android[^6].

[^6]: We also have to build the Compose Kotlin compiler plugin for native because of how the Kotlin/Native compiler works. [Google could ship it](https://issuetracker.google.com/issues/205021616), or [JetBrains could make the existing plugins work for native](https://youtrack.jetbrains.com/issue/KT-27683).