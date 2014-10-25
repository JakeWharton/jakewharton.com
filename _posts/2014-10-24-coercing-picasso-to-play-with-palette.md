---
title: Coercing Picasso To Play With Palette
layout: post

categories: post
tags:
- Android

lead: Palette is a fantastic addition to the support library suite. Unfortunately Picasso doesn't have any obvious places to wire it in. Thankfully all it takes is a bit of creativity with its existing APIs.
---

Features feel how I imagine children would. They are relatively easy to spawn but require a lengthy committment and constant care. Picasso's API would be nothing short of a Greek tragedy if we humored every feature request that we received. Finding the right balance of what is appropriate to add and what isn't is a constant struggle.

When [the Palette library](https://developer.android.com/tools/support-library/features.html#v7-palette) was teased the inevitable feature request came in to Picasso for a means of supporting it. This was certainly not an unreasonable request, and while we might not explicitly support it directly we will probably add something in the future to more easily facilitate its use.

But what do we do in the interim? The API for proper support is many months if not a year away from actually being implemented. Let's walk through an attempt to adapt the existing APIs to allow Palette's use.

----

The fundamental component of Picasso's data pipeline after the request has been fulfilled is a `Bitmap`. We use this in the return values and method parameters which traverse upwards to the main thread. `Bitmap` is a `final` class in Android so we will not get an opportunity to hang extra metadata on a subclass.

There are two ways that Picasso can notify the caller of a successful image download: the `Callback` when loading directly into an `ImageView` or the more generalized `Target`. A `Target` is given direct access to the `Bitmap` object but the `Callback` is not (although you can get it indirectly with some cleverness). Regardless of `Bitmap` access is the problem that both of these are called on the main thread. Since Palette does a decent amount of computation we don't want to do it here anyways.

Palette actually has an asynchronous mode of operation that we could leverage in these two callback locations but you wouldn't want to. The images are ready to be displayed when the callbacks are invoked so either delaying display until you can run Palette on another background thread or displaying the image right away and getting the Palette information later both seem like sub-par experiences.

From the time Picasso gets the `Bitmap` to the time it makes it back to the main thread, where can we hook in to allow the invocation of Palette inside of the threading model which is already being managed by Picasso? Those familiar with Picasso will know that there's two places: the `Downloader` (or `RequestHandler` in the upcoming v2.4) and in a `Transformer`.

`Downloader` and the upcoming `RequestHandler` are means to obtaining the original `Bitmap` instances (or `InputStream` instances) to fulfill a request. While we could invoke Palette here, I'm going to immediately reject it for a few reasons. There are multiple sources from which an image can be loaded which means we need to duplicate our logic across all of them. Additionally, the number of sources is constantly changing and some of them you cannot replace (I'm looking at you, drawable resource ID loading). Sometimes sources provide an `InputStream` which means we now have the burden of doing the initial `Bitmap` decoding ourselves--a job which is supposed to be Picasso's, right? Finally, the `Bitmap` at this level is the raw, original sized version. Not only will Palette operate much more slowly on it but a later transformation might alter the color makeup of the image.

A `Transformer`, it would seem, remains our only hope. And in fact, the more you look at it the more appealing it becomes. A `Transformer` always receives a `Bitmap` instance. It is invoked after all of the internal transformations have been applied. This means that the ever-common `fit()`/`resize()` & `centerCrop()` combo have already been executed. All transformers run last in the pipeline and the order is controlled by the caller. This means that we can place a custom transformer as the very last thing that is run before Picasso starts the process of sending the `Bitmap` back to the main thread.

I think we found our hook. Let's get started on some code:

```java
public final PaletteTransformation implements Transformation {
  @Override public Bitmap transform(Bitmap source) {
    // TODO Palette all the things!
    return source;
  }

  @Override public String key() {
    return ""; // Stable key for all requests. An unfortunate requirement.
  }
}
```

----

While `Transformer` gives us the very last minute hook into the processing pipeline, as we noted before, its return type of `Bitmap` means we aren't hanging any metadata directly on the return value. How can we propogate this metadata between the transformation back to the call site?

Looking at how we invoke Picasso with our transformation should give you a clue:

```java
Picasso.with(context)
    .load(url)
    .fit().centerCrop()
    .transform(new PaletteTransformation())
    .into(imageView, new EmptyCallback() {
      @Override public void onSuccess() {
        // TODO I can haz Palette?
      }
    });
```

Those familiar with Picasso best practices should be screaming about the `new PaletteTransformer()` snippet. In general, all Picasso transformations should be completely stateless functions so that a single instance can be used for every call to `.transform()`. In this case we are going to make an exception because the transformation looks like a great place to pass along our metadata.

```java
final PaletteTransformation paletteTransformation = new PaletteTransformation();
Picasso.with(context)
    .load(url)
    .fit().centerCrop()
    .transform(paletteTransformation)
    .into(imageView, new EmptyCallback() {
      @Override public void onSuccess() {
        Palette palette = paletteTransformation.getPalette();
        // TODO apply palette to text views, backgrounds, etc.
      }
    });
```

Now that we have a working model to hand off the metadata, let's update our `PaletteTransformation` to actually extract the palette from the `Bitmap` that is passing through.

```java
public final PaletteTransformation implements Transformation {
  private Palette palette;

  public Palette getPalette() {
    if (palette == null) {
      throw new IllegalStateException("Transformation was not run.");
    }
    return palette;
  }

  @Override public Bitmap transform(Bitmap source) {
    if (palette != null) {
      throw new IllegalStateException("Instances may only be used once.");
    }
    palette = Palette.generate(source);
    return source;
  }

  // ...
}
```

While this looks like a working solution, there are two problems:

 1. It requires an additional object allocation for every Picasso request (something we try very hard at to minimize).
 2. Images which are cached do not pass through the transformation pipeline and thus will always return `null` from `getPalette()`.

----

Object allocations are becoming more cheap with newer platform versions but will never be free. Since Picasso is often called thousands of times in very performance sensitive areas of applications we aim for the utmost efficiency in terms of CPU and memory use.

Saving the allocation for our transformation is easy. We can use the well-known pattern of object pooling. Thanks to recent support library updates, this is even easier to do than before with [the `Pools` helper](https://developer.android.com/reference/android/support/v4/util/Pools.html).

```java
public final PaletteTransformation implements Transformation {
  private static final Pool<PaletteTransformation> POOL = new SynchronizedPool<>(5);

  public static PaletteTransformation getInstance() {
    PaletteTransformation instance = POOL.obtain();
    return instance != null ? instance : new PaletteTransformation();
  }

  private Palette palette;

  private PaletteTransformation() {}

  public Palette extractPaletteAndRelease() {
    Palette palette = this.palette;
    if (palette == null) {
      throw new IllegalStateException("Transformation was not run.");
    }
    this.palette = null;
    POOL.release(this);
    return palette;
  }

  // ...
}
```

Our calling code only changes slightly to use the new static factory and the more semantically named palette extraction method.

```java
final PaletteTransformation paletteTransformation = PaletteTransformation.getInstance();
Picasso.with(context)
    .load(url)
    .fit().centerCrop()
    .transform(paletteTransformation)
    .into(imageView, new EmptyCallback() {
      @Override public void onSuccess() {
        Palette palette = paletteTransformation.extractPaletteAndRelease();
        // TODO apply palette to text views, backgrounds, etc.
      }
    });
```

I have hard coded the pool size to retain 5 instances. This is an educated guess based on how I know Picasso's internals to work. If you were adopting this implementation you should add logging to test whether the size needs to be increased for you application.

----

Dealing with memory-cached images is a bit less straightforward. Picasso's `Cache` is hard coded to use `Bitmap` as the value which means we can't wrap up the `Palette` instance along side. Since we can't use the main cache we will be forced to mirror it.

In deciding on our cache key we have a choice: the `Bitmap` which is the source of truth for the pixels or the URL which is the source of truth for the image. The choice here leads to two different implementations and neither one is applicable to all use cases. We'll quickly explore both which will yield the final result.

Keying by `Bitmap` creates the most elegant implementation of the transformation at the expense of ugliness in the calling code. We can even revert to using a single transformation instance with an embedded cache. Pooling is fun but not having to pool is even better!

```java
public final class PaletteTransformation implements Transformation {
  private static final PaletteTransformation INSTANCE = new PaletteTransformation();
  private static final Map<Bitmap, Palette> CACHE = new WeakHashMap<>();

  public static PaletteTransformation instance() {
    return INSTANCE;
  }

  public static Palette getPalette(Bitmap bitmap) {
    return CACHE.get(bitmap);
  }

  private PaletteTransformation() {}

  @Override public Bitmap transform(Bitmap source) {
    Palette palette = Palette.generate(source);
    CACHE.put(source, palette);
    return source;
  }

  // ...
}
```

The `WeakHashMap` will release the `Palette` reference when its associated `Bitmap` is garbage collected. We rely on Picasso's memory cache to retain the strong reference even if it isn't currently being displayed in an `ImageView`.

The calling code has to obtain the final `Bitmap` in order to query the cache. This is trivial in Picasso's `Target`, but much more ugly in the more common `Callback`.

```java
Picasso.with(context)
    .load(url)
    .fit().centerCrop()
    .transform(PaletteTransformation.instance())
    .into(imageView, new EmptyCallback() {
      @Override public void onSuccess() {
        Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap(); // Ew!
        Palette palette = PaletteTransformation.getPalette(bitmap);
        // TODO apply palette to text views, backgrounds, etc.
      }
    });
```

This approach places each `Bitmap` as the source of truth for the `Palette` instance. The same URL which is displayed at different resolutions might have slightly different values in each swatch because of this. Using the URL as the key on a cache would ensure that multiple sizes of the same image had exactly the same palette. I'm not exactly sure of how much of an issue this is in practice, if any.

There are other problems with a URL `String` key approach, however. Picasso's default `LruCache` implementation does not expose a callback for when entries are being purged. This means we have no way of reference counting the `Palette` instances in a parallel cache (remember, multiple entries in the main cache could reference the same `Palette`). We could create a new `Cache` implementation based on the support-v4 library `LruCache` (from which Picasso's is based) but now we are duplicating functionality for little gain.

Another way to solve the problem would be a double-key map where the URL `String` is mapped to the `Palette` instance, but also each resulting transformed `Bitmap` instance was used as a map key with a weak reference. When a reference queue callback was invoked because the `Bitmap` was garbage collected, we check to see whether any `Bitmap` mappings still exist and if not purge the `String` to `Palette` mapping. This is viable, but it's more work than I am willing to do because ultimately we are designing a temporary solution.

It looks like the `Bitmap` key based approach is the most viable at the expense of potential (but not proven) variance in the `Palette` instances for multiple `Bitmaps` of the same image URL.

----

This is a long post and there isn't exactly a nice neat bow to tie it all together with. I wrote it in this way because I wanted to showcase two very important things when it comes to how we do feature and API design:

 1. Not every use case deserves a first-party API but with some clever thinking you can usually accomplish what you are after by thinking outside of the box. We ultimately will support the use of Palette in Picasso but less as a first-class citizen and more through a general means of passing along arbitrary metadata through the pipeline.
 2. Exhausting multiple implementations and solutions to a problem is essential for ironing out the best approach. This was a journey to the final conclusion and it's one we frequently make for most of the changes we make to Picasso and all of our open source libraries. Not only can we be sure that we reached a good solution, but we also are able to defend and justify our decisions.

If you come upon any other bright ideas for applying Palette into Picasso I'd love to hear about them. Otherwise get playing with the fantastic Palette library today and keep an eye out for Picasso 2.4 in the next few days!
