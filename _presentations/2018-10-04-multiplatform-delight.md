---
layout: presentation

event: KotlinConf
location: Amsterdam, Netherlands
homepage: https://www.kotlinconf.com/
listing: https://www.kotlinconf.com/
date: 2018-10-04

type: Technical
additional_presenters:
- Alec Strong

title: A Multiplatform Delight
speakerdeck: eb7e738565e149c1bd3b81ee7b11b639
youtube: WkIry790PHI
---

SQL Delight, a type-safe database API, recently completed migration from being a Java-generating, Android-specific tool to a Kotlin-generating, multiplatform one. Migrating an API from Java to Kotlin has obvious benefits, but adding multiplatform support for iOS introduces a dynamic which complicates the API, code generation, and runtime.

This talk will cover the challenges of platform-agnostic API design, type-safe multiplatform Kotlin code generation, and the integration of platform-specific runtimes such that the library not only runs efficiently on each platform but also integrates well with the other languages each might be using.

_Presented with Alec Strong_