---
title: Easy HTTP Requests with Retrofit
layout: post

external: true
blog: Square Corner
blog_link: https://developer.squareup.com/blog/easy-http-requests-with-retrofit

categories: post
tags:
- Java
- Android
- Retrofit
- HTTP
---

There are few fundamentals to application development. One that is almost always guaranteed is making HTTP requests and reading their responses. Retrofit is an open source library from Square which simplifies HTTP communication by turning remote APIs into declarative, type-safe interfaces.

Methods declared on an interface represent a single remote API endpoint. Annotations describe how the method maps to an HTTP request.

To demonstrate how Retrofit works we will use it to list the top contributors on [OkHttp](http://github.com/square/okhttp)’s GitHub project.


### Declaring API

GitHub’s [well-documented API](http://developer.github.com/) provides a call to list all contributors on a repository. Here’s what an interface containing that call looks like:

```java
public interface GitHubService {
  @GET("/repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(
      @Path("owner") String owner,
      @Path("repo") String repo
  );
}
```

Let’s break down what the above interface contains:

* `@GET(“/repos/{owner}/{repo}/contributors”)` — This is both the HTTP request method (“GET”) and the relative URL to fetch. The `{owner}` and `{repo}` strings are replacement blocks.
* `@Path(“owner”)` and `@Path(“repo”)` define method arguments that will be used for replacing sections in the URL.
* `List<Contributor>` — The type of the response body.

For this to work we will need a small `Contributor` type.

```java
public class Contributor {
  public String login; // GitHub username.
  public int contributions; // Commit count.
}
```


### Using RestAdapter

RestAdapter is the class which transforms an API interface into an object which actually makes network requests.

In order to use our GitHubService we need to create a RestAdapter and then use it to create a real instance of the interface.

```java
RestAdapter restAdapter = new RestAdapter.Builder()
  .setServer("https://api.github.com") // The base API endpoint.
  .build();

GitHubService github = restAdapter.create(GitHubService.class);
```

To list contributors, we call our method with the appropriate data.

```java
List<Contributor> contributors = github.contributors("square", "okhttp");
for (Contributor contributor : contributors) {
  log(contributor.login + " - " + contributor.contributions);
}
```

Not only have we hidden the complexities of HTTP away from our implementation code, we’ve also hidden the URL structure and requirements of the GitHub API into testable Java objects.


### Retofit 1.0

More information about configuring and using Retrofit as well as downloads can be found [on its website](http://square.github.io/retrofit/).

**Fun fact:** Retrofit is actually Square’s oldest open source project! The [first commit](https://github.com/square/retrofit/commit/17886a10eecccada75e736cb2ffb30b8b8a58b55) was made by Bob Lee on September 6th, 2010!

_This post is part of Square’s “[Seven Days of Open Source](https://corner.squareup.com/2013/05/seven-days-of-open-source.html)” series._