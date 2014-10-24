---
title: Advanced Pre-Honeycomb Animation with NineOldAndroids
layout: post

categories: post
tags:
- Android
- NineOldAndroids

lead: Translation, scale, and other transformations were introduced in Honeycomb but can still be easily used on previous platforms.
source: http://nineoldandroids.com
---

The lovely new [animation framework][1] in Android 3.0 came with some additional
methods on the <code>View</code> class to allow for transformations such as
translation, scale, rotation, and alpha. The [NineOldAndroids][2] library allows
for the use of this API on any platform but is limited only to modifying values
for which methods exist on the running platform.

Recently I [set out][3] to solve this problem and allow for utilizing my library
to animate these properties regardless of the API level. Neither an answer to
the linked StackOverflow question nor a quick exchange with the animation guru
Chet Haase himself semed to produce a reliable, stable implementation for
this--the recommendation always being to just use the built-in view animation.

As I was digging around in the `View` class I noticed that there really was no
way to achieve this effect directly, even with reflection. It was only once I
started poking around how view animations are processed and executed did a
rather clever solution appear to me.

For those that are not familiar with how the view animation framework works,
an animation receives a callback with a `Transformation` object and a time
interval. Each animation then adjusts the object in order to reflect the state
of the associated view at whatever time interval it is at. Since the
`Transformation` object contains a method for setting alpha and a matrix which
is applied to the canvas rendering the view we can easily achieve all of the
transformations of the native methods introduced in Honeycomb.

```java
void applyTransformation(float interpolatedTime, Transformation t) {
    //Perform transformations
}
```

So now that we know these transformations were possible, how best to implement
them in a manner that can be used by the new animation API? To accomplish this
we use a few tricks of view animation in order to do this in a way that is as
lightweight and fast as possible (we're on the UI thread, remember).

Only one animation can be applied to a view at a time so it was obvious that a
custom class extending `Animation` was required to apply our many
transformations. Now it became a matter of synchronizing our new class with the
NineOldAndroids library since it would be the one actually controlling the
animation.

Instead of attempting to integrate NineOldAndroids directly in this custom class
I chose to make it act only as a proxy to the alpha and the `Transformation`
object by exposing methods to allow changing the various properties that were
introduced in Honeycomb.

In order to take the native stepping of view animation out of the equation, our
custom class immediately sets two properties on itself: `setDuration(0)` and
`setFillAfter(true)`. This effectively disables the timer internally triggering
the transformation and it allows the transformations that we make to be
persisted on the view after the animation has completed. In order for the latter
to occur the animation is kept around so that its transformation can be applied
whenever the view is invalidated. This is the behavior that we leverage in order
to provide our animation.

```java
AnimatorProxy(View view) {
    setDuration(0); //perform transformation immediately
    setFillAfter(true); //persist transformation beyond duration
    view.setAnimation(this);
    mView = view;
}
```

We expose our new properties as getter and setter methods that the new animation
API can interact with and hold them in instance variables in our animation. Each
invalidation then triggers our callback which we can then apply the newly
updated values for each property, thus, animating the view.

```java
void setAlpha(float alpha) {
    mAlpha = alpha;
    mView.invalidate();
}
```

This works extremely well and provides fluid, multi-property animation using
NineOldAndroids for the new animation API but it still requires us to use the
animation proxy class for these specific properties. In order to provide a more
seamless experience, we need a way to have this handled automatically.

In order to determine when this class is required we add a small check in the
initialization method of `ObjectAnimator`. If the animation meets the following
four conditions then a proxy instance is used: we are using a named property and
not a `Property`, we are running on pre-3.0 Android, the target class is an
instance of `View`, and the named property is one of the ones introduced in
Honeycomb.

```java
if ((mProperty == null) && AnimatorProxy.NEEDS_PROXY && (mTarget instanceof View)
        && PROXY_PROPERTIES.containsKey(mPropertyName)) {
    setProperty(PROXY_PROPERTIES.get(mPropertyName));
}
```

Here, `PROXY_PROPERTIES` is a `Map` which maps the required property names to
special `Property` classes that automatically use an instance of our proxy
animation class. By setting a `Property` instance on the animation we will
essentially override the string equivalent so that reflection on the method
is not attempted.

Now you can enjoy advanced Honeycomb-style animation of post-Honeycomb `View`
properties by simple changing your imports to use NineOldAndroids!

```java
AnimatorSet set = new AnimatorSet();
set.playTogether(
    ObjectAnimator.ofFloat(myView, "rotationX", 0, 360),
    ObjectAnimator.ofFloat(myView, "rotationY", 0, 180),
    ObjectAnimator.ofFloat(myView, "rotation", 0, -90),
    ObjectAnimator.ofFloat(myView, "translationX", 0, 90),
    ObjectAnimator.ofFloat(myView, "translationY", 0, 90),
    ObjectAnimator.ofFloat(myView, "scaleX", 1, 1.5f),
    ObjectAnimator.ofFloat(myView, "scaleY", 1, 0.5f),
    ObjectAnimator.ofFloat(myView, "alpha", 1, 0.25f, 1)
);
set.setDuration(5 * 1000).start();
```

Download NineOldAndroids 2.0.0 from [nineoldandroids.com][2] and check it out
on [GitHub][4].


 [1]: http://android-developers.blogspot.com/2011/02/animation-in-honeycomb.html
 [2]: http://nineoldandroids.com
 [3]: http://stackoverflow.com/q/8734001/132047
 [4]: http://github.com/JakeWharton/NineOldAndroids/
