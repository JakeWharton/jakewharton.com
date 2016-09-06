---
title: Forcing bytes downward in Okio
layout: post

categories: post
tags:
- Android
- Java

lead: "A comparison and guide to using Okio's three different methods to ensure bytes are written to the underlying sink: `emitCompleteSegments()`, `emit()`, and `flush()`."
---

Okio's `BufferedSink` is a high-level abstraction for writing binary and character data as bytes.
Its design stems from frustrations with the JDK's `java.io.*` and `java.nio.*` libraries. At
Droidcon Montreal last year I gave [a presentation](/a-few-ok-libraries) comparing
it with the former, but also showcased Okio's concept of a _segment_ and how it enables the library
to cheaply move bytes. If you aren't familiar with Okio I encourage you to go watch the presentation
first since the rest of this post will assume at least cursory knowledge of its types.

If you never look at the source code of Okio you won't know that this segment concept exists.
It's an implementation detail for performance that remains completely opaque to the consumer of the
library. That is, except for one<sup>\*</sup> notable exception: the `emitCompleteSegments()` method
on `BufferedSink`.

This method is part of a family of three methods which force buffered bytes to be moved to the
underlying `Sink`. Their difference is subtle, but understanding that difference ensures correctness
and can make or break throughput.

First let's understand the difference in behavior and then look at some use cases for each.

---

**`flush()`**

<img src="/static/post-image/okio-flush.gif" height="240" width="210" class="float-right"/>

Flush is a common concept in stream APIs and its semantics remain unchanged in Okio. Calls to this
method cause _all buffered bytes_ to be moved to the underlying `Sink` and then that `Sink`
is also instructed to flush itself. When calls to `flush()` return, you are guaranteed that all
bytes have been sent all the way to the destination `Sink`.

When multiple levels of buffering are in use, a call to `flush()` will clear the buffers at every
level. In Okio multiple levels of buffering are so cheap that it's practically free. A flush just
amounts to each level _moving_ its segments down to the next level of the chain.

With `java.io.*` streams, however, multiple levels of buffering require each level to allocate
and manage its own `byte[]`. This means that a flush operation will result in each level doing an
`arraycopy()` of its data down to the next level (which also might have required buffer expansion).

Calls to `close()` on stream types typically behave similar to `flush()` in that they write all
buffered bytes to the underlying stream before also instructing it to close.

**`emit()`**

<img src="/static/post-image/okio-emit.gif" height="240" width="210" class="float-right"/>

Emitting bytes is very similar to flushing except that it is not a recursive operation. Calls to
this method cause all buffered bytes to be moved to the underlying `Sink`. Unlike `flush()`,
however, that `Sink` is not told to do any other operations.

Because buffering is so inexpensive with Okio, it's not uncommon to accept a `Sink` for API flexibility and
immediately wrap it in a `BufferedSink` for the implementation's convenience. It's important to not
leave any buffered bytes unwritten to the original `Sink` which is what calls to `emit()` will ensure.

`emit()` is a nice alternative to `flush()` since it allows you to use the more useful
`BufferedSink` type without the concern of needlessly causing bytes to be sent all the way down the
chain every time you're finished with the abstraction.

**`emitCompleteSegments()`**


<img src="/static/post-image/okio-emit-complete-segments.gif" height="240" width="210" class="float-right"/>

If you understand the behavior of `emit()` and you understood the concept of segments then the
behavior of this method should be straightforward. Calls to this method cause only the bytes which
are part of _complete segments_ to be moved to the underlying `Sink`. If you haven't buffered
enough bytes to create a complete segment this method will actually do nothing!

Remember, segments are an implementation detail of Okio and as such so are their sizes. So why does
the public API expose their concept in this method?

The reason this method exists to ensure that your code is actually not buffering _too many bytes_.
When sending large amounts of data over a long period of time through a `BufferedSink`, it can be
beneficial to occasionally write parts of the data to the underlying `Sink`. This ensures that the
destination isn't overwhelmed by a single gigantic write. Instead, the `Sink` can incrementally
process bytes as they're available and optionally send back signals to the producer (either with an
exception or out-of-band notifications like HTTP/2's flow control).

---

Now that we know the difference in behavior of these three methods, let's look at some use cases
of when you would want to use each.

**Writing messages to a WebSocket**

WebSockets are long-lived connections to a server over which string or binary messages are
constantly streamed in both directions. The frequency of these messages can be extremely rapid or
quite sparse. An API for sending messages on a `WebSocket` class would look something like this:

    public void sendMessage(String content);
    public void sendMessage(byte[] content);

This `WebSocket` type would wrap a socket stream with a `BufferedSink` for sending messages. Because
we don't know when the next message will come from the application, the implementation of
`sendMessage` needs to call `flush()` before returning to optimize for latency. This ensures all of
the data from each message will be sent down through the socket.

If you were to use `emitCompleteSegments()` part of the message would almost always be left in the
buffer. Using `emit()` would only work if there's no intermediate buffering which is hard to
guarantee. This is why `flush()` is the only appropriate operation for this example.

**Encoding a video to a file**

Video encoding is a CPU-bound, memory-intensive process which generates data at a fairly consistent
rate. Writing this data to a file as its being encoded keeps the buffer size small and ensures that
the slower disk drive can keep up.

At regular intervals the implementation writing encoded data to a `BufferedSink` should call
`emitCompleteSegments()` to allow large portions of the buffer to get moved to the underlying
`Sink` and start trickling down the chain. The reason that `emitCompleteSegments()` is preferred
here over `emit()` is that more data will be coming into buffer. Sending a partially-completed
segment would be wasteful since it has empty bytes that can still be used for the incoming data.

It's important to note again that `emitCompleteSegments()` only writes to the underlying `Sink`
and not all the way down the chain (i.e., it's not `flushCompleteSegments()`). This means that if
there is an intermediate buffer which isn't monitoring its size and occasionally calling this method
you will end up buffering the whole video.

When the video is done encoding and no more bytes will be written a call to `emit()` (with the same
caveats as the last paragraph) or `flush()` should happen so that the final bytes are not left in
the buffer.

**Serializing an object to JSON**

If we wanted to create a library that took an object and serialized it to JSON we would probably
give it a method signature like this:

    public void toJson(Object o, Sink sink)

Because `Sink` offers no convenience on its own, the implementation would buffer it into an
`BufferedSink` for access to high-level APIs.

Once the implementation was finished writing the JSON representation to the `BufferedSink` it needs
to return. In order to not leave any bytes in the buffer it needs to call `emit()`. This writes all
of the buffered bytes to the underlying `Sink`, but not any further. Whether or not the `Sink`
should be flushed all the way down is left as a decision for the caller. This affords more control
so that if the caller wants to serialize multiple objects and flush them all at once they are able
to do so.

Astute readers might be aware of [Moshi](https://github.com/square/moshi)–a small JSON
serialization library built with Okio. Unlike our hypothetical `toJson` method above, Moshi's method
actually requires a `BufferedSink` directly. The reason for this is completely unrelated to flushing
or emitting data, but rather for symmetry with the `fromJson` method which requires a
`BufferedSource`.

---

So `flush()`, `emit()`, and `emitCompleteSegments()` each instruct a `BufferedSink` to move data to
the underlying `Sink` in slightly different ways. Understanding that difference ensures that your
bytes do not get lost inside of intermediate buffers but also that you move the minimal amount of
bytes for your needs.

&nbsp;

&nbsp;

&nbsp;

_<small><sup>*</sup> Technically there is two exceptions. `Buffer` has a `completeSegmentByteCount()` method which returns the number of bytes that would be moved by a call to `emitCompleteSegments()`.</small>_
