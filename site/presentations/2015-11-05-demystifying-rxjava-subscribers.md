---
layout: presentation

event: Øredev
location: Malmö, Sweden
homepage: http://oredev.org/
date: 2015-11-05

type: Technical

title: Demystifying RxJava Subscribers
vimeo: 144812843
speakerdeck: d439c614db4d42aa96f310ed6ab329d8

redirect_from:
  - /presentation/2015-11-05-oredev/
---

RxJava is a powerful library for creating and composing streams of data. It can quickly be used to great effect, but a deeper understand of its internals will prevent running into pitfalls later on.

This talk will focus on the core mechanism of how streams are created and observed: subscribers and subscriptions. We will start with an introduction to the contract of the Subscriber type and how it is used by sources to create streams. Then we will touch on operators and how they use subscribers to modify the data flowing through streams. Finally we'll look at how threading behaves in operators like subscribeOn and observeOn.

Only a very basic level of RxJava knowledge is required for this talk. It will be assumed that you have used or at least seen the basics of RxJava's API before.
