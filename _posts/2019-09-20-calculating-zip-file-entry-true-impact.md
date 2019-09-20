---
title: Calculating the true impact of zip file entries
layout: post

categories: post
tags:
- Java
---

How can we determine the impact of each entry on a zip file's size? It seems like a trivial problem, but things quickly don't add up.

There's three built-in ways to read information about the contents of zip file in Java:

 1. Mount the zip as a `FileSystem` using `FileSystems.newFileSystem` and then access its contents using `Path`s.
 2. Open it with `ZipInputStream` for a one-shot iteration over the zip entries.
 3. Open it with `ZipFile` for random access to the zip entries.

The first mechanism is extremely convenient. It allows interacting with the contents of a zip file using the same APIs as normal files. Unfortunately, by virtue of being exposed like regular files, you only have one way to check their size: `Files.size(Path)`. This delegates to an API called `BasicFileAttributes.size()` which returns size of the file contents. While there is a `ZipFileAttributes.compressedSize()` for returning the size of the compressed contents, it's internal to the JDK and not available for our use.

The other two mechanisms,`ZipInputStream` and `ZipFile`, both expose entries using the `ZipEntry` type. These being zip-centric APIs, many of the properties of the zip file format are directly available. Notably for our use case, there's a `getCompressedSize()` method.

Problem solved? Not exactly…

If you sum the compressed size of all entries in a zip the result will not equal the size of the zip file. This isn't entirely unexpected. After all, the zip file format surely requires additional metadata to track per-entry information like the relative path of each compressed file.

So if we're looking to calculate the _actual_ size impact of an entry on the final zip, can we do it?


### Zip file format

An overview of the zip file format specification can be [found on Wikipedia](https://en.wikipedia.org/wiki/Zip_(file_format)#Structure). It consists of a list of entries which are each defined as header followed by the compressed data (whose length is specified in the header). Finally, at the end, there is a central directory which lists all of the entries available in the file.

<a href="/static/post-image/zip_layout@2x.png">
  <img
    src="/static/post-image/zip_layout.png"
    srcset="/static/post-image/zip_layout.png 1x,
            /static/post-image/zip_layout@2x.png 2x"
    alt="Diagram showing the zip file format as previously described."
    />
</a>

A slight tangent: Given this format, it's pretty obvious how `ZipInputStream` and `ZipFile` work. The former simply iterates forward through the bytes reading each entry as it comes. The latter parses the central directory at the end and then jumps to the offset of whichever entry you request.

Back on our problem, `ZipEntry.getCompressedSize()` is only exposing the length of compressed data (pictured as the blue `<data>` blocks). However, the header for each entry and the record in the central directory also contribute to the overall size impact. Thus, to get the real value, we need to be able to calculate the size of those two things.

#### Zip entry header

The header for each entry is defined as follows:

<style type="text/css">
th,td { padding-right: 15px; padding-bottom: 5px; }
table { margin-bottom: 15px; }
</style>

| Offset | Size | Description                 |
| ------ | ---- | --------------------------- |
| 0      | 4    | Local file header signature |
| ...    | ...  | ...                         |
| 26     | 2    | File name length (n)        |
| 28     | 2    | Extra field length (m)      |
| 30     | n    | File name                   |
| 30+n   | m    | Extra field                 |

Here we can see that the size of the header will be a fixed 30 bytes plus the length of `ZipEntry.getName()` (as UTF-8 bytes) plus the length of `ZipEntry.getExtra()` (which returns opaque bytes).

There is also an optional trailer which can be either 12 or 16 bytes. This is only present when a specific bit in one of the fields of the header is set. Unfortunately, the field which contains the bit is not exposed in the API of `ZipEntry`, and so we cannot include it in the calculation. Thankfully, this seems infrequently used.

#### Central directory record

The central directory is a list of records for each file followed by a single end-of-directory record.

The record for each entry is defined as follows:

| Offset | Size | Description                             |
| ------ | ---- | --------------------------------------- |
| 0      | 4    | Central directory file header signature |
| ...    | ...  | ...                                     |
| 42     | 4    | Relative offset of local file header.   |
| 46     | n    | File name                               |
| 46+n   | m    | Extra field                             |
| 46+n+m | k    | File comment                            |

The size will be 46 bytes plus the length of `ZipEntry.getName()` plus the length of `ZipEntry.getExtra()` plus the length of `ZipEntry.getComment()` (as UTF-8 bytes).

The end-of-directory record is defined as follows:

| Offset | Size | Description                        |
| ------ | ---- | ---------------------------------- |
| 0      | 4    | End of central directory signature |
| ...    | ...  | ...                                |
| 20     | 2    | Comment length (n)                 |
| 22     | n    | Comment                            |

Its size is 22 bytes plus the length of `ZipFile.getComment()` (as UTF-8) bytes. `ZipInputStream`, since it only iterates forward over the entries, does not expose the zip comment.


### Putting it all together

With this knowledge of the zip file format we can now calculate a more accurate representation of the impact of each entry.

```java
static long entryImpactBytes(ZipEntry entry) {
  int nameSize = entry.getName().getBytes(UTF_8).length;
  int extraSize = entry.getExtra() != null
      ? entry.getExtra().length
      : 0;
  int commentSize = entry.getComment() != null
      ? entry.getComment().getBytes(UTF_8).length
      : 0;

  // Calculate the actual compressed size impact in the zip, not just compressed data size.
  // See https://en.wikipedia.org/wiki/Zip_(file_format)#File_headers for details.
  return entry.getCompressedSize()
      // Local file header. There is no way of knowing whether a trailing data descriptor
      // was present since the general flags field is not exposed, but it's unlikely.
      + 30 + nameSize + extraSize
      // Central directory file header.
      + 46 + nameSize + extraSize + commentSize;
}
```

Using this method, a sum of all entries will put you very close to the actual size of the zip file. All that's left is to account for the end-of-directory record from the central directory.

```java
static int additionalBytes(ZipFile file) {
  int commentSize = file.getComment() != null
      ? file.getComment().getBytes(UTF_8).length
      : 0;
  return 22 + commentSize;
}
```

Using these two functions, the sum total should now exactly match the size of the zip file.

There's some small improvements to be had here if we want. For one, we don't need to encode the name and comment as UTF-8 bytes only then to get its length. Libraries like [Guava](https://guava.dev/releases/19.0/api/docs/com/google/common/base/Utf8.html#encodedLength(java.lang.CharSequence)) and [Okio](https://square.github.io/okio/2.x/okio/okio/kotlin.-string/utf8-size/) provide methods for calculating the UTF-8 length directly on a `String`. Additionally, the zip format is so simple that you could write your own parser which included the file trailers in its calculation depending on how accurate you needed the numbers to be.

---

This `entryImpactBytes` method can be useful for calculating how much a zip file size will change when an entry is added or removed. But it really shines when you have two versions of a zip file. For example, reducing the contents of one file by 100 bytes _and_ removing 50 bytes from its name will result in a net change of -200 bytes (2 * name diff + content diff). If you were only using `ZipEntry.getCompressedSize()` to compute such a difference, the result would only show a change of -100 bytes.