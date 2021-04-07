---
title: 'Integration verbosity and good layering'
layout: post

categories: post
---

One of my favorite non-features from building [view binding][vb] is that it lacks integration with
activities or fragments. If you use view binding with activities or fragments, however, this fact
might be to your disdain. Every activity using view binding is forced to do something along the
lines of:

[vb]: https://developer.android.com/topic/libraries/view-binding

```kotlin
override fun onCreate(savedInstanceState: Bundle) {
  super.onCreate(savedInstanceState)

  val binding = ProfileViewBinding.inflate(layoutInflater)
  setContentView(binding.root)

  // Do stuff with 'binding'
}
```

This is textbook verbosity, and some would argue boilerplate. It only gets worse with fragments
(due to their poor design and to no specific fault of view binding which works the same as any
`View` reference).

View binding exists at a different layer of abstraction than is appropriate for integration with
higher-level components like activities or fragments. It serves as a type-safe representation of a
schema declared in an XML file and that's it. It has no more knowledge of activities and fragments
than the associated `R.layout.profile_view` integer does.

Higher-level libraries like androidx.activity and androidx.fragment have integrations with those
`R.layout` integers. If you're upset that view binding has no turn-key solution for activities and
fragments then this is the tree you should be barking up.

View binding wasn't built with verbosity in mind.
Hell, it's not even _that_ verbose.
It ended up this way because it's the design the layer of abstraction it operates at demands.

The same pattern occurs in some of my other favorite libraries.
Dagger offers you nothing and requires that you build up the dependency injectors, their hierarchy, and their lifecycle entirely yourself.
SQLDelight makes you specify database info in the build configuration and a database driver in the runtime API.
RecylerView requires at minimum an adapter subtype and to choose and configure a layout manager.
The layer at which these tools operate is sufficiently general such that their good design requires them to hoist a bunch of decisions to their caller.

If Dagger was more opinionated about integration with Android it's hard to imagine Hilt could have been built as it is today.
If SQLDelight was more opinionated about talking to SQLite on Android it's hard to imagine it could support talking to SQLite, MySQL, or Postgres on any platform as it does today.
If RecyclerView was more opinionated about layout managers or adapters it's hard to imagine ViewPager 2 could have been built as it is today.

I certainly bear many scars of layering mistakes in my library past.

Picasso shipped with a global, static `get()` method so that image loading could be a one-liner with no setup.
But what if you need to configure the HTTP client or set cache policy or need two different versions of those things?
Libraries even shipped on top of Picasso using `get()` and assuming it would behave a certain way.
It is a mistake to assume there will be only one configuration even if it is true 99% of the time.
~~Half-life 3~~ Picasso 3 (if it ever ships) corrects this mistake by only offering instance-based APIs.
If you want a global instance it's only one line of code, and it's now your decision to make.

Retrofit 1 shipped with a Gson dependency that was enabled by default.
You could still swap in a different converter if you wanted, but Gson would always be there.
It is a mistake to assume someone will be speaking JSON and that they will want to use Gson even if that was true (then) 99% of the time.
We know literally nothing about the enclosing application or the server it's speaking to!
Retrofit 2 corrects this mistake by only speaking bytes in its core.
You're forced to bring a serialization format converter and configure it on each instance, even if it's always JSON and Gson (please stop using Gson).

You can usually spot these types of problems in libraries because they start to accumulate weird
ceremony in order to support different use-cases like testing[^1]. It can be tempting as a library
author to over-correct away from exposing verbosity. By removing required configuration options and
reducing the use of inversion of control you make the happy path happy, but alternative use-cases
and alternative integrations become much harder.

 [^1]: Um, this sentence is somewhat ridiculous, right? Testing is not a _different_ use case. It's a primary use case! I hope libraries come to mind here. Many do for me when I wrote it.

Instead of trying to push verbosity down into the library when faced with situations like the view
binding activity usage above, package it into an integration library that's easy to evolve or throw
away. When one of those integrations inevitably disappears, or a new one arrives, your core library
won't need to change.
