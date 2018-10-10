---
layout: presentation

event: Droidcon
location: New York City, NY, USA
homepage: http://droidcon.nyc/
listing: http://droidcon.nyc/
date: 2018-08-27

type: Technical

title: Digging into D8 and R8
speakerdeck: 30ccda6cb0f642c78c89d35c675ce1c7
youtube: 99H7COwhIpI
---

D8 has replaced DX as the default dexer, the tool which converts Java bytecode to Dalvik bytecode. R8 is coming as the default shrinker, the tool which removes and optimizes code. While you may have heard about one or both of these tools before, not a lot of information exists on the advantages they bring. This talk will cover those advantages both in the scope of your entire application but also with very localized examples in a single class or method. We'll also look at some tricks that D8 and R8 can do which their predecessors could not. Finally, we'll cover how to enable both tools in your build and recommendations to ensure you're getting the most out of each.
