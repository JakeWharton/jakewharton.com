---
title: 'The state of managing state (with Compose)'
layout: post

external: true
blog: Cash App Code Blog
blog_link: https://code.cash.app/the-state-of-managing-state-with-compose

categories: post
tags:
- Android
---

Five years ago the Cash App Android client started splitting our UI rendering and UI presenter responsibilities into distinct types.
We had leaned into RxJava heavily in the years prior, and it became very effective in facilitating this split.
I ended up giving a fun talk, "[The State of Managing State with RxJava](https://jakewharton.com/the-state-of-managing-state-with-rxjava/)", where I refactored then-common (anti?)patterns in RxJava to an architecture similar to where we were moving.

For all the gain in clean layering and improved testability, I was very dissatisfied with how opaque the code to actually manage the state objects was becoming.
The business logic was becoming lost in a sea of RxJava operators combinations and nesting.
We had tried a few redux-like libraries to attempt at mitigating this, and had even built our own which never saw the light of day[^1], but none were satisfying as a solution.

[^1]: It was called _Ducks_ and it had [the best logo](/assets/2021-11/ducks.png) (a mashup of the Rx logo and the Anaheim Ducks hockey logo).

Shortly thereafter I briefly left Cash App for Google, but since all good library work is built in the context of a real app I built [SDK Search](https://github.com/JakeWharton/SdkSearch) as my playground with a similar architecture.
As kotlinx.coroutines was being built I migrated from RxJava to its `Channel` to unlock multiplatform support.
When `Flow` was released I never landed its migration.
The change itself was easy, but the problem was never the stream type but in how the logic which produces the state is defined.
I had a picture of what I wanted but was unable to express it without drowning in library API.

Back on Cash App I was still dissatisfied with `Flow` and coroutines in any shape as a good solution.
I had been playing with Compose[^2] to build [fancy terminal UI](https://github.com/JakeWharton/mosaic/) and [multiplatform UI binding](https://jakewharton.com/multiplatform-compose-and-gradle-module-metadata-abuse/) while also thinking about the role of architecture in any Compose-based project.
Then earlier this year [Matt Precious](https://twitter.com/mattprec) was building a Compose Web project and we were iterating on what a classic presenter/render split in Compose looked like.
We created something good, but by virtue of depending on Compose it could only be used for Compose UI or Compose Web.

Or could it?

[^2]: Obligatory: [I mean Compose and **NOT** Compose UI](https://jakewharton.com/a-jetpack-compose-by-any-other-name/)!

### Enter Molecule

The [Molecule](https://github.com/cashapp/molecule/) library is the idea that you can use Compose solely as a mechanism of producing state values and not as something which does rendering.

First and foremost, what does it look like?
```kotlin
@Composable
fun Counter(start: Int, stop: Int): Int {
  val value by remember { mutableStateOf(start) }
  
  LaunchedEffect(Unit) {
    while (value <= stop) {
      delay(1_000)
      value++
    }
  }
  
  return value
}
```

This is a normal, state-returning composable function which could be used to bind to the text property of some Compose UI element.

Molecule lets you take this shape of composable and expose it as a `StateFlow<Int>` which can be consumed anywhere.
Compose synchronously recomposes when first initialized to produce the initial value, and then all subsequent values will emit in reaction to state change.

```kotlin
val count: StateFlow<Int> = scope.launchMolecule {
  Counter(1, 10)
}
```

When it comes to presenters, we have a composable function pattern we can use with Molecule:
```kotlin
@Composable
fun SomePresenter(events: Flow<EventType>): ModelType {
  // ...
}

val models: StateFlow<ModelType> = scope.launchMolecule {
  SomePresenter(events)
}
```

The reason this is exciting, and the reason for the history lesson above, is that Compose _does_ enable a new way of writing our logic. The use of a compiler plugin unlocks the language in a way that otherwise could not be achieved with raw coroutine library APIs. If you aren't familiar with Compose (but still got this far in the post), the ways in which it changes how you write code are too extensive to detail here and [the official documentation](https://developer.android.com/jetpack/compose) is a good place to start.

Now instead of chaining RxJava or Flow operators I can write plain `if`/`else` statements and `for` loops.
Instead of using `publish`/`filter`/`merge` combinations for type hierarchies I can now write a plain `when` and gain the language's exhaustiveness checking.

All of Compose's tools such as `remember`, state, derived state, effects, and more are available to use. Molecule's sample application starts to show a bit more complexity and use of these helpers.

```kotlin
@Composable
fun CounterPresenter(
  events: Flow<CounterEvent>,
  randomService: RandomService,
): CounterModel {
  var count by remember { mutableStateOf(0) }
  var loading by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    events.collect { event ->
      when (event) {
        is Change -> {
          count += event.delta
        }
        Randomize -> {
          loading = true
          launch {
            count = randomService.get(-20, 20)
            loading = false
          }
        }
      }
    }
  }

  return CounterModel(count, loading)
}
```

And finally, in Cash App our usage is class-based which allows us to normalize the presenter API and still participate in compile-time safe dependency injection.

```kotlin
class CounterPresenter @Inject constructor(
  private val randomService: RandomService,
) : MoleculePresenter {
  @Composable
  override fun Present(events: Flow<CounterEvent>) : CounterModel {
    // ...
  }
}
```

---

We've been playing with Molecule on the side for about five months now[^3].
It's not ready for a 1.0 because there are some tradeoffs in how we're using Compose and in the shape of our APIs that we're not 100% sure are the right ones to make.
As of this week the library is public and has been integrated into Cash App for more real-world testing.
We invite you to experiment with the library alongside us.

[^3]: Special thanks to our friends at Pinterest and Reddit who took early looks and offered feedback!

Is this the final form in the evolution of how we manage state?
Unlikely.
But it just may be our next form, and perhaps it could be yours, too!
