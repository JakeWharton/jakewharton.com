---
title: 'Exceptions and proxies and coroutines, oh my!'
layout: post

categories: post
tags:
- Kotlin
- Java
---

Checked exceptions are a concept that exist only in the Java compiler and are enforced only in source code. In Java bytecode and at runtime in the virtual machine you're free to throw checked exceptions from anywhere regardless of whether they're declared. At least, anywhere _except_ from a instance created by a Java `Proxy`.

A `Proxy` creates instances of interfaces at runtime where a single callback intercepts every method call. Libraries like [Retrofit](https://square.github.io/retrofit/) use proxies to create HTTP calls based on the annotations of interface methods. These methods tend to return promise-like objects such as RxJava's `Single`, Guava's `ListenableFuture`, or its own `Call` type.

```java
// MyService.java
interface MyService {
  @GET("/user/{id}")
  Call<User> user(@Path("id") long id);
}
```

Retrofit recently added support for Kotlin coroutines' `suspend` functions which behave a bit differently. Aside from the `suspend` modifier, the method signature otherwise _appears_ synchronous.

```kotlin
// MyService.kt
interface MyService {
  @GET("/user/{id}")
  suspend fun user(@Path("id") id: Long): User
}
```

Kotlin does not require declaring checked exceptions. With Retrofit using a `Proxy` and performing a network call that may throw an `IOException`, you might expect to be required to declare `@Throws(IOException::class)` though. This isn't actually required because the method signature gets rewritten by the Kotlin compiler to accept a `Continuation` parameter where both exceptions and results are forwarded.

```java
// Approximate Java for the compiled bytecode of MyService.kt:
interface MyService {
  void user(@Path("id") long id, Continuation<? super User> continuation);
}
```

Despite rewriting the bytecode to be callback-based and Retrofit asynchronously invoking the `Continuation`, rare calls to this method were resulting in an `UndeclaredThrowableException`. This indicates a checked exception was somehow being _synchronously_ thrown.

To understand why this was occurring and to craft a fix, we need to learn more about how coroutines work…


### Coroutine Implementation Crash Course

The above approximation of the Kotlin `MyService` bytecode is inaccurate. While the `Continuation` parameter is the _primary_ mechanism of delivering a success or error result, it's not the only mechanism.

```java
// Exact Java equivalent of MyService.kt bytecode
interface MyService {
  Object user(@Path("id") long id, Continuation<? super User> continuation);
}
```

`Object` is used as the return type because a `User` instance can be directly returned if available synchronously. Otherwise, the method returns the ["coroutine suspended" marker object](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.coroutines.intrinsics/-c-o-r-o-u-t-i-n-e_-s-u-s-p-e-n-d-e-d.html) to indicate suspension (where the result will be delivered to the `Continuation`).

This is one way that a checked exception could occur synchronously outside of Retrofit. When the method fails synchronously, the exception is allowed to propagate.

For asynchronous results, the Kotlin standard library provides the `suspendCoroutine` API.

```kotlin
suspend fun user(id: Long): User {
  return suspendCoroutine { continuation ->
    executor.execute {
      continuation.resume(User("jw"))
      // or continuation.resumeWithException(IOException("broken"))
    }
  }
}
```

This approximates to the following Java source:
```java
public Object user(long id, Continuation<? super User> continuation) {
  // code inside lambda that calls into 'continuation'
  return COROUTINE_SUSPENDED;
}
```

The marker object is returned up the stack which frees the thread to run other code. Once the continuation is invoked, our code will resume as soon as any thread is free again.


### Retrofit Coroutine Implementation

Retrofit uses the `suspendCoroutine` API with its own `Callback` to suspend while the HTTP request is sent on a background thread.

```kotlin
suspend fun <T : Any> Call<T>.awaitResponse(): Response<T> {
  return suspendCoroutine { continuation ->
    enqueue(object : Callback<T> {
      override fun onResponse(call: Call<T>, response: Response<T>) {
        continuation.resume(response)
      }

      override fun onFailure(call: Call<T>, t: Throwable) {
        continuation.resumeWithException(t)
      }
    })
  }
}
```

The implementation of `Call.enqueue` is very similar to the sample above which calls `executor.execute { .. }`. A thread pool picks up the `Call`, runs the request, and invokes the `Callback` when a reply is received.

It seems that Retrofit is not doing any work synchronously that would cause a checked exception. The stacktrace of the `UndeclaredThrowableException` even confirms that the work ran on the background `Executor`:

```
java.lang.reflect.UndeclaredThrowableException
    at ...
Caused by: java.net.UnknownHostException
    at ...
    at retrofit2.AsyncCall.execute(AsyncCall.java:172)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
    at java.lang.Thread.run(Thread.java:748)
```

Despite doing everything seemingly right, there's still clearly a bug or we never would see the `UndeclaredThrowableException`.


### The Bug

There's one behavior of `suspendCoroutine` that was not mentioned above which is designed to protect the execution stack. If the lambda passed to `suspendCoroutine` invokes the `Continuation` parameter _synchronously_ then instead of calling the real `Continuation`, the value is intercepted and propagated synchronously.

Going back to the sample, removing the call to `executor.execute` would create this behavior.
```kotlin
suspend fun user(id: Long): User {
  return suspendCoroutine { continuation ->
    continuation.resume(User("jw"))
  }
}
```

Without stack protection, invoking the continuation like this could cause the caller's code to resume _beneath_ the current stack frame. This would lead to extremely deep call stacks which would eventually trigger a `StackOverflowError`.

`suspendCoroutine` performs interception by wrapping the `Continuation`. Here is the approximated Java equivalent:
```java
public Object user(long id, Continuation<? super User> real) {
  ContinuationImpl<? super User> continuation = new ContinuationImpl(real);
  // code inside lambda that calls into 'continuation'
  return continuation.getResult();
}
```

The `getResult()` call will do one of three things:

 1. If `resume` was already called on `continuation`, return the value that was supplied.
 2. If `resumeWithException`was already called on `continuation`, throw the exception that was supplied.
 3. Otherwise, return `COROUTINE_SUSPENDED`. Future calls to `resume` and `resumeWithException` will forward to the `real` continuation.

The behavior of case #2 provides a probable source of a checked exception being thrown synchronously which in turn would cause the `UndeclaredThrowableException`.

But this only explains the bug if the callback is invoked _before_ the calling method is able to return. Since `enqueue` dispatches work to an `Executor` and immediately returns, the likelihood of this happening is zero. That is, at least, until you consider [preemption](https://en.wikipedia.org/wiki/Preemption_(computing)).

There are two threads here: the caller and the background worker. If we ignore the case where these execute on different CPU cores, a single core may preempt the caller thread to let the background worker make progress.

<a href="/static/post-image/exception-proxy-coroutine-1@2x.png">
  <img
    src="/static/post-image/exception-proxy-coroutine-1.png"
    srcset="/static/post-image/exception-proxy-coroutine-1.png 1x,
            /static/post-image/exception-proxy-coroutine-1@2x.png 2x"
    alt="Diagram showing the caller thread being preempted between the call to enqueue and returning and the worker thread invoking the continuation"
    />
</a>

Occasionally that preemption will occur precisely between the `ContinuationImpl` creation (green) and the call to `getResult()` (red). If the background work is quick enough the continuation may be invoked (orange) before switching back. In this example, an exception is quickly thrown due to a failed DNS lookup that was cached.


### The Fix

Detecting this case in Retrofit is simple. When the Java-based implementation delegates to the `suspend fun` it captures checked exceptions with a `try`/`catch` block.
```java
try {
  return KotlinExtensions.awaitResponse(call, continuation);
} catch (Exception e) {
  // but now what?
}
```

Invoking the `continuation` in the `catch` block is possible, but would defeat the stack protection of `suspendCoroutine` that caused this behavior in the first place. The current method call needs to be suspended before the exception is delivered. In Kotlin, this can be achieved with `yield()`.
```kotlin
suspend fun Exception.yieldAndThrow(): Nothing {
  yield()
  throw this
}
```

From Java this function which will always return `COROUTINE_SUSPENDED` because of `yield()`. The `continuation` will then receive the exception at the next available time on the current coroutine dispatcher.
```java
try {
  return KotlinExtensions.awaitResponse(call, continuation);
} catch (Exception e) {
  return KotlinExtensions.yieldAndThrow(e, continuation);
}
```

It's not clear why a `Proxy` requires checked exceptions to be declared when normal methods do not. Libraries providing `suspend fun` support through a `Proxy` will need to be mindful of this behavior and put similar workarounds in place.

This bug fix is available in Retrofit 2.6.1 today!
