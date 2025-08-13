---
title: "Simple Multiplatform RPC with Kotlin Serialization"
layout: post

categories: post
tags:
- Kotlin
---

I recently played a minor role in helping add Cast support to an Android app. Both the Android app and Cast display are written in Kotlin. The Android Cast SDK relays JSON strings to the JavaScript SDK which invokes your callback with the deserialized equivalent as a JS object. A multiplatform library holds the model objects so that they can be shared between Android and JS.

```kotlin
class Game(
  val players: Array<Player>
)
class Player(
  val name: String,
  val color: String,
  val scores: Array<Int>
)
```

[Moshi][moshi] serializes the models to JSON in the Android app.

 [moshi]: https://github.com/square/moshi/

```kotlin
val game = Game(arrayOf(
  Player("Jesse", "#ff0000", arrayOf(1, 2, 3)),
  Player("Matt", "#ff00ff", arrayOf(3, 0, 2))
))

val gameAdapter = moshi.adapter(Game::class.java)
val gameJson = gameAdapter.toJson(game)
// {"players":[{"name":"Jesse",...},{"name":"Matt",...}]}

castSdk.send(gameJson)
```

The Cast app receives the deserialized JS object and interprets it as being of the same type.

```kotlin
castSdk.addCustomMessageListener { message ->
  val game = message.data.unsafeCast<Game>()
  ui.render(game)
}
```

This works but imposes some severe limitations. The model objects can only use collections available natively to JS which means `Array`s instead of `List`s. Custom serialization is also not supported because the JSON to JS object conversion was happening outside the library.

It was clear this setup wasn't going to work long-term.


### Kotlin Serialization

[kotlinx.serialization][kx-serialization] is Kotlin's multiplatform, reflection-free, format-agnostic serialization library. Its compiler plugin generates code for types which are annotated as `@Serializable`.

 [kx-serialization]: https://github.com/Kotlin/kotlinx.serialization

```diff
+@Serializable
 class Game(
   val players: Array<Player>
 )
+@Serializable
 class Player(
   val name: String,
```

Updating the Android app requires specifying that we're using the JSON format and supplying a reference to the generated serializer.

```diff
-val gameAdapter = moshi.adapter(Game::class.java)
-val gameJson = gameAdapter.toJson(game)
+val gameJson = Json.stringify(Game.serializer(), game)
 // {"players":[{"name":"Jesse",...},{"name":"Matt",...}]}

 castSdk.send(gameJson)
```

Normally in this situation, changing the serialization library would only affect the Android app since the Cast SDK internally parses JSON to JS objects. However, kotlinx.serialization has the unique feature of being able to "parse" a JS object.

```diff
+val objectParser = DynamicObjectParser()
 castSdk.addCustomMessageListener { message ->
-  val game = message.data.unsafeCast<Game>()
+  val game = objectParser.parse(message.data, Game.serializer())
   ui.render(game)
 }
```

This walks the object properties as if it were JSON and passes them through the serializer. Now we can use all of the features of the library from custom serializers to simple things like using a `List`.

```diff
 @Serializable
 class Game(
-   val players: Array<Player>
+   val players: List<Player>
 )
 @Serializable
 class Player(
   val name: String,
   val color: String,
-  val scores: Array<Int>
+  val scores: List<Int>
 )
```

This future-proofed the app to ensure that its models could continue to be shared even as they grew in complexity. And they were about to.


### Simple RPCs

The Cast app started as a stateless rendering of the game model but it lacked some of the Android app's flair. Instead of sending only the bare model, the Android app was changed to send an event. This allowed showing animations on the Cast display after an action. Each event contained a copy of the game model as well as any other information about the event.

```kotlin
@Serializable
data class PlayerAdded(
  val game: Game,
  val player: Player
)

@Serializable
data class SpinTheBottle(
  val game: Game,
  val winner: Int
)
```

The type will determine the behavior of the Cast app in response to these events.

```kotlin
when (event) {
  is PlayerAdded -> { .. }
  is SpinTheBottle -> { .. }
}
```

Unfortunately this does not work as-is. When serialized, the root JSON object contains only the properties of the object and not which specific type was serialized.

```js
{"game":{ /*..*/ },"winner":1}
```

You can try to infer the type from which properties are present but it's a brittle setup.

This is generally solved by using something called "polymorphic serialization" which uses some kind of marker to encode which type was serialized. In kotlinx.serialization 0.14.0, the compiler automatically enables polymorphic serialization for Kotlin sealed hierarchies so it's an obvious choice.

```diff
+@Serializable
+sealed class GameEvent {
+  abstract val game: Game
+}

 @Serializable
 data class PlayerAdded(
-  val game: Game,
+  override val game: Game,
   val player: Player
-)
+) : GameEvent()

 @Serializable
 data class SpinTheBottle(
-  val game: Game,
+  override val game: Game,
   val winner: Int
-)
+) : GameEvent()
```

The JSON will now include a discriminator, a string identifying which type was used, so that the deserialization code picks the corresponding type on the other side. By default the library uses array-based discriminators (but you could elect to add a property to the object itself).

```js
["com.example.model.SpinTheBottle",{"game":{ /*..*/ },"winner":1}]
```

Additionally, by using a sealed class, Kotlin can now enforce that a `when` on the event types is exhaustive[^1].

 [^1]: Note: The snippet with this code is not set up to be exhaustive for simplicity.

kotlinx.serialization 0.20.0 added support for polymorphic serialization in `DynamicObjectParser` allowing the Cast app to take advantage of it.

```diff
 val objectParser = DynamicObjectParser()
 castSdk.addCustomMessageListener { message ->
-  val game = objectParser.parse(message.data, Game.serializer())
+  val event = objectParser.parse(message.data, GameEvent.serializer())
+  val game = event.game
   ui.render(game)
+  when (event) {
+    is PlayerAdded -> { .. }
+    is SpinTheBottle -> { .. }
+  }
 }
```

This setup creates a pretty robust unidirectional RPC system for the Android app to talk to the Cast display. The build will fail if you forget to handle a new event on the Cast side. The sending code and transport don't need updated for new events since it's all based on the `GameEvent` supertype.

---

With the Cast SDK imposing JSON and automatic deserialization to JS objects, the feature set of Kotlin serialization fits right in. It allows maximizing code reuse without imposing too much complexity. And, granted, it's just about the most basic RPC system you could build, but it serves the app well. Supporting requirements like associated responses and bidirectional streaming is better left to more heavyweight systems like gRPC.