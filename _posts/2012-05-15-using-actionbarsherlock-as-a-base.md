---
title: Using ActionBarSherlock As A Base
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/using-actionbarsherlock-as-a-base

categories: post
tags:
- Android
- ActionBarSherlock
---

[ActionBarSherlock](http://actionbarsherlock.com/) is an open source Android library which aims to bring the full experience of the [action bar design pattern](http://developer.android.com/design/patterns/actionbar.html) to previous versions of the platform under a unified API and theme. The library delegates to the built-in platform action bar where appropriate and uses a compatibility implementation otherwise.

At present there are a few choices for adding an action bar to an application. Here we will talk about the advantages and disadvantages of three of the popular options that developers choose: custom layouts, ActionBarCompat, and ActionBarSherlock.


### Custom Layout

Creating a custom layout for the action bar and including it on the top of each screen allows you to customize and perfect all of the functionality that you require. It also ensures that you can very easily style the action bar to match the look of your application and/or your brand.

![](/static/post-image/abs-base-0.png)

<small><em>The popular Instagram application uses a custom layout for their action bar which feels out of place here pictured running on Ice Cream Sandwich.</em></small>

While this provides the greatest flexibility, it also requires the largest investment of developer time. Any new features that you require must be built from scratch.

Another major disadvantage is that you will not be using the built-in platform action bar unless you invest the time to make that happen. This means that an application will not be able to use future improvements to the action bar and it may feel out of place with the rest of the platform when run on versions of Android where there is a platform action bar available for use.


### ActionBarCompat

Google provides a sample, [ActionBarCompat](http://developer.android.com/resources/samples/ActionBarCompat/index.html), which provides a very rudimentary pre-Honeycomb action bar. The sample uses the traditional options menu callbacks and leverages helper classes from the [support library](http://developer.android.com/sdk/compatibility-library.html) to ensure it functions properly on all platforms.

![](/static/post-image/abs-base-1.png)

<small><em>Google’s ActionBarCompat sample shows action items in use. More advanced features such as list navigation, tab navigation, action providers, and action modes are not available.</em></small>

Where this sample falls short is that it only provides a very limited subset of the action bar features. Any additional functionality that you require in your application will have to be added by you.

ActionBarCompat is only meant to be viewed as a sample of how to write a custom action bar that also uses the built-in platform version where available. Do not think of this implementation as a full library for you to use; it will likely require the same amount of work as a custom layout implementation.


### ActionBarSherlock

ActionBarSherlock provides the full feature set of the latest built-in platform action bar to Android 2.1 and newer. The functionality of the library is exposed by calling getSupportActionBar() rather than getActionBar() but the API is exactly the same. The library handles switching between the built-in platform action bar (when available) and a compatibility version (when it is not). This means that you are able to translate all of the documentation and samples for the built-in action bar to ActionBarSherlock by only changing the method which you are calling.

![](/static/post-image/abs-base-2.png)

<small><em>A demonstration of some of the ActionBarSherlock features: stacked tab navigation, action items, and the “up” indicator on icon.</em></small>

All of the advanced features of the action bar work on Android 2.1 and newer without any additional effort: list navigation, stacked tab navigation, custom action item views, action item submenus, and action modes are available for use.

Theming ActionBarSherlock directly mirrors theming the built-in platform action bar. Customizing the look and feel is done by creating an action bar style which has duplicates of each attribute being specified — one with the regular android:-prefix and one without. This applies the style regardless of which version of the action bar is currently in use.

By combining ActionBarSherlock with Google’s support library you are able to provide the three key features introduced in Honeycomb — the action bar, fragments, and loaders — to 99% of the devices which have access to the Google Play Store. All of this can be done without having to write any code that is specific to the version of Android on which an app is currently running.

---

In the latest major version of [Pay with Square](https://play.google.com/store/apps/details?id=com.squareup.cardcase) _(formerly Card Case)_, Square chose to use ActionBarSherlock.

![](/static/post-image/abs-base-3.png)

![](/static/post-image/abs-base-4.png)

![](/static/post-image/abs-base-5.png)

<small><em>Three examples of the Pay with Square app using numerous ActionBarSherlock features on versions of Android where the action bar would otherwise not be present.</em></small>

Initially the action bar was only used for very basic navigation and action items. As we developed the application further we found that in certain areas that the more advanced features of the action bar could be utilized in order to enhance the user experience.

Action providers, expandable action items, and dynamic theming are some of the features now used throughout the application to provide a much more immersive experience while interacting with our merchants. By choosing to use ActionBarSherlock initially, we did not have to worry about support for these features since they were already available. If we had followed one of the other two paths mentioned above these features would have required a lot more work. Or more likely, we wouldn’t have added them and the app would be a much poorer experience.

Enabling these features was as simple as looking at the official documentation for the native action bar. Since the API is exactly the same, the implementation was trivial and it allowed us to allocate developer time to improving the core functionality of the application rather than spending precious time adding these features.

Want to learn more? Check out the [ActionBarSherlock website](http://actionbarsherlock.com/) for more information.