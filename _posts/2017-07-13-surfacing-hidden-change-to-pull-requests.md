---
title: Surfacing Hidden Change to Pull Requests
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/surfacing-hidden-change-to-pull-requests

categories: post
tags:
- Code Review
---

Programming frequently deals in visible changes: the logic in your code, the dependencies you declare, the API you expose. There is, however, quite a bit of associated hidden change: transitive dependencies, generated code, and manifest files.

In code review we focus on the visible because that is what is presented to us in the diff.

![](/static/post-image/hidden-change-0.png)

It’s equally as important to pay attention to the hidden changes during code review. Transitive dependency changes or generated code changes might bloat the resulting binary or reduce performance. Manifest changes might cause an incompatibility downstream. These problems are often found much later in the release process where an investigation is required to find the cause.

After running into a few problems with hidden changes on the Cash Android team, we decided to promote them to be more visible at the point where they change: the pull request.

In every CI build, we calculate interesting pieces of data like the binary size and method count (an important metric for Android apps) and write them to shared storage. When a CI build runs for a pull request, we figure out the difference of these values from the ancestor commit on master. The numbers are sent back to the pull request in the form of a comment to ensure the author and any reviewers are notified.

![](/static/post-image/hidden-change-1.png)

These important metrics are now surfaced explicitly to ensure that the result is what you intended. Does that new dependency only add 10 methods or do we incur 10,000? Does that new hero image add only 20KB or occupy 2MB?

---

In addition to these simple numerical stats, we also capture the entire dependency graph for the application and also of Gradle’s buildscript (the build system dependencies).

![](/static/post-image/hidden-change-2.png)

Here we had upgraded Kotlin from 1.0.4 to a 1.1 milestone. Not only do we save 3 methods, but we can see that they added a dependency to JetBrains’ annotations artifact from the kotlin-runtime artifact.

---

Android builds use something called a manifest merger to create the final manifest of an application. It merges together the manifest you’ve defined with those embedded in the libraries you use. This merged manifest is essentially the public API of your app from the Android operating system’s perspective, defining things like the entry points, exposed services, and permissions required. It’s very important to track changes in your manifest as it can cause incompatibilities when upgrading.

![](/static/post-image/hidden-change-3.png)

Square’s open source library Whorlwind simplifies adding fingerprint support to applications. It includes a permission in its manifest for the ability to use the fingerprint reader. When adding the library to Cash, this permission ends up in our merged manifest and thus shows up in the pull request comment.

While this example is harmless and intended, unexpected permissions or erroneously-exposed components can get merged in and cause upgrade problems or security vulnerabilities for your users.

---

Not all implicit information should be exposed directly in this comment. For example, method count changes always intrigue me to the point where I want to see the actual diff of the individual methods. These counts are sometimes in the thousands which would make the comment unusable and annoying. Instead of surfacing the method diff directly in the comment then, we render the diff to a file that gets included as an attachment to the CI build.

![](/static/post-image/hidden-change-4.png)

When you’re curious for more detailed information on what was included in the comment all you need to do is click through to the CI server. This example shows one of the build shards providing the full method diff, a report of what ProGuard removed, a report of what the resource shrinker removed, a Gradle profile showing build speeds, and an image rendering of the project module graph. Other shards include things like Android’s Lint report or summaries of test execution.

---

What you choose to include in a comment or in CI is subjective to your project. For Cash Android these are what we deem most valuable to surface. The goal should be the same though: surface information which is important but otherwise hidden into being visible.