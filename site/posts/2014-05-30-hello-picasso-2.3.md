---
title: Hello Picasso 2.3
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/hello-picasso-2-3

categories: post
tags:
- Android
- Picasso
---

_Written with [Dimitris Koutsogiorgas](https://twitter.com/dnkoutso)_

Today we are publishing a new version of [Picasso](http://square.github.io/picasso/), our solution for image downloading and caching on Android. One of the highlights of this release is what we refer to as “request replaying.” Before we showcase it, let’s dig into how Picasso is architected and why that helps us provide cool new features.


### High Maintenance

Anyone involved with Android development — and for that matter, any app developer — knows not to mess with the main thread. In the first version of Picasso (which was accomplished during a hackweek at Square!) things were simple. The main thread would make a new request for an image and would also manage canceling requests. But as we went on and imagined what features we really wanted to bring into Picasso, we quickly saw that this simple pipeline wouldn’t allow us to achieve our goals. We wanted a a foundation to build upon, while ensuring the main thread does as little as possible.

We literally went back to the drawing board, and with Picasso 2.0 we introduced a new layer in the pipeline: The Dispatcher. The Dispatcher is a dedicated thread responsible for coordinating all incoming requests from the main thread. There was a shift of responsibilities from the main thread to the dispatcher. A lot of the work was offloaded and the two things the main thread now cares for are “I want this image” or “I dont want this image.” High maintenance indeed.

The dispatcher now has all the burden of fetching images, often by delegating to one of its “hunters” (because “workers” is so 2000late). The intention here was for the dispatcher to grow into something bigger, rather than being just a pass-through stage in the pipeline. For example, one of the neat tricks of dispatcher is the ability to batch completed requests and send them off to the main thread — effectively reducing the amount of messages the handler queue has to process.


### Request Replaying

Apps that heavily depend on images to display useful information to the user, such as a static maps image showing a location, would often require the user to go back and reload the page when their network was available to load the image.

This user experience is subpar. To improve this, the dispatcher now has the ability to keep track of failed requests. When the network is back up, the dispatcher will automatically replay requests that previously failed.

You don’t need to do anything to enable this feature; it’s the responsibility of the dispatcher to get the image for you, while keeping the main thread work at a bare minimum.


### New APIs

Today we are also slightly expanding our fluent API to support widgets and notifications (RemoteViews). As you’d expect, invoking Picasso is very simple:

```java
// For widgets
Picasso.with(context).load(url) //
  .into(remoteViews, R.id.image, appWidgetIds);

// For notifications
Picasso.with(context).load(url) //
  .into(remoteViews, R.id.image, notificationId, notification);
```

Request replaying is supported everywhere, including notification and widget requests.


### Logging

We’re also bringing logging into Picasso. Going through each stage of the pipeline can be rather difficult to follow, especially when dealing with adapters that create/cancel requests as fast as the user can scroll.

We came up with a simple yet effective scheme to log every stage, as well as an easy way to track a single request. Additional metrics are also presented so you can further optimize — such as the time needed to transform a bitmap. Finally, each request has an ID associated with it. You can easily track a request just by grepping the output on the ID of the request.

The scheme is as follows:

```
THREAD | VERB | ID+ milliseconds since request creation | EXTRAS
```

To enable logging use:

```java
Picasso.with(context).setLoggingEnabled(true);
```

And the output:

```
Picasso  D  Main        created      [R1] Request{http://i.imgur.com/0E2tgV7.jpg resize(540,540)}
         D  Dispatcher  enqueued     [R1]+0ms
         D  Main        created      [R2] Request{http://i.imgur.com/jbemFzr.jpg resize(540,540)}
         D  Dispatcher  enqueued     [R2]+2ms
         D  Hunter      executing    [R2]+2ms
         D  Hunter      executing    [R1]+3ms
         D  Hunter      decoded      [R1]+25ms
         D  Hunter      decoded      [R2]+22ms
         D  Hunter      transformed  [R1]+36ms
         D  Dispatcher  batched      [R1]+51ms for completion
         D  Hunter      transformed  [R2]+55ms
         D  Dispatcher  batched      [R2]+55ms for completion
         D  Dispatcher  delivered    [R1]+251ms, [R2]+250ms
         D  Main        completed    [R1]+251ms from DISK
         D  Main        completed    [R2]+251ms from DISK
         D  Main        created      [R3] Request{http://i.imgur.com/xZLIYFV.jpg resize(540,540)}
         D  Main        completed    [R3] from MEMORY
```

Do NOT enable logging automatically (even for development builds). Displaying such detailed information will slow down the overall performance your app — something you wouldn’t even want your alpha or beta users to go through.


### What's Next

A lot of cool tricks and features are waiting to be added into Picasso. We hear you loud and clear. We tailor our roadmap towards the needs of the community.

At the moment we are considering a cache control system for each request (for controlling fetching and expiration) as well as a new mechanism for handling large UIs with a ton of image views.

We’d like to thank everyone who is supporting Picasso including Pinterest, Spotify, Pandora, Lyft, Uber, New York Times, Locish, Pinnatta and many others.

Fin.