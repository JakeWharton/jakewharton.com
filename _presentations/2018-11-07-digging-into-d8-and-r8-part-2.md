---
layout: presentation

event: Android Dev Summit
location: Mountain View, CA, USA
homepage: https://developer.android.com/dev-summit/
listing: https://developer.android.com/dev-summit/
date: 2018-11-07

type: Technical

title: Digging into D8 and R8 (Part 2)
---

D8 has replaced DX as the default dexer, the tool which converts Java bytecode to Dalvik bytecode. R8 is coming as the default shrinker, the tool which removes and optimizes code. While you may have heard about one or both of these tools before, not a lot of information exists on the advantages they bring. This talk will cover those advantages both in the scope of your entire application but also with very localized examples in a single class or method. We'll also look at some tricks that D8 and R8 can do which their predecessors could not. Finally, we'll cover how to enable both tools in your build and recommendations to ensure you're getting the most out of each.
