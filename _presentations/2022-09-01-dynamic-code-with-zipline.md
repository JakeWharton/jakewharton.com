---
layout: presentation

event: Droidcon
location: New York City, NY, USA
homepage: https://nyc.droidcon.com
listing: https://nyc.droidcon.com/jake-wharton/
date: 2022-09-01

type: Technical
additional_presenters:
- Jesse Wilson

title: "Dynamic Code with Zipline"
speakerdeck: b9dc9be31039467f98ac1505bb9f54a7
vimeo: 754042291
---

As products grow, teams tend to move business logic to the backend. Keeping clients dumb avoids duplication and allows behavior changes without app releases. But it comes with significant downsides: limited interactivity, difficult development, and inflexible APIs.

Zipline is a new library from Cash App that takes a new approach. Instead of moving logic to the backend, Zipline runs dynamic Kotlin/JS code in your Android and iOS apps. It lets you ship behavior changes without an app release!

This talk advises when to use dynamic code and how to adopt it in your apps. It also goes deep on Zipline internals:

 - Interface bridging
 - Coroutines & Flows
 - Fast launches
 - Debugging features

If your apps are getting dumber, or you're using server-driven UI, don't miss this talk for a great alternative.

_Presented with Jesse Wilson_
