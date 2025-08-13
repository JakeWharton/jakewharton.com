---
title: 'A Jetpack Compose by any other name'
layout: post

categories: post
---

I really like Jetpack Compose.
Between work and personal stuff I have three projects which are each built on top of it.
It's great!

So far my biggest problem is its name… but that requires some explaining.
Welcome to one of the hills I'll die on!

### What is Jetpack Compose?

If you're already familiar with it, something should pop in your head when asked:
_What_ is Jetpack Compose?

A new UI toolkit for Android? Yep, that's right.
A declarative Android UI framework? Sure, that is correct.
A multiplatform application UI? Thanks to JetBrains this is also true.

If you're somewhat in tune to how the sausage is made you may also reference the fact that it's a
Kotlin compiler plugin and DSL to build Android UI or multiplatform UI. It's those things, too.

None of these answers are wrong. However, they're doing a bit of a disservice 
to the internals of Compose and its unrealized potential.

### Pedigree

What we now know as Jetpack Compose started as two separate projects:

The first was a solution for writing declarative Android UIs using the existing platform UI toolkit.
Take the declarative components of React, wrap it in an ahead-of-time compiler like Svelte, and
target Android's UI toolkit with Kotlin. All existing `View`-based UI could suddenly level-up by
changing their programming paradigm from imperative to declarative.

Separately, the toolkit team was about to ramp up on unbundling as many UI widgets as possible from
the OS. This followed on the success of `ViewPager`, `RecyclerView`, and what was learned from
the AppCompat and Design libraries. By removing a ton of OEM touchpoints and normalizing behavior
across all versions of Android, the work required to build a good UI would be reduced.

Over time these efforts became inescapably linked.

If you are building standalone versions of the platform UI widgets then why not take the opportunity
to correct mistakes in their API and overcome limitations of the resource system? And if you're
changing their API, why not have the new declarative system target only these unbundled widgets?
Each project only empowered the other as they spiraled closer together.

In hindsight, it seems inevitable they would become a single effort. Being a single effort does
not necessarily mean tight coupling, however.

### Layering

Each of my three projects built on Compose do not use the new Compose UI toolkit. That can be a
confusing statement even to those who have done a lot of Compose work. Didn't we just call them
inescapably linked? Didn't we define it earlier as a UI toolkit?

While Compose became a single effort started from two projects, a layering and responsibility
split similar to those original projects still exists. In fact, that separation has only become
more defined.

What this means is that Compose is, at its core, a general-purpose tool for managing a tree of
nodes of any type. Well a "tree of nodes" describes just about anything, and as a result Compose can
target just about anything.

Those tree nodes could be the new Compose UI toolkit internals. But it could just as easily be the
old `View` nodes inside a `ViewGroup` tree, it could be a `RemoteView` and the various remote views
within its tree, or a notification and the content inside it.

The nodes don't have to be UI related at all. The tree could exist at the presenter layer as view
state objects, at the data layer as model objects, or simply be a value tree of pure data.

Compose doesn't care! This is the part I really like. This is the part that's great.

Separately, however, Compose is _also_ a new UI toolkit and DSL which renders applications on
Android and Desktop. This part of Compose is built on top of the aforementioned core as a tree of
nodes which happen to be able to render themselves to a canvas.

The two parts are called the Compose compiler/runtime and Compose UI, respectively.

This separation of concerns is very welcome. Conflating both under the name of Compose,
in my opinion, is not welcome.

### Naming

Adjusting the naming here would address two problems: specificity and pigeonholing.

Placing a general-purpose compiler and runtime with a specific UI toolkit implementation under an
umbrella name means discussions about them are imprecise by nature. This post started by saying
that I'm working on three projects built on Compose… did you think I meant Compose UI?
You almost certainly did.

The Compose name is more akin to Jetpack than it is AppCompat. We don't treat it like that, and
there are no signs of Google correcting our perception. So now I must endlessly clarify that I'm
working on three projects built on the Compose compiler/runtime which do not use the Compose UI
toolkit and oh, by the way, yes, those are separate things.

Maybe you don't think this is a good enough reason to have two names. After all, how many people
are going to build something on just the compiler and runtime?

Yet that is all the _more_ reason to rename it! You've just pigeonholed the project to not be
anything more than what it already is–a cool technology on which Compose UI is built. The possible
applications of the general-purpose Compose compiler/runtime are widespread and should be
encouraged. Right now it feels like Google buried the lede.

A separate name is an easy way to draw attention to the great work which is the compiler and runtime
of Compose. There's been the rare tweet about it, the casual mention in a talk, and the occasional
blog post showcasing a different use, but aside from that there's not much breathing room. The
excitement around Compose UI (which is also much deserved) drowns it out.

Compose compiler/runtime supports more platforms and targets than Compose UI. In addition to Android
I've run Compose-based projects on the JVM (in a server, not on desktop) and limped one along in
a non-browser JS engine. These are places where Compose UI is impossible, but Compose is not!

I'm excited for these three projects of mine to make their way into open source to showcase what the
Compose compiler and runtime can do on their own. I am not excited about having to continually
clarify that the Compose compiler/runtime duo are not related to Compose UI or to Android.

### Crane

The internal codename for the new UI toolkit was "crane". Before it was public I voiced support
of retaining that name. After Compose was public I voiced support of using two names and using
"crane" for Compose UI. But messages in chat rooms are easy to ignore–even if some agreed.

Unfortunately, much time has passed, and the tea leaves are showing that Compose is about to enter
beta. It's too late to make this naming change for Compose UI. No one even calls it Compose UI.
It's always been just Compose.

So this blog post is a hail-mary plea to Google:
please rename the compiler and runtime to something else!

Compose is such a bland name anyway.
Name it Evergreen (like the trees).
Name it Juliet (who wrote the blog title).
Hell, name it Crane (for maximum internal confusion).
Give it the different name that it deserves so it can stand on its own.

But please, don't relegate this amazing, general-purpose, multiplatform compiler and runtime to live
behind the blanket nomenclature that is just Compose!
