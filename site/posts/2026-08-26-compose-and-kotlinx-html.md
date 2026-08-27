---
title: "Compose & kotlinx.html"

tags:
- Kotlin
- Web
---

Let's render a simple page with [Ktor server](https://ktor.io) and [kotlinx.html](https://github.com/kotlin/kotlinx.html):
```kotlin
get("/users.html") {
  call.respondHtml {
    myLayout(title = "Users") {
      userList(
        users = db.users.value,
      )
    }
  }
}
```

A reusable `myLayout` provides scaffolding, `userList` encapsulates the specific page content, and `db.users` is a `StateFlow<List<User>>` from the persistence layer.

I've been doing this over and over to create admin dashboard pages for a project. It works great right up until you leave it open for a minute or two, and its content becomes stale.

Client-side frameworks exist within the ecosystem to "solve" this, such as Compose for HTML or Compose UI for Web. If your house has a leaky pipe you can also "solve" that by moving to a new house. I simply will not bring myself to abandoning HTML let alone delivery of static HTML in the response.

Efforts are underway [to adapt Compose for HTML](https://blog.jetbrains.com/kotlin/2026/08/exploring-compose-html-for-server-side-rendering/) for so-called isomorphic rendering. This would involve performing the initial composition on the server to produce the static HTML for the HTTP response. Then, client side as JS, mounting the same rendering code to reproduce the DOM tree and incrementally update it for future state changes.

### Tomorrow's Compose today

Instead of waiting, I brought my own Compose on the JVM from home in the form of [Molecule](https://github.com/cashapp/molecule/). Instead of managing a UI tree over time, Molecule manages a single piece of state over time. We can use that to render an HTML fragment on the server as a string, and then stream that to the client.

```kotlin
webSocket("/users.ws") {
  launchMolecule(Immediate) {
    val users by db.users.collectAsState()

    createHTML().userList(
      users = users,
    )
  }.collect(::send)
}
```

That's it. Ktor gives us the `webSocket`, Molecule runs the `StateFlow<String>` which is piped into it, and kotlinx.html renders the HTML fragment of our existing content function.

In the initial HTML payload you need to wire this up somehow. Something like:
```kotlin
script {
  unsafe {
    +"""
    |const content = document.getElementById("content");
    |const socket = new WebSocket("ws://" + location.host + "/users.ws");
    |socket.addEventListener("message", (event) => {
    |  // TODO Something!
    |});
    """.trimMargin()
  }
}
```

In the event listener you can send `event.data` directly into `content.innerHTML`. Browsers are _really_ good at parsing HTML.

My event listeners currently call `Idiomorph.morph(content, event.data)` which uses [Idiomorph](https://github.com/bigskysoftware/idiomorph) to patch the DOM. This ensures any form fields or interactive elements which have not changed are retained rather than replaced.

### Productionizing

Once again I want to emphasize that I'm using this for low-traffic admin pages. My pages can make one web socket connection or fifty and it wouldn't make a difference. On a real site with real users you need to evaluate some tradeoffs, or this might inadvertently denial-of-service your backend.

First, we probably only want a single long-lived connection to the server and to multiplex updates to different elements. Payloads need to be framed in an envelope that includes which HTML ID they target. The JS would then dispatch each payload to the appropriate section of the page for update.

Next, our payloads are entirely unidirectional, so HTTP server-sent events (SSE) is likely a better fit than a web socket. SSE ensures the connection can be multiplexed with others over a single HTTP/2 TCP connection, and it also provides the necessary framing for the previous problem by separating the event name from its payload.

Finally, you need to decide whether the composition runs inside the context of a single connection or is shared outside of it. This mostly depends on the type of data you are rendering and whether it's tied to a particular connection (such as for an authenticated user) or not.

Depending on what you pick with that last choice, writing a helper which encapsulates the static route + dynamic route can reduce duplication. Or don't. It's not that much code!
