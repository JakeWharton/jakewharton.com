---
title: 'The Economics of Generated Code'
layout: post

categories: post
---
 
Among the many things that I've ~~stolen~~ learned from [Jesse Wilson](https://twitter.com/jessewilson) is the phrase "the economics of generated code". This captures the idea that the things we value when generating code are different than those we value for code that's manually written.

A code generator is only written once but the code it generates occurs many times. Thus, any investment into making the generator emit more efficient code will pay for itself very quickly. This generally means output less code and allocate fewer objects wherever possible. I'd like to expand on that with two specific, real-world examples which I've run into.


### Extra Method References

While it's not as much of a problem as it used to be, method reference count is still something worth keeping an eye on. This is especially true for generated code. Small changes in the generator can result in the count going up or down by the hundreds or thousands.

It's common for generated classes to be a subtype of a class in the runtime library. Aside from facilitating polymorphism, this allows consolidating common utilities and behavior. Take a JSON model that wants to retain unknown keys and values encountered during parsing. Each generated class could maintain its own `Map<String, ?>` for the unknown pairs, but this is a great candidate for consolidation into a base class in the library.

```java
abstract class JsonModel {
  private final Map<String, ?> unknownPairs;

  public final Map<String, ?> getUnknownPairs() {
    return unknownPairs;
  }

  // …
}
```

Not having a `getUnknownPairs()` method in each generated class should obviously reduce the count. But since the count is not just about declared methods, reducing the _referenced_ methods in the generated code will also have an impact.

Each generated class extends `JsonModel` and implements `toString()` which outputs its own fields and the `getUnknownPairs()` map.

```java
final class UserModel extends JsonModel {
  private final String name;
  private final String email;

  // …

  @Override public String toString() {
    return "UserModel{"
        + "name=" + name + ", "
        + "email=" + email + ", "
        + "unknownPairs=" + getUnknownPairs()
        + '}';
  }
}
```

When you compile, dex, and dump the Dalvik bytecode of the above class with `dexdump`, the way in which `toString()` invokes the `getUnknownPairs()` method is surprising.

<pre class="highlight">
[00024c] UserModel.toString:()Ljava/lang/String;
0000: iget-object v0, v5, LUserModel;.name:Ljava/lang/String;
0002: iget-object v1, v5, LUserModel;.email:Ljava/lang/String;
<b>0004: invoke-virtual {v5}, LUserModel;.getUnknownPairs:()Ljava/util/Map;</b>
0007: move-result-object v2
</pre>

Despite placing the `getUnknownPairs()` method on the `JsonModel` supertype, each generated class produces a reference to that method as if it were defined directly on the generated type. Moving the method does not actually reduce the count!

A medium-sized app might have 100 models for its API layer. If each generated class contains four calls to a method defined in the supertype that's 400 method references created for no purpose.

Changing the generated code to explicitly use `super` will produce method references which all point directly to the supertype method.

```diff
 @Override public String toString() {
   return "UserModel{"
       + "name=" + name + ", "
       + "email=" + email + ", "
-      + "unknownPairs=" + getUnknownPairs()
+      + "unknownPairs=" + super.getUnknownPairs()
       + '}';
 }
```
```diff
 [00024c] UserModel.toString:()Ljava/lang/String;
 0000: iget-object v0, v5, LUserModel;.name:Ljava/lang/String;
 0002: iget-object v1, v5, LUserModel;.email:Ljava/lang/String;
-0004: invoke-virtual {v5}, LUserModel;.getUnknownPairs:()Ljava/util/Map;
+0004: invoke-virtual {v5}, LJsonModel;.getUnknownPairs:()Ljava/util/Map;
 0007: move-result-object v2
```

Those 400 extra references are now reduced to just one! We would normally be unlikely to make such a change, but because we control the base class and the generated class this change is safe and results in a significant reduction of method references.

It's important to point out that using R8 to optimize your app will change this method reference automatically. Not every consumer of your code generator will be using an optimizer, though. Making this small change will ensure everyone benefits.


### String Duplication

Having strings in generated code isn't a given, but it shows up frequently enough to think about its impact. In my experience, strings in generated code tend to fall into two categories: keys for some type of serialization or error messages for exceptions. There's not much we can do about the former, but the latter is interesting because those strings exist in code paths which are expected to be rarely taken.

Take, for example, a code generator which binds Android views from a layout into fields of a class. Views are required when they're present in every configuration of the layout and we validate their presence at runtime with a null check.

```java
public final class MainBinding {
  // …

  public static MainBinding bind(View root) {
    TextView name = root.findViewById(R.id.name);
    if (name == null) {
      throw new NullPointerException("View 'name' required but not found");
    }
    TextView email = root.findViewById(R.id.email);
    if (email == null) {
      throw new NullPointerException("View 'email' required but not found");
    }
    return new MainBinding(root, name, email);
  }
}
```

If you compile, dex, and dump the contents of the `.dex` file using [Baksmali](https://github.com/JesusFreke/smali), you can see these strings in the string data section of the output.

```
                           |[20] string_data_item
00044f: 22                 |  utf16_size = 34
000450: 5669 6577 2027 6e61|  data = "View \'name\' required but not found"
000458: 6d65 2720 7265 7175|
000460: 6972 6564 2062 7574|
000468: 206e 6f74 2066 6f75|
000470: 6e64 00            |
                           |[21] string_data_item
000473: 23                 |  utf16_size = 35
000474: 5669 6577 2027 656d|  data = "View \'email\' required but not found"
00047c: 6169 6c27 2072 6571|
000484: 7569 7265 6420 6275|
00048c: 7420 6e6f 7420 666f|
000494: 756e 6400          |
```

In order to be encoded in the dex file format, these strings require 36 and 37 bytes, respectively (the extra two bytes for each encode their length and a null terminator).

With some napkin math we can estimate the cost of these strings in a real app. Each string requires 32 bytes plus the length of the view ID which we'll say is usually around 12 characters. A medium-sized app has around 50 layouts each with around 10 views. So 50 * 10 * (32 + 12) yields a total cost of 22KB. This isn't a huge amount of space, but considering we expect these strings to never be used unless there's a programming error the overhead feels unfortunate.

Strings are de-duplicated in dex so if the common parts of the string were separated we would only pay their cost once. Additionally, the string data section is also used to hold the names of fields so strings which match the name of a field will be free. Using this information, we might naively try to split up the string into three pieces.

```diff
 if (name == null) {
-  throw new NullPointerException("View 'name' required but not found");
+  throw new NullPointerException("View '" + "name" + "' required but not found");
 }
 TextView email = root.findViewById(R.id.email);
 if (email == null) {
-  throw new NullPointerException("View 'email' required but not found");
+  throw new NullPointerException("View '" + "email" + "' required but not found");
 }
```

Unfortunately, `javac` sees the concatenation of constants as something it can optimize so it turns them back into single, unique strings. To outsmart it, we need to generate code which uses a `StringBuilder` or the little-known `String.concat` method.

```diff
 if (name == null) {
-  throw new NullPointerException("View 'name' required but not found");
+  throw new NullPointerException("Missing required view with ID: ".concat("name"));
 }
 TextView email = root.findViewById(R.id.email);
 if (email == null) {
-  throw new NullPointerException("View 'email' required but not found");
+  throw new NullPointerException("Missing required view with ID: ".concat("email"));
 }
```

Now the dex file only contains a single prefix string and we don't pay for the ID strings because they were already being used for the `R.id.` fields.

```
                           |[17] string_data_item
00046a: 1f                 |  utf16_size = 31
00046b: 4d69 7373 696e 6720|  data = "Missing required view with ID: "
000473: 7265 7175 6972 6564|
00047b: 2076 6965 7720 7769|
000483: 7468 2049 443a 2000|
```

22KB of string data reduced to 33 bytes! Now it is worth noting that we spend an extra 7 bytes loading the second string and invoking `String.concat`, but since the string was always more than 32 bytes it's still a nice win. There's still room to de-duplicate the actual concatenation and exception throwing code so that it's only paid once per class instead of once per view, but I'll leave that for another post.

---

Seeing either of these optimizations in manually written code should raise an eyebrow. The individual savings of applying them are not worth their otherwise unidiomatic nature. With code generation, however, the economics are different. A single change to the generator can have optimizations like this apply to hundreds or thousands of locations producing a much larger effect.
