---
title: "Let's defuse the Compose BOM"

tags:
- Android
---

Many people rely on the [Compose bill of materials (BOM)](https://developer.android.com/develop/ui/compose/bom) artifact to provide the complete set of Compose dependency versions.

If we use Compose’s foundation 1.8.0 but a transitive dependency bumps foundation-layout, there’s a risk that these two versions are incompatible with each other despite otherwise being stable libraries.
The Compose BOM will unify the versions so that all are guaranteed to work with each other.

Since Compose comprises about 15 individual libraries, the Compose BOM provides us with only a single version that we have to manually change when upgrading.
Nice and simple.

But wait…

### We don't really need those things!

Every AndroidX library automatically bundles peer dependency constraints into its Gradle module metadata which ensures that within a library group all artifacts resolve to the same version.

Here's a fragment from the [Gradle module metadata](https://maven.google.com/androidx/compose/foundation/foundation-layout-android/1.10.0/foundation-layout-android-1.10.0.module) for foundation-layout v1.10.0:
```json
"dependencyConstraints": [
  {
    "group": "androidx.compose.foundation",
    "module": "foundation",
    "version": {
      "requires": "1.10.0"
    },
    "reason": "foundation-layout is in atomic group androidx.compose.foundation"
  },
  {
    "group": "androidx.compose.foundation",
    "module": "foundation-lint",
    "version": {
      "requires": "1.10.0"
    },
    "reason": "foundation-layout is in atomic group androidx.compose.foundation"
  }
],
```

This means that in the scenario above, with a mismatched transitive dependency bump, the module metadata instructs Gradle to automatically bump _all_ artifacts in that group.
No manual action or BOM usage required.

As to the single version, did you know there’s actually only five library groups in the Compose BOM?
Despite encompassing about 15 libraries, it’s only actually defining four distinct versions (Compose UI and Material library groups share a version).
Tools like Renovate or Dependabot can track the libraries in use and query upstream Maven repositories for new versions.
When one is available, a PR is automatically created bumping the affected libraries.
No manual version bumping required.

### Why does the Compose BOM exist at all then?

Build systems which do not use the Gradle module metadata will not automatically align sibling artifacts which exist in the same library group.
The BOM is a concept created by Maven, and it’s defined using the same `pom.xml` format as a normal artifact published to a Maven repository.
We publish BOMs for libraries with multiple artifacts like OkHttp, Retrofit, and Wire.
Someday we’ll also publish Gradle module metadata for those libraries (and more), making their BOMs entirely redundant for Gradle users.

Additionally, Gradle’s [version catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html) are a relatively new concept which standardizes and centralizes how a project defines its external library coordinates and their versions.
Prior to this, you either did it in build script code or for smaller projects put versions directly in the build file.
The Compose BOM allows these types of builds to omit the versions on the 15 individual libraries it covers and only specify its sole version.
When the version catalog is in place, however, the four Compose versions are defined once in the `[versions]` table.

### The BOM can hold you back

Unlike the normal AndroidX release cadence, it is only released once or sometimes twice a month.
Sometimes it inexplicably doesn’t release for a month or two.
Sometimes a new release actually contains zero changes from the last.
Sometimes it does bump all the Compose dependencies, but those libraries can still have their own individual releases between the BOM releases.
I don’t fully understand what process or policy causes all this inconsistency on Google’s side, but it all combines to make the BOM a somewhat unreliable source of versioning.

This single, date-based number ends up masking the real versions.
Oh, that bug you hit was fixed in Foundation 1.9.4?
Well you’re on 2025.10.01 of the BOM.
Now go find the website which contains the mapping to determine if you've already got the fix or not.

Finally, if you start [gradual adoption of AndroidX betas](/you-should-use-androidx-betas/), you are partially overriding the versions declared in the BOM.
So not only is it an indirection that masks the real versions, it’s not even the full source of truth.

Because the versions in the BOM still participate in normal Gradle dependency resolution semantics, transitive dependencies and local overrides can change the resolved version.
Unless you commit a lock file, you won't actually know what version is going into your final artifact.

---

The next time you start a new project, consider defining the two or three versions of the Compose library groups you use the same way you do every other dependency.
And if you're already on an existing project which is using it, consider calling in the BOM squad to safely dispose of this artifact.

🚫💣
