---
title: Dynamic Images with Thumbor
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/dynamic-images-with-thumbor

categories: post
tags:
- Thumbor
- Android
---

Square uses a lot of user-generated images which are displayed on many mediums. An image for a single item can appear in [the Dashboard](https://squareup.com/dashboard), on [a Market page](https://squareup.com/market), in [the Register client](https://squareup.com/register) (on an iPad, iPhone, or Android), in an email receipt, or [in Wallet](https://squareup.com/wallet) (on iPhone or Android). In order to do this in an efficient, consistent, and cross-platform way we leverage an application named Thumbor.

[Thumbor](http://github.com/globocom/thumbor/) is an open-source, on-demand image service which allows for server-side cropping, resizing, and compositing of images. This is particularly useful for applications where the density and resolution of the target screen can vary wildly. Rather than downloading a large image and scaling it to fit the display, the image is requested at the exact desired size and the server does the work on-demand to resize it before delivering.

Downloading images at exact sizes affords many benefits:

* _Reduced bandwidth_ — Smaller images download and display faster. As mobile usage continues to increase, wireless networks remain much slower than their desktop counterparts.
* _Reduced memory consumption_ — Since the image is the exact size that is required, apps and browsers do not have to scale it in memory. If the image is also cached in memory, this increases the quantity that you are able to store.
* _Reduced disk usage_ — Caching images to disk avoids unnecessary re-downloading. As with the memory cache, this allows for less space to be occupied for each image which allows for faster loads when there is a ‘miss’ in the memory cache.

Thumbor runs as a web application which uses specially constructed URLs to define how it behaves. The simple case of resizing [the Google logo](http://www.google.com/images/srpr/logo3w.png) at 100px\*100px we would use this URL:

```
/unsafe/100x100/http://www.google.com/images/srpr/logo3w.png
```

Two security options that prevent others from abusing your service are opt-in: domain whitelisting and authentication. Whitelisting domains ensures Thumbor will only transform images which originate from your servers. Authentication uses a private key to add a checksum to the URL which is verified before performing the requested transformations. These two options restrict use to only allowed images and ensure that only the clients of your choosing can use the service. The secure version of the above URL becomes:

```
/X_5ze5WdyTObULp4Toj6mHX-R1U=/100x100/http://www.google.com/images/srpr/logo3w.png
```

Thumbor has [many options](https://github.com/globocom/thumbor/wiki/Usage) for transforming and compositing images. Combining multiple resize operations and arranging them into a single image further compounds all of the aforementioned positive effects. With all of these configuration options the URL can become very complex to construct. Square has developed two libraries to simplify this task.

[Pollexor](http://square.github.com/pollexor/) is a Thumbor URL builder for Java suitable for use both in server-side applications and Android clients.

Constructing the URL for the previous example using the Google logo becomes more declarative:

```java
String u = image("http://www.google.com/images/srpr/logo3w.png")
  .resize(100, 100)
  .toUrl();
```

Complex configurations can also be constructed in a clear, descriptive manner:

```java
String u = image("http://example.com/background.png")
  .resize(200, 100)
  .filter(
      roundCorner(10),
      watermark("http://example.com/overlay1.png")
  )
  .toUrl()
```

Our forthcoming ThumborURL library allows for easy URL creation for iOS-based clients.

```objc
NSURL *imageURL = [NSURL URLWithString:@"http://www.google.com/images/srpr/logo3w.png"];

TUOptions *opts = [[TUOptions alloc] init];
opts.targetSize = CGSizeMake(100, 100);

NSURL *u = [NSURL TU_secureURLWithOptions:opts imageURL:imageURL baseURL:baseURL];
```

Using Thumbor has alleviated the burden of dealing with images of all different sizes in our clients while also allowing them to operate much more efficiently.