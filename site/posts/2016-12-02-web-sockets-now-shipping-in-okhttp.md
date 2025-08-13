---
title: Web Sockets now shipping in OkHttp 3.5!
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/web-sockets-now-shipping-in-okhttp-3-5

categories: post
tags:
- Java
- OkHttp
- Web Sockets
---

Unlike the traditional request/response model of HTTP, web sockets provide fully bi-directional streaming of messages. This means that both the client and the server can send any number of messages to the other peer at any time. With today’s version 3.5 release, OkHttp now offers native support for web sockets!

Connect a web socket by passing a request to the `newWebSocket()` method along with a listener for server-sent messages.

```java
OkHttpClient client = new OkHttpClient();

Request request = //...
WebSocketListener listener = //...

WebSocket ws = client.newWebSocket(request, listener);
```

Enqueue text or binary messages by calling `send(String)` or `send(ByteString)`, respectively. Because OkHttp uses its own thread for sending messages, you can call `send` from any thread (even Android’s main thread).

Messages received from the server will be delivered to the listener’s `onMessage` callback as a `String` or `ByteString`. The listener also has callbacks which notify you of the connection lifecycle.

We’re excited to finally be able to share a stable web socket API in OkHttp. Use version 3.5 in your app by adding the following to your `build.gradle`:

```groovy
compile 'com.squareup.okhttp3:okhttp:3.5.0'
```

The full change log for this version is available [here](https://github.com/square/okhttp/blob/master/CHANGELOG.md#version-350).

---

Astute users of OkHttp might be a bit confused reading this post because web sockets have been _slightly_ supported for some time. A separate artifact called `okhttp-ws` has been available for nearly two years, but its API wasn’t considered stable. And even before that, a few classes implementing the web socket protocol were present in the ‘internal’ package.

The history of web sockets and OkHttp goes back three years to an unreleased Android version of [PonyDebugger](https://github.com/square/PonyDebugger), an implementation of the Chrome DevTools protocol to inspect and alter debug-versions of running apps on your device. Sadly someone else beat us to open sourcing this tool for Android so ours likely will never see the light of day.

All that internal work along with the support of a few companies using the early implementations has finally led to a shipping version. Thanks to everyone who contributed or filed bugs over the years.
