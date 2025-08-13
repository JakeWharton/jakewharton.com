---
layout: presentation

event: Droidcon
location: Toronto, Canada
homepage: https://www.to.droidcon.com/
listing: https://www.to.droidcon.com/
date: 2019-11-13

type: Technical

title: "Diffusing Changes in Your APK"
speakerdeck: 7cf9abccac794263b13d96fb357ecbc2
youtube: 5a-sRoPVEZk
---

Nearly every change to your project affects the final APK is some way. Whether adding a method, removing a class, updating a dependency, changing the Android Gradle plugin version, importing new translations, or refactoring a layout XML, all of these will change the size of your APK.

But what is actually going on in the APK when you make these changes? Each affects more than you think. Unfortunately, there's little visibility into this aside from birds-eye-view numbers like APK size and method count. Diffuse is a tool which aims to help provide that insight by breaking down the changes based on what is affected and then showing granular diffs.

This talk will go through examples of each of the scenarios above and explore how the tool surfaces what actually changes as a result. With each example, we'll explore what makes up an APK (and other formats like AAR, JAR, and AAB). Finally, we'll talk about how you can integrate diffuse into your development workflow so its reports are available automatically for every change.
