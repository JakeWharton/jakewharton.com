---
title: Advanced Pre-Honeycomb Animation
layout: post
published: false

categories: post
tags:
- Android
- NineOldAndroids

lead: Translation, scale, and other transformations were introduced in Honeycomb but can still be used on previous platforms.
---

The lovely new [animation framework][1] in Android 3.0 came with some additional
methods on the <code>View</code> class to allow for transformations such as
translation, scale, rotation, and alpha. The [NineOldAndroids][2] library allows
for the use of this API on any platform but is limited only to modifying values
for which methods exist on the running platform.

Recently I [set out][3] to solve this problem and allow for utilizing my library
to animate these properties regardless of the API level. Neither an answer to
the linked StackOverflow question nor talking to the animation guru Chet Haase
himself semed to produce a reliable, stable implementation for this--the
recommendation always being to just use the build-in view animation.

As I was digging around in the `View` class I noticed that there really was no
way to achieve this effect directly even with reflection. It was only once I
started poking around how view animations are processed and executed did a
rather clever solution appear to me.

For those that are not familiar with how the view animation framework works,
an animation receives a callback with a `Transformation` object and a time
interval. Each animation then adjusts the object in order to reflect the state
of the associated view at whatever time interval it is at. Since the
`Transformation` object contains a method for setting alpha and a matrix which
is applied to the canvas rendering the view we can easily achieve all of the
transformations of the native methods introduced in Honeycomb.

So now that we know these transformations were possible how best to implement
them in a manner that can be used by the new animation API. To accomplish this
we use a few tricks of view animation in order to do this in a way that is as
lightweight and fast as possible (we're on the UI thread, remember).

Only one animation can be applied to a view at a time so it was obvious that a
custom class extending `Animation` was required to apply our many
transformations. Now it became a matter of synchronizing our new class with the
NineOldAndroids library since it would be the one actually controlling the
animation.

Instead of attempting to integrate NineOldAndroids directly in our class or try
to make the NineOldAndroids library aware of our class I chose an alternate
route. Our animation class would act only as a proxy to the alpha and the
`Transformation` object by exposing methods to allow changing the various
properties that were introduced in Honeycomb.

In order to take the native behavior of view animation out of the equation, our
custom class immediately sets two properties on itself: `setDuration(0)` and
`setFillAfter(true)`. This effectively disables the timer internally triggering
the transformation and it allows the transformations that we make to be
persisted on the view after the animation has completed. In order for the latter
to occur the animation is kept around so that its transformation can be applied
whenever the view is invalidated. This is the behavior that we leverage in order
to provide our animation.

We expose our new properties as getter and setter methods that the new animation
API can interact with and hold them in instance variables in our animation. Each
invalidation then triggers our callback which we can then apply the newly
updated values for each property, thus, animating the view.

This works extremely well and provides fluid, multi-property animation using
NineOldAndroids for the new animation API but it still requires us to use the
animation class for these specific properties. In order to provide a more
seamless experience the next major version of NineOldAndroids, version 2.0,
provides a set of base classes for `View`, `ViewGroup`, and the major layout
classes.

Each one of these base classes provides the methods for the properties which
were introduced in Honeycomb so that you can animate any view extending from
them as if they existed natively. The classes also handle calling the superclass
implementations if they are present. *Note: this means that they can only be
used on Android 2.1+*

In a followup release to NineOldAndroids 2.0 I hope to provide a means of
automatically creating and using this animation class as a proxy without
necessitating the use of these base activities (and thus allow it to work on
all Android platforms).


 [1]: http://android-developers.blogspot.com/2011/02/animation-in-honeycomb.html
 [2]: http://nineoldandroids.com
 [3]: http://stackoverflow.com/q/8734001/132047
