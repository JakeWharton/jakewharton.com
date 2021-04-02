---
title: Enhance Your Application Using Picasso
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/enhance-your-application-using-picasso

categories: post
tags:
- Android
- Picasso
---

_Written with [Dimitris Koutsogiorgas](https://twitter.com/dnkoutso)_

Today, we would like to introduce and open source Picasso, our solution for image downloading and caching on Android.

Picasso aims to be fast and simple to use — often requiring only one line of code.

```java
Picasso.with(context).load("http://example.com/logo.png").into(imageView);
```

And that’s it! You can use this for downloading a single image or inside of an adapter’s getView method to download many. Picasso automatically handles caching, recycling, and displaying the final bitmap into the target view.

Picasso also allows you to transform images.

```java
Picasso.with(context)
    .load("http://example.com/logo.png")
    .resize(100, 100)
    .centerCrop()
    .into(imageView);
```

The transformation will occur in the same background thread used to decode the original source bitmap and the final result will be stored into memory. This means you can store different transformations of the same source bitmap for future use.

![](/static/post-image/picasso-0.png)

Picasso automatically utilizes a memory and disk cache (provided by the HTTP client) to speed up bitmap loading. For development you can enable the display of a colored marker which indicates the image source.

![](/static/post-image/picasso-1.png)

For the latest release of Square Register and future releases of Square Wallet we really wanted to improve image downloading. Picasso is the result of lessons learned from our previous framework as well as various third-party libraries.

More information and downloads are available on the Picasso [website](http://square.github.io/picasso/).

_This post is part of Square’s “[Seven Days of Open Source](https://corner.squareup.com/2013/05/seven-days-of-open-source.html)” series._