---
title: Java Interoperability Policy for Major Version Updates
layout: post

categories: post
tags:
- Android
- Java

lead: New major versions of libraries usually bring with it breaking changes in the form of big improvements. This is great for new users, but a hassle for existing clients. This is a new policy to make things suitable for both parties.
---

Major version updates to libraries solve the API warts of old and bring shiny new APIs to address previous shortcomings—often in a breaking fashion. Updating an Android or Java app is usually a day or two affair before you reap the benefits. Problems arise, however, when other libraries you depend on have transitive dependencies on older versions of the updated library.

[Retrofit 2.0][retrofit] is nearing release and it comes with three years of knowledge gained since its version 1.0—some of which is in backwards-incompatible API changes. We are fortunate to say that Retrofit has become a popular library, but it presents a real problem in that other libraries have been published which rely on its 1.x API. While a sudden breaking change doesn't present an immediate problem for them, consumers of those libraries wanting to upgrade their apps to the new API face a difficult choice.

This problem is not new, and I won't waste time rehashing all its nuances. After some discussion with [Jesse Wilson][jw2], we have decided on a course of action for the libraries we manage going forward in order to mitigate this pain. The following does not assume strict semantic versioning, but general adherence to its idea of major version bumps.

For major version updates in significantly foundational libraries we will take the following steps:

 1. **Rename the Java package to include the version number.**

    This immediately solves the API compatibility problem from transitive dependencies on multiple versions. Classes from each can be loaded on the same classpath without interacting negatively.

    Users can perform major versions updates gradually or in increments rather than requiring an immediate switch. If possible, shims for older versions can be built on newer versions in a sibling artifact.

    For example, versions 0.x and 1.x would be under `com.example.retrofit`, versions 2.x would be under `com.example.retrofit2`, and so on.

    (Libraries with a major version of 0 or 1 can skip this, and only start with major version 2 and above.)

 2. **Include the library name as part the group ID in the Maven coordinates.**

    Even for projects that have only a single artifact, including the project name in the group ID allows future updates that may introduce additional artifacts to not pollute the root namespace. In projects that have multiple artifacts from inception, it provides a means of grouping them together on artifact hosts like Maven central.

    For example, the Maven coordinates for the main Retrofit artifact could be `com.example.retrofit:retrofit`. Additional modules (present or future) can be listed under the same group ID such as `com.example.retrofit:converter-moshi`.

 3. **Rename the group ID in the Maven coordinates to include the version number.**

    Individual group IDs prevent dependency resolution semantics to upgrade older versions to newer, incompatible ones. Each major version is resolved independently allowing transitive dependencies to be upgraded compatibly.

    For example, take a project given _Library A_ with a dependency on 1.2.0, _Library B_ with a dependency on 1.3.0, _Library C_ with a dependency on 2.1.0, and a direct dependency on 2.4.0. A dependency resolver would first choose 1.3.0 for which _Library A_ and _Library B_ are compatible using the 1.x group ID. The resolver would then choose 2.4.0 for which _Library C_ is compatible using the 2.x group ID.

    Group ID renaming is chosen over the artifact ID for a few reasons:
    
     * The filename of built artifacts is the combination of the artifact ID the the version. If the artifact ID contained the major version it would appear redundant (e.g., `retrofit2-2.1.0`).
     * Projects can be comprised of multiple artifacts and not all of them contain the raw name of the project. Properly describing the contents of the artifact is more important than including versioning information.
     * Maven-based builds reference dependencies on sibling modules in the same project using their artifact ID but can use variables for the group ID and version. If the artifact ID were to change, a lot of error-prone `pom.xml` changes would be required instead of one group ID change.

     (Libraries with a major version of 0 or 1 can skip this, and only start with major version 2 and above.)

---

Each of these steps are not new ideas themselves. The growing usage of the libraries on which we work has forced us to figure out a reasonable policy to ensure major version upgrades are as smooth as possible. We are excited to offer something that will allow our users to upgrade sooner while also having relatively low maintenance cost for us.

The forthcoming releases of Retrofit 2.0 and OkHttp 3.0 will be the first two libraries to apply this policy. Enjoy!


 [retrofit]: https://github.com/square/retrofit/
 [jw2]: https://twitter.com/jessewilson
