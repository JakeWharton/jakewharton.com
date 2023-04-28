---
layout: presentation

event: KotlinConf
location: Amsterdam, Netherlands
homepage: https://kotlinconf.com
listing: https://kotlinconf.com/speakers/3eabdd46-6cb5-4083-9eb8-1ad9a3b7a5eb/
date: 2023-04-14

type: Technical

title: "Playing in the Treehouse with Redwood and Zipline"
speakerdeck: 693336bdd82f4cfc8c5e1a8605e66af7
youtube: G4LK_euTadU
---

Redwood is Cash App's multiplatform Compose library which targets the native UI toolkit of each platform while still sharing presentation logic. This means our app's existing Android views and iOS UIViews can be powered by common Compose and still offer a path to Compose UI and Swift UI.

Zipline is a multiplatform Javascript engine for Android, iOS, and the JVM which uses Kotlin interfaces for calls in and out of JS. This allows us to update the logic of our apps faster than going through the app store release process.

Treehouse is what we call the combination of Redwood and Zipline. By moving the Compose presentation logic into Zipline we can update the screens of our apps across Android, iOS, and web without waiting for the app store.

This talk will cover how Redwood and Zipline can work in isolation but how the combination as Treehouse is their most powerful form.
