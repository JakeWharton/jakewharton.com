---
title: 'Smaller APKs with resource optimization'
layout: post

categories: post
---

How many times does the name of a layout file appear in an Android APK? We can build a minimal APK with a single layout file to count the occurrences empirically. 

Building an Android app with Gradle requires only one thing: an `AndroidManifest.xml` file with a package. From there we can add a dummy layout whose contents are just `<merge/>` since we only care about its name.
```
.
├── build.gradle
└── src
    └── main
        ├── AndroidManifest.xml
        └── res
            └── layout
                └── home_view.xml
```

Running `gradle assembleRelease` will produce a release APK measuring a paltry 2,118 bytes. We can dump its contents using `xxd` and look for `home_view` byte sequences.

```
$ xxd build/outputs/apk/release/app-release-unsigned.apk
   ⋮
000004c0: 0000 0074 0000 0018 0000 0072 6573 2f6c  ...t.......res/l
000004d0: 6179 6f75 742f 686f 6d65 5f76 6965 772e  ayout/home_view.
000004e0: 786d 6c63 66e0 6028 6160 6060 6490 61d0  xmlcf.`(a```d.a.
   ⋮
00000570: 0000 0000 0000 0000 1818 7265 732f 6c61  ..........res/la
00000580: 796f 7574 2f68 6f6d 655f 7669 6577 2e78  yout/home_view.x
00000590: 6d6c 0000 0002 2001 f801 0000 7f00 0000  ml.... .........
   ⋮
00000700: 0000 0000 0909 686f 6d65 5f76 6965 7700  ......home_view.
00000710: 0202 1000 1400 0000 0100 0000 0100 0000  ................
   ⋮
00000870: 0000 ad04 0000 7265 732f 6c61 796f 7574  ......res/layout
00000880: 2f68 6f6d 655f 7669 6577 2e78 6d6c 504b  /home_view.xmlPK
   ⋮
```

There are three uncompressed occurrences of the path and one uncompressed occurrence of only the name in the APK based on this output.

If you have not read my [post on calculating zip entry size](https://jakewharton.com/calculating-zip-file-entry-true-impact/) or are not familiar with [the structure of a zip file](https://en.wikipedia.org/wiki/Zip_(file_format)#Structure), a zip file is a list of file entries followed by a directory of all available entries. Each entry contains the file path and so does the directory. This accounts for the first occurrence (the entry header) and the last occurrence (the directory record) in the output.

The middle two occurrences in the output are from inside the `resources.arsc` file which is a database of sorts for resources. Its contents are visible because the file is uncompressed inside the APK. Running `aapt dump --values resources build/outputs/apk/release/app-release-unsigned.apk` shows the `home_view` record and its mapping to the path:
```
Package Groups (1)
Package Group 0 id=0x7f packageCount=1 name=com.example
  Package 0 id=0x7f name=com.example
    type 0 configCount=1 entryCount=1
      spec resource 0x7f010000 com.example:layout/home_view: flags=0x00000000
      config (default):
        resource 0x7f010000 com.example:layout/home_view: t=0x03 d=0x00000000 (s=0x0008 r=0x00)
          (string8) "res/layout/home_view.xml"
```

The APK contains a fifth occurrence of the name inside the `classes.dex` file. It does not show up in the `xxd` output because the file is compressed. Running `baksmali dump <(unzip -p build/outputs/apk/release/app-release-unsigned.apk classes.dex)` shows the dex file's string table which contains an entry for `home_view`:
```
                           |[10] string_data_item
000227: 09                 |  utf16_size = 9
000228: 686f 6d65 5f76 6965|  data = "home_view"
000230: 7700               |
```

This is for the field inside the `R.layout` class which maps the layout name to a unique integer value. Incidentally, that integer is the index into the `resources.arsc` database to look up the associated file name for reading its XML contents.

To summarize the answer to our question, for each resource file, the full path appears three times and the name appears twice.


### Optimizing resources

Android Gradle plugin 4.2 introduces the `android.enableResourceOptimizations=true` flag which will run optimizations targeted for resources. This invokes the `aapt optimize` command on the merged resources and `resources.arsc` file before they are packaged into the APK. The optimization only applies to release builds and will run regardless of whether `minifyEnabled` is set to true.

With the flag added to `gradle.properties` we can compare two APKs using [diffuse](https://github.com/JakeWharton/diffuse) to see its effects. The output is long, so we'll break it apart by section.

```
          │       compressed        │       uncompressed
          ├─────────┬───────┬───────┼─────────┬─────────┬───────
 APK      │ old     │ new   │ diff  │ old     │ new     │ diff
──────────┼─────────┼───────┼───────┼─────────┼─────────┼───────
      dex │   695 B │ 695 B │   0 B │ 1,016 B │ 1,016 B │   0 B
     arsc │   682 B │ 674 B │  -8 B │   576 B │   564 B │ -12 B
 manifest │   535 B │ 535 B │   0 B │ 1.1 KiB │ 1.1 KiB │   0 B
      res │   185 B │ 157 B │ -28 B │   116 B │   116 B │   0 B
    asset │     0 B │   0 B │   0 B │     0 B │     0 B │   0 B
    other │    22 B │  22 B │   0 B │     0 B │     0 B │   0 B
──────────┼─────────┼───────┼───────┼─────────┼─────────┼───────
    total │ 2.1 KiB │ 2 KiB │ -36 B │ 2.7 KiB │ 2.7 KiB │ -12 B
```

First is a diff of the contents in the APK. The "compressed" columns are the size cost inside the APK, and the "uncompressed" columns are the cost when extracted.

The `res` category represents our single resource file whose size dropped 28 bytes. The `arsc` category is for the `resource.arsc` file which itself dropped 8 bytes. We'll see the cause of these changes shortly.

```
 DEX     │ old │ new │ diff
─────────┼─────┼─────┼───────────
   files │   1 │   1 │ 0
 strings │  15 │  15 │ 0 (+0 -0)
   types │   8 │   8 │ 0 (+0 -0)
 classes │   2 │   2 │ 0 (+0 -0)
 methods │   3 │   3 │ 0 (+0 -0)
  fields │   1 │   1 │ 0 (+0 -0)


 ARSC    │ old │ new │ diff
─────────┼─────┼─────┼──────
 configs │   1 │   1 │  0
 entries │   1 │   1 │  0
```

These two sections represent the code and contents of the resource database. Having no changes, we can infer that the optimizations have not affected the `R.layout.home_view` field nor the `home_view` resource entry.

```
=================
====   APK   ====
=================

   compressed   │  uncompressed  │
───────┬────────┼───────┬────────┤
 size  │ diff   │ size  │ diff   │ path
───────┼────────┼───────┼────────┼────────────────────────────
       │ -185 B │       │ -116 B │ - res/layout/home_view.xml
 157 B │ +157 B │ 116 B │ +116 B │ + res/eA.xml
 674 B │   -8 B │ 564 B │  -12 B │ ∆ resources.arsc
───────┼────────┼───────┼────────┼────────────────────────────
 831 B │  -36 B │ 680 B │  -12 B │ (total)
```

Finally, a granular diff of the file changes shows the effect of optimization. Our layout resource had its filename significantly truncated and was moved out of the `layout/` folder!

Inside the Gradle project, the folder and file names of XMLs have meaning. The folder is the resource type, and the name corresponds to the generated field and resource entry in the `.arsc` file. Once those files are inside the APK, however, the file path is meaningless and arbitrary. Resource optimization leverages this fact by making the names as short as possible[^1].

 [^1]: In this example, notably, the name doesn't seem as small as possible since it is two characters instead of one. A hash function computes the new name for each file. The number of resource files dictates the size of the hash which has a lower bound of two. The algorithm appears to work with a lower bound of one, so I'm not sure why the author chose to use two. Perhaps they didn't expect projects to contain fewer than 64 resources. I sent [r.android.com/1416749](https://r.android.com/1416749) to lower the bound.

The output of `aapt dump` confirms that the resource database also reflects the file change:
```
Package Groups (1)
Package Group 0 id=0x7f packageCount=1 name=com.example
  Package 0 id=0x7f name=com.example
    type 0 configCount=1 entryCount=1
      spec resource 0x7f010000 com.example:layout/home_view: flags=0x00000000
      config (default):
        resource 0x7f010000 com.example:layout/home_view: t=0x03 d=0x00000000 (s=0x0008 r=0x00)
          (string8) "res/eA.xml"
```

All three occurrences of the path in the APK are now shorter which results in the 36 byte savings. And while 36 bytes is a very small number, remember that the entire binary is only 2,118 bytes. A 36-byte savings is a 1.7% size reduction!


### Real-world examples

The resources of a real application number far more than just one. What does this optimization look like when applied to a real application?

#### Plaid

Nick Butcher's [Plaid](https://github.com/android/plaid) app has 734 resource files. In addition to their quantity, the names of the resource files are more descriptive (which is a fancy way of saying they're longer). Instead of `home_view`, Plaid contains names like `searchback_stem_search_to_back.xml`, `attrs_elastic_drag_dismiss_frame_layout`, and `designer_news_story_description.xml`.

After updating the project to AGP 4.2, I used `diffuse` to compare a build without resource optimization to one with it enabled:
```
          │            compressed             │           uncompressed
          ├───────────┬───────────┬───────────┼───────────┬───────────┬───────────
 APK      │ old       │ new       │ diff      │ old       │ new       │ diff
──────────┼───────────┼───────────┼───────────┼───────────┼───────────┼───────────
      dex │   3.8 MiB │   3.8 MiB │       0 B │   9.9 MiB │   9.9 MiB │       0 B
     arsc │ 316.7 KiB │ 292.5 KiB │ -24.2 KiB │ 316.6 KiB │ 292.4 KiB │ -24.2 KiB
 manifest │     3 KiB │     3 KiB │       0 B │  11.9 KiB │  11.9 KiB │       0 B
      res │ 539.2 KiB │ 490.7 KiB │ -48.5 KiB │ 617.2 KiB │ 617.2 KiB │       0 B
   native │   4.6 MiB │   4.6 MiB │       0 B │   4.6 MiB │   4.6 MiB │       0 B
    asset │       0 B │       0 B │       0 B │       0 B │       0 B │       0 B
    other │  83.6 KiB │  83.6 KiB │       0 B │ 128.6 KiB │ 128.6 KiB │       0 B
──────────┼───────────┼───────────┼───────────┼───────────┼───────────┼───────────
    total │   9.4 MiB │   9.3 MiB │ -72.7 KiB │  15.6 MiB │  15.5 MiB │ -24.2 KiB
```

Resource optimization netted a 0.76% savings on APK size. The native library size kept the impact smaller than I had hoped.

#### SeriesGuide

Uwe Trottmann's [SeriesGuide](https://github.com/UweTrottmann/SeriesGuide) app has 1044 resource files. Unlike Plaid, it is free of native libraries which should increase the impact of the optimization.

Once again I updated the project to AGP 4.2 and used `diffuse` to compare two builds:
```
          │            compressed             │           uncompressed
          ├───────────┬───────────┬───────────┼───────────┬───────────┬───────────
 APK      │ old       │ new       │ diff      │ old       │ new       │ diff
──────────┼───────────┼───────────┼───────────┼───────────┼───────────┼───────────
      dex │   2.4 MiB │   2.4 MiB │       0 B │   5.7 MiB │   5.7 MiB │       0 B
     arsc │   1.7 MiB │   1.6 MiB │ -32.9 KiB │   1.7 MiB │   1.6 MiB │ -32.9 KiB
 manifest │   5.6 KiB │   5.6 KiB │       0 B │  28.3 KiB │  28.3 KiB │       0 B
      res │ 693.9 KiB │   628 KiB │   -66 KiB │ 992.2 KiB │ 992.2 KiB │       0 B
    asset │  39.9 KiB │  39.9 KiB │       0 B │ 100.4 KiB │ 100.4 KiB │       0 B
    other │ 118.1 KiB │ 118.1 KiB │       0 B │ 148.8 KiB │ 148.8 KiB │       0 B
──────────┼───────────┼───────────┼───────────┼───────────┼───────────┼───────────
    total │   4.9 MiB │   4.8 MiB │ -98.9 KiB │   8.6 MiB │   8.6 MiB │ -32.9 KiB
```

Here resource optimization was able to reduce the APK size by 2.0%!

#### Tivi

Chris Banes' [Tivi](https://github.com/chrisbanes/tivi/) app has a non-trivial subset written using [Jetpack Compose](https://developer.android.com/jetpack/compose) which means fewer resources overall. A current build still contains 776 resource files.

By virtue of using Compose, Tivi is already using the latest AGP 4.2. With two quick builds we can see the impact of resource optimization:
```
          │            compressed             │           uncompressed
          ├───────────┬───────────┬───────────┼───────────┬───────────┬───────────
 APK      │ old       │ new       │ diff      │ old       │ new       │ diff
──────────┼───────────┼───────────┼───────────┼───────────┼───────────┼───────────
      dex │     3 MiB │     3 MiB │       0 B │   6.8 MiB │   6.8 MiB │       0 B
     arsc │ 363.4 KiB │ 337.9 KiB │ -25.6 KiB │ 363.3 KiB │ 337.7 KiB │ -25.6 KiB
 manifest │   3.6 KiB │   3.6 KiB │       0 B │  16.1 KiB │  16.1 KiB │       0 B
      res │ 680.4 KiB │ 629.2 KiB │ -51.2 KiB │   1.2 MiB │   1.2 MiB │       0 B
    asset │  39.9 KiB │  39.9 KiB │       0 B │ 100.4 KiB │ 100.4 KiB │       0 B
    other │ 159.9 KiB │ 151.7 KiB │  -8.2 KiB │ 306.3 KiB │ 254.8 KiB │ -51.5 KiB
──────────┼───────────┼───────────┼───────────┼───────────┼───────────┼───────────
    total │   4.2 MiB │   4.1 MiB │   -85 KiB │   8.8 MiB │   8.7 MiB │ -77.1 KiB
```

Once again we hit the 2.0% mark for APK size reduction!

### One more occurrence

All four examples so far have not used signed APKs. There are multiple versions of APK signing, and if your `minSdkVersion` is lower than 24 you are required include version 1 (V1) when signing. V1 signing uses [Java's `.jar` 
signing specification](https://docs.oracle.com/javase/tutorial/deployment/jar/intro.html) which signs each file individually as a text entry in the `META-INF/MANIFEST.MF` file.

After creating and configuring a keystore for the original single-layout app, dumping the manifest file with `unzip -c build/outputs/apk/release/app-release.apk META-INF/MANIFEST.MF` shows these signatures:
```
Manifest-Version: 1.0
Built-By: Signflinger
Created-By: Android Gradle 4.2.0-alpha08

Name: AndroidManifest.xml
SHA-256-Digest: HdoGVd8U3Zjtf2VkGLExAPCQ1fq+kNL8eHKjVQXGI60=

Name: classes.dex
SHA-256-Digest: BVA1ApPvECg56DrrNPgD3jgv1edcM8VKYjcJEAG4G44=

Name: res/eA.xml
SHA-256-Digest: nDn7UQex2OWB3/AT054UvSAx9pYNSWwERCLfgdM6J6c=

Name: resources.arsc
SHA-256-Digest: 6w7i2Z9+LjwqlXS7YhhjzP/XhgvJF3PUuyJM60t0Qbw=
```

The full path of each file makes an appearance bringing the total occurrences of each resource path to four. Since shorter names will once again result in this file containing fewer bytes, resource optimization has an even greater impact.

---

The Google-internal email which introduced me to this feature purported a savings of 1-3% on final APK size. Based on real-world tests this range seems to be about right. Ultimately the savings will depend on the size and number of resource files in your APK.

If you're already using AGP 4.2 add `android.enableResourceOptimizations=true` to your `gradle.properties` and enjoy this free APK size savings. If you are not yet on AGP 4.2 add it anyway so that you don't forget when you eventually upgrade!
