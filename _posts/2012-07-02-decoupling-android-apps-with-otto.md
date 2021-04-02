---
title: Decoupling Android App Communication with Otto
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/decoupling-android-app-communication-with-otto

categories: post
tags:
- Android
- Otto
---

[Otto](http://square.github.com/otto) is a tiny but useful library for Android which aims to simplify how parts of your application communicate with each other.


### The Situation

As Android applications increase in complexity, ensuring effective communication between different parts becomes more and more difficult.

In multiple places of your app, components will need to update their state based on actions that occured somewhere else. A common solution to this problem was having a component expose a listener interface which interested parties could implement. These implementations would then register and unregister with the component allowing them to receive updates.

For example, the [Pay with Square app](http://play.google.com/store/apps/details?id=com.squareup.cardcase) relies on the device location and used to expose an interface to listen for updates from our SquareLocationManager class.

```java
interface SquareLocationListener {
  void onLocationChanged(SquareLocation location);
}
```

Different parts of the application implemented the interface and performed registration and unregistration on the SquareLocationManager where appropriate.

```java
// Assume a squareLocationManager reference exists.
squareLocationManager.get().register(this);
```


### The Problem

While the above solution worked, it was not ideal. Each component implementing the interface contained a hard dependency on the location manager for registration. It also meant that when testing, the location manager would have to be mocked in order to simulate location updates.

In addition to the location manager, it should be easy to see how this pattern can spiral out of control with other components. There may be many other things that parts of your application care about: user authentication, data synchronization updates, changes in settings, or configuration changes–or all of the above!

```java
// This is becoming unmanageable...
squareLocationManager.get().register(this);
userAuthenticator.get().register(this);
settingsManager.get().register(this);
syncManager.get().register(this);
configurationMonitor.get().register(this);
```

Remember, for each one of these registrations we must also implement an interface from the target component and whichever methods it requires.

The registration and unregistration with all of the different sources of information can easily become an unmanageable graph of dependencies. Coupled with difficulty when testing, this slows developer productivity and has the potential to create bugs in your application.


### The Solution

In order to find an elegant solution to this problem, a technique is borrowed from an unexpected place: Swing applications. The event bus pattern — also known as message bus or publisher/subscriber model — allows for the communication of two components to occur without either of them being immediately aware of the other.

Rather than explicitly registering with every necessary service, a component now registers once with the event bus.

```java
bus.register(this);
```

With this single registration we inform the event bus that we are interested in receiving updates from different components. The bus checks our methods for any which are annotated with @Subscribe, and will notify us by calling these methods when the appropriate event occurs.

In the example above the component was interested in location updates. Rather than having the class implement an interface and the methods it requires, we provide a single method:

```java
@Subscribe
public void locationChanged(LocationChangedEvent event) {
    // TODO React to location change.
}
```

With this method declaration, the bus will now dispatch any LocationChangedEvents to the class.

The SquareLocationManager class, without needing any registration or having to keep track of listeners, can post events onto the bus and classes who have subscribed will each be notified.

```java
bus.post(new LocationChangedEvent(37.892818, -121.772608));
```

Not only can components now be decoupled from each other but testing also becomes much more straightforward. Arbitrary events can be posted to the bus in order to represent any state of your application against which tests can be written.

_(Note: you might recognize that this pattern already exists on Android at a much higher level: the intent system!)_


### Otto for Android

Otto is Square’s library for implementing the event bus pattern in our applications. Forked from Guava, Otto adds unique functionality to an already refined event bus as well as specializing it to the Android platform.

With Otto, Square has been able to write much more loosely coupled and easily testable apps. We hope that you will be able to utilize it to do the same.

You can read more about the library on [Otto’s website](http://square.github.com/otto) or its [GitHub project](http://github.com/square/otto).