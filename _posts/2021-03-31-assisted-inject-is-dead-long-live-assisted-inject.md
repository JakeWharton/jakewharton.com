---
title: "AssistedInject is dead, long live AssistedInject!"
layout: post

external: true
blog: Cash App Code Blog
blog_link: https://code.cash.app/assisted-inject-is-dead-long-live-assisted-inject

categories: post
tags:
- Android
---

After \~5 years of existing and \~4 years as an open source project, our AssistedInject library has been deleted. Mourn not, however, for the same functionality is [now available][assisted] directly in Dagger and InflationInject [got its own repo][inflation-injection].

[assisted]: https://dagger.dev/dev-guide/assisted-injection.html
[inflation-injection]: https://github.com/cashapp/InflationInject

_(Psst. Know Dagger but don't know what assisted or inflation injection is? That's okay. Check out [this introductory talk][talk]. Otherwise, this post won't make much sense!)_

[talk]: https://jakewharton.com/helping-dagger-help-you/

This post will cover the steps required for migrating from Square AssistedInject to Dagger AssistedInject, minor differences between the two, and the status of InflationInject.


## Migration and differences

Our migration to Dagger's AssistedInject was done in a few discrete steps after failing to migrate in a single change. It is possible to migrate in a single change, but multiple changes help ensure the differences are handled correctly.

These steps assume you are using AssistedInject 0.6.0 which was the latest stable version for the past six months.

### Step 1: Upgrade to 0.7

[Version 0.7.0][seven] contains changes from the last 6 months which will make future changes simpler.

[seven]: https://github.com/cashapp/InflationInject/blob/trunk/CHANGELOG.md#070---2021-03-21

Most notably, this version will require specifying qualifier annotations for assisted parameters of the same type.

```diff
 class Example @AssistedInject constructor(
   graphValue: GraphValue,
-  @Assisted assistedValue1: String,
+  @Assisted @Named("one") assistedValue1: String,
-  @Assisted assistedValue2: String,
+  @Assisted @Named("two") assistedValue2: String,
 ) {
   // ...
   
   @AssistedInject.Factory
   interface Factory {
     fun create(
-      assistedValue1: String,
+      @Named("one") assistedValue1: String,
-      assistedValue2: String,
+      @Named("two") assistedValue2: String,
     ): Example
   }
 }
```

While this seems like boilerplate, the previous behavior of relying on parameter names was not safe as they are not always available (Dagger will also require this).

This version also includes a bug fix that allows using Dagger's `@AssistedInject`-annotated types
inside a Square `@AssistedInject` or `@InflationInject`-annotated type. If you are migrating incrementally rather than all modules at once this fix is essential for interoperability between the two libraries.

### Step 2: Upgrade to 0.8

If you are using inflation injection, [version 0.8.1][eight-one] changes the annotation used in the constructor from `@Assisted` to `@Inflated`.

[eight-one]: https://github.com/cashapp/InflationInject/blob/trunk/CHANGELOG.md#081---2021-03-22

```diff
-import com.squareup.inject.assisted.Assisted
+import com.squareup.inject.inflation.Inflated
 import com.squareup.inject.inflation.InflationInject

 class ExampleView @InjectionInject constructor(
-  @Assisted context: Context,
+  @Inflated context: Context,
-  @Assisted attrs: AttributeSet,
+  @Inflated attrs: AttributeSet,
   picasso: Picasso,
 ) : View {
   // ...
 }
```

Since Dagger validates that all usages of its `@Assisted` are inside constructors annotated with its `@AssistedInject`, this change is required before migrating. Additionally, since the layout inflater provides the values instead of the user, the new name is also more accurate.

### Step 3: Switch to Dagger

At this point we can start migrating to Dagger's version. While it has been released for a few versions, the latest (at time of writing) is 2.33 which has some important bug fixes.

Aside from simple import changes, Dagger uses `@AssistedFactory` instead of `@AssistedInject.Factory` and disambiguation for assisted parameters of the same type goes directly on the `@Assisted` annotation rather than through a qualifier annotation.

```diff
-import com.squareup.inject.assisted.Assisted
+import dagger.assisted.Assisted
+import dagger.assisted.AssistedFactory
-import com.squareup.inject.asissted.AssistedInject
+import dagger.assisted.AssistedInject
-import javax.inject.Named

 class Example @AssistedInject constructor(
   graphValue: GraphValue,
-  @Assisted @Named("one") assistedValue1: String,
+  @Assisted("one") assistedValue1: String,
-  @Assisted @Named("two") assistedValue2: String,
+  @Assisted("two") assistedValue2: String,
 ) {
   // ...
   
-  @AssistedInject.Factory
+  @AssistedFactory
   interface Factory {
     fun create(
-      @Named("one") assistedValue1: String,
+      @Assisted("one") assistedValue1: String,
-      @Named("two") assistedValue2: String,
+      @Assisted("two") assistedValue2: String,
     ): Example
   }
 }
```

With assisted injection built-in to Dagger there is no need to have something like `@AssistedModule`. Delete it!

```diff
-@AssistedModule
-@Module(includes = AssistedInject_ExampleModule.class)
+@Module
 class ExampleModule {
   // ...
 }
```

There is one pattern to watch out for in this migration. Dagger's assisted injection requires that the factory return type matches the type which is being created. If one of your factories was returning a supertype of the enclosing class, you will have to pull out a second factory interface and use `@Binds` to support it.

```diff
 class TestPresenter @AssistedInject constructor(
   // ...
 ) : ObservableTransformer<Event, Model> {
   // ...
  
+  @AssistedFactory
+  interface DaggerFactory : Factory {
+    override fun create(/*..*/): TestPresenter
+  }
-  @AssistedInject.Factory
   interface Factory {
     fun create(/*..*/): ObservableTransformer<Event, Model>
   }
 }
```
```diff
 // In a module somewhere…
+@Binds
+abstract fun daggerFactoryTestPresenter(
+  factory: TestPresenter.DaggerFactory,
+): TestPresenter.Factory
```

### Step 4: Remove dependencies or upgrade to 0.9

At this point you can remove the Square AssistedInject dependencies. And if you're not using inflation injection then you're done!

If you are using inflation injection, upgrade to [version 0.9.1][nine-one] which completely removes the assisted injection parts. You're _almost_ done…

[nine-one]: https://github.com/cashapp/InflationInject/blob/trunk/CHANGELOG.md#091---2021-03-26

## InflationInject 1.0!

With assisted injection now excised from the project, [the GitHub repo][repo] was renamed to InflationInject, moved into the 'cashapp' organization, and given a quick makeover for its 1.0.0 release.

[repo]: https://github.com/cashapp/InflationInject

The Maven coordinates now use the groupId of 'app.cash.inject'.

```diff
-implementation 'com.squareup.inject:inflation-inject:0.9.1'
+implementation 'app.cash.inject:inflation-inject:1.0.0'
-annotationProcessor 'com.squareup.inject:inflation-inject-processor:0.9.1'
+annotationProcessor 'app.cash.inject:inflation-inject-processor:1.0.0'
```

Imports were changed to also use `app.cash.inject` as the base package.

```diff
-import com.squareup.inject.inflation.Inflated
+import app.cash.inject.inflation.Inflated
-import com.squareup.inject.inflation.InflationInject
+import app.cash.inject.inflation.InflationInject

 class ExampleView @InjectionInject constructor(
   // ...
 ) : View {
   // ...
 }
```

Inflation injection serves a niche need, and with Jetpack Compose UI becoming viable the project is unlikely to see much more development. With the primary goal of landing assisted injection upstream into Dagger fulfilled, it's still nice to have the rest of the project reach 1.0.
