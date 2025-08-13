---
title: 'Optimizing Bytecode by Manipulating Source Code'
layout: post

categories: post
---

This post is a follow-up to "[The Economics of Generated Code](/the-economics-of-generated-code/)" which argued that spending time optimizing generated code is more worthwhile than the same optimizations done in manually-written code.

The second example from that post dealt with looking up views, checking for null, and potentially throwing an exception. In an effort to reduce the impact of the generated exception message string, each was split into a prefix which will be de-duplicated and the view ID name which was effectively free since it matched a field name. If you're lost on what that all means, check out the other post first.

```diff
 public static MainBinding bind(View root) {
   TextView name = root.findViewById(R.id.name);
   if (name == null) {
-    throw new NullPointerException("View 'name' required but not found");
+    throw new NullPointerException("Missing required view with ID: ".concat("name"));
   }
   TextView email = root.findViewById(R.id.email);
   if (email == null) {
-    throw new NullPointerException("View 'email' required but not found");
+    throw new NullPointerException("Missing required view with ID: ".concat("email"));
   }
   return new MainBinding(root, name, email);
 }
```

That change was just about strings, but I also mentioned that there's more optimization which could be done. So let's do it!

By virtue of the fact that we throw an exception when a view is absent, that case is expected to be rare. This is what allowed us to justify sacrificing a single string constant in favor of multiple constants and runtime concatenation. While that allowed us to de-duplicate the strings, it creates more duplication in the bytecode.

```
[000288] MainBinding.bind:(Landroid/view/View;)LMainBinding;
0000: sget v0, LR$id;.name:I
0002: invoke-virtual {v3, v0}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
0005: move-result-object v0
0006: check-cast v0, Landroid/widget/TextView;

0008: if-nez v0, 0018

000a: new-instance v0, Ljava/lang/NullPointerException;
000c: const-string v1, "Missing required view with ID: "
000e: const-string v2, "name"
0010: invoke-virtual {v1, v2}, Ljava/lang/String;.concat:(Ljava/lang/String;)Ljava/lang/String;
0013: move-result-object v1
0014: invoke-direct {v0, v1}, Ljava/lang/NullPointerException;.<init>:(Ljava/lang/String;)V
0017: throw v0

0018: sget v1, LR$id;.email:I
001a: invoke-virtual {v3, v1}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
001d: move-result-object v1
001e: check-cast v1, Landroid/widget/TextView;

0020: if-nez v1, 0030

0022: new-instance v0, Ljava/lang/NullPointerException;
0024: const-string v1, "Missing required view with ID: "
0026: const-string v2, "email"
0028: invoke-virtual {v1, v2}, Ljava/lang/String;.concat:(Ljava/lang/String;)Ljava/lang/String;
002b: move-result-object v1
002c: invoke-direct {v0, v1}, Ljava/lang/NullPointerException;.<init>:(Ljava/lang/String;)V
002f: throw v0

0030: new-instance v2, LMainBinding;
0032: invoke-direct {v2, v3, v0, v1}, LMainBinding;.<init>:(Landroid/view/View;Landroid/widget/TextView;Landroid/widget/TextView;)V
0035: return-object v2
```

I've spaced the bytecode out so it's easier to see the logical sections and, hopefully, identify what we want to change.

Indices `000a`–`0017` and `0022`–`002f` are near-exact duplicates of each other which only vary by the name of the missing view. Again, because this code is expected to never run, it would be nice to remove the duplication. Fixing this will be the focus of the post, but I also want to point out a second problem that we'll fix in tandem.

In addition to the exception code being duplicated it's also interspersed between "normal" code. This means that the common execution path of required views being present has to jump over unused bytecode.

![](/static/post-image/bytecode-economics-1.png)
<!--
digraph {
  ranksep=.2;
  nodesep=0;
  node [fontsize=10, height=.05,  fontname="menlo"];
  {
    node [shape=plaintext];
    edge [style=invis];
    "0000" -> "0008" -> "000a" -> "0018" -> "0020" -> "0022" -> "0030";
  }
  {
    node [shape=plaintext, width=2];
    edge [style=invis];
    "findViewById(name)" -> "if name == null" -> "throw NPE(name)" -> "findViewById(email)" -> "if email == null" -> "throw NPE(email)" -> "return MainBinding(…)"
  }
  {
    edge [arrowsize=0.5];
    "findViewById(name)" -> "if name == null"[constraint=false];
    "if name == null" -> "throw NPE(name)"[constraint=false];
    "if name == null" -> "findViewById(email)"[constraint=false, margin="4,4"];
    "findViewById(email)" -> "if email == null"[constraint=false];
    "if email == null" -> "throw NPE(email)"[constraint=false];
    "if email == null" -> "return MainBinding(…)"[constraint=false];
  }
  { rank=same; "0000"; "findViewById(name)"; }
  { rank=same; "0008"; "if name == null"; }
  { rank=same; "000a"; "throw NPE(name)"; }
  { rank=same; "0018"; "findViewById(email)"; }
  { rank=same; "0020"; "if email == null"; }
  { rank=same; "0022"; "throw NPE(email)"; }
  { rank=same; "0030"; "return MainBinding(…)"; }
}
-->

The code was actually compiled with the old `dx` tool to produce the bytecode above. Simply compiling with D8 instead produces a dramatically different arrangement of the control flow.

```
[000258] MainBinding.bind:(Landroid/view/View;)LMainBinding;
0000: sget v0, LR$id;.name:I
0002: invoke-virtual {v3, v0}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
0005: move-result-object v0
0006: check-cast v0, Landroid/widget/TextView;

0008: const-string v1, "Missing required view with ID: "

000a: if-eqz v0, 0028

000c: sget v2, LR$id;.email:I
000e: invoke-virtual {v3, v2}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
0011: move-result-object v2
0012: check-cast v2, Landroid/widget/TextView;

0014: if-eqz v2, 001c

0016: new-instance v1, LMainBinding;
0018: invoke-direct {v1, v3, v0, v2}, LMainBinding;.<init>:(Landroid/view/View;Landroid/widget/TextView;Landroid/widget/TextView;)V
001b: return-object v1

001c: new-instance v3, Ljava/lang/NullPointerException;
001e: const-string v0, "email"
0020: invoke-virtual {v1, v0}, Ljava/lang/String;.concat:(Ljava/lang/String;)Ljava/lang/String;
0023: move-result-object v0
0024: invoke-direct {v3, v0}, Ljava/lang/NullPointerException;.<init>:(Ljava/lang/String;)V
0027: throw v3

0028: new-instance v3, Ljava/lang/NullPointerException;
002a: const-string v0, "name"
002c: invoke-virtual {v1, v0}, Ljava/lang/String;.concat:(Ljava/lang/String;)Ljava/lang/String;
002f: move-result-object v0
0030: invoke-direct {v3, v0}, Ljava/lang/NullPointerException;.<init>:(Ljava/lang/String;)V
0033: throw v3
```

D8 understands that the case in which you throw an exception is, well, exceptional. Thus, the conditionals are inverted so that the exceptional cases move to the end of the method. This makes the common case not require any jumps.

![](/static/post-image/bytecode-economics-2.png)
<!--
digraph {
  ranksep=.2;
  nodesep=0;
  node [fontsize=10, height=.05,  fontname="menlo"];
  {
    node [shape=plaintext];
    edge [style=invis];
    "0000" -> "0008" -> "000a" -> "000c" -> "0014" -> "0016" -> "001c" -> "0028";
  }
  {
    node [shape=plaintext, width=2];
    edge [style=invis];
    "findViewById(name)" -> "const-string \"Missing\"" -> "if name != null" -> "findViewById(email)" -> "if email != null" -> "return MainBinding(…)" -> "throw NPE(name)" -> "throw NPE(email)"
  }
  {
    edge [arrowsize=0.5];
    "findViewById(name)" -> "const-string \"Missing\""[constraint=false];
    "const-string \"Missing\"" -> "if name != null"[constraint=false];
    "if name != null" -> "throw NPE(name)"[constraint=false];
    "if name != null" -> "findViewById(email)"[constraint=false, margin="4,4"];
    "findViewById(email)" -> "if email != null"[constraint=false];
    "if email != null" -> "throw NPE(email)"[constraint=false];
    "if email != null" -> "return MainBinding(…)"[constraint=false];
  }
  { rank=same; "0000"; "findViewById(name)"; }
  { rank=same; "0008"; "const-string \"Missing\""; }
  { rank=same; "000a"; "if name != null"; }
  { rank=same; "000c"; "findViewById(email)"; }
  { rank=same; "0014"; "if email != null"; }
  { rank=same; "0016"; "return MainBinding(…)"; }
  { rank=same; "001c"; "throw NPE(name)"; }
  { rank=same; "0028"; "throw NPE(email)"; }
}
-->

Another side-effect of using D8 is that the loading of the exception message prefix string was de-duplicated at bytecode index `0008`. This is actually an unfortunate behavior since it now occurs during normal execution as well.

Before attempting to fix these problems, let's manually re-arrange the bytecode (with dummy indices, for simplicity) to the ideal form we'd like to produce.

```
[000258] MainBinding.bind:(Landroid/view/View;)LMainBinding;
0000: sget v0, LR$id;.name:I
0001: invoke-virtual {v3, v0}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
0002: move-result-object v0
0003: check-cast v0, Landroid/widget/TextView;

0010: if-eqz v0, 0050

0020: sget v1, LR$id;.email:I
0021: invoke-virtual {v3, v1}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
0022: move-result-object v1
0023: check-cast v1, Landroid/widget/TextView;

0030: if-eqz v1, 0060

0040: new-instance v2, LMainBinding;
0041: invoke-direct {v2, v3, v0, v1}, LMainBinding;.<init>:(Landroid/view/View;Landroid/widget/TextView;Landroid/widget/TextView;)V
0042: return-object v2

0050: const-string v2, "email"
0051: goto 0070

0060: const-string v2, "name"

0070: const-string v1, "Missing required view with ID: "
0071: new-instance v3, Ljava/lang/NullPointerException;
0072: invoke-virtual {v1, v2}, Ljava/lang/String;.concat:(Ljava/lang/String;)Ljava/lang/String;
0073: move-result-object v2
0074: invoke-direct {v3, v2}, Ljava/lang/NullPointerException;.<init>:(Ljava/lang/String;)V
0075: throw v3
```

This has everything we want: the normal execution case flows from index `0000` to `0042` without jumps and the exception-handling code is de-deuplicated at index `0070` to `0075`. There's only one load of the prefix string as part of creating the exception message. When a null is found, the code jumps to a section which loads the correct view ID string and then jumps (or falls through) to the exception.

![](/static/post-image/bytecode-economics-3.png)
<!--
digraph {
  ranksep=.2;
  nodesep=0;
  node [fontsize=10, height=.05,  fontname="menlo"];
  {
    node [shape=plaintext];
    edge [style=invis];
    "0000" -> "0010" -> "0020" -> "0030" -> "0040" -> "0050" -> "0060" -> "0070";
  }
  {
    node [shape=plaintext, width=2];
    edge [style=invis];
    "findViewById(name)" -> "if name != null" -> "findViewById(email)" -> "if email != null" -> "return MainBinding(…)" -> "const-string \"name\"" -> "const-string \"email\"" -> "throw NPE(missingId)"
  }
  {
    edge [arrowsize=0.5];
    "findViewById(name)" -> "if name != null"[constraint=false];
    "if name != null" -> "const-string \"name\""[constraint=false];
    "if name != null" -> "findViewById(email)"[constraint=false, margin="4,4"];
    "findViewById(email)" -> "if email != null"[constraint=false];
    "if email != null" -> "const-string \"email\""[constraint=false];
    "if email != null" -> "return MainBinding(…)"[constraint=false];
    "const-string \"name\"" -> "throw NPE(missingId)"[constraint=false];
    "const-string \"email\"" -> "throw NPE(missingId)"[constraint=false];
  }
  { rank=same; "0000"; "findViewById(name)"; }
  { rank=same; "0010"; "if name != null"; }
  { rank=same; "0020"; "findViewById(email)"; }
  { rank=same; "0030"; "if email != null"; }
  { rank=same; "0040"; "return MainBinding(…)"; }
  { rank=same; "0050"; "const-string \"name\""; }
  { rank=same; "0060"; "const-string \"email\""; }
  { rank=same; "0070"; "throw NPE(missingId)"; }
}
-->

Now that we have a goal it's easier to iterate on the generated Java code to see how our changes move us closer or farther from achieving it. Let's start by de-duplicating the exception code.

```diff
 public static MainBinding bind(View root) {
+  String missingId = null;
   TextView name = root.findViewById(R.id.name);
   if (name == null) {
+    missingId = "name";
-    throw new NullPointerException("Missing required view with ID: ".concat("name"));
   }
   TextView email = root.findViewById(R.id.email);
   if (email == null) {
+    missingId = "email";
-    throw new NullPointerException("Missing required view with ID: ".concat("email"));
   }
-  return new MainBinding(root, name, email);
+  if (missingId == null) {
+    return new MainBinding(root, name, email);
+  }
+  throw new NullPointerException("Missing required view with ID: ".concat(missingId));
 }
```

This produces bytecode which successfully de-duplicates the exception code but with a slight penalty on the other parts.

```
[000258] MainBinding.bind:(Landroid/view/View;)LMainBinding;
0000: sget v0, LR$id;.name:I
0002: invoke-virtual {v3, v0}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
0005: move-result-object v0
0006: check-cast v0, Landroid/widget/TextView;

0008: if-nez v0, 000d

000a: const-string v1, "name"
000c: goto 000e

000d: const/4 v1, #int 0

000e: sget v2, LR$id;.email:I
0010: invoke-virtual {v3, v2}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
0013: move-result-object v2
0014: check-cast v2, Landroid/widget/TextView;

0016: if-nez v2, 001a

0018: const-string v1, "email"

001a: if-nez v1, 0022

001c: new-instance v1, LMainBinding;
001e: invoke-direct {v1, v3, v0, v2}, LMainBinding;.<init>:(Landroid/view/View;Landroid/widget/TextView;Landroid/widget/TextView;)V
0021: return-object v1

0022: new-instance v3, Ljava/lang/NullPointerException;
0024: const-string v0, "Missing required view with ID: "
0026: invoke-virtual {v0, v1}, Ljava/lang/String;.concat:(Ljava/lang/String;)Ljava/lang/String;
0029: move-result-object v0
002a: invoke-direct {v3, v0}, Ljava/lang/NullPointerException;.<init>:(Ljava/lang/String;)V
002d: throw v3
```

Since the `throw` statement was removed from the `if` check body, D8 no longer understands that they're exceptional cases. This means that the jumps in normal execution have returned. There's also a slight behavior change in that we now report the last missing view instead of the first.

![](/static/post-image/bytecode-economics-4.png)
<!--
digraph {
  ranksep=.2;
  nodesep=0;
  node [fontsize=10, height=.05,  fontname="menlo"];
  {
    node [shape=plaintext];
    edge [style=invis];
    "0000" -> "0008" -> "000a" -> "000d" -> "000e" -> "0016" -> "0018" -> "001a" -> "001c" -> "0022";
  }
  {
    node [shape=plaintext, width=2];
    edge [style=invis];
    "findViewById(name)" -> "if name == null" -> "missingId = \"name\"" -> "missingId = null" -> "findViewById(email)" -> "if email == null" -> "missingId = \"email\"" -> "if missingId == null" -> "return MainBinding(…)" -> "throw NPE(missingId)"
  }
  {
    edge [arrowsize=0.5];
    "findViewById(name)" -> "if name == null"[constraint=false];
    "if name == null" -> "missingId = \"name\""[constraint=false];
    "if name == null" -> "missingId = null"[constraint=false, margin="4,4"];
    "missingId = \"name\"" -> "findViewById(email)"[constraint=false];
    "missingId = null" -> "findViewById(email)"[constraint=false];
    "findViewById(email)" -> "if email == null"[constraint=false];
    "if email == null" -> "missingId = \"email\""[constraint=false];
    "if email == null" -> "if missingId == null"[constraint=false];
    "missingId = \"email\"" -> "if missingId == null"[constraint=false];
    "if missingId == null" -> "return MainBinding(…)"[constraint=false];
    "if missingId == null" -> "throw NPE(missingId)"[constraint=false];
  }
  { rank=same; "0000"; "findViewById(name)"; }
  { rank=same; "0008"; "if name == null"; }
  { rank=same; "000a"; "missingId = \"name\""; }
  { rank=same; "000d"; "missingId = null"; }
  { rank=same; "000e"; "findViewById(email)"; }
  { rank=same; "0016"; "if email == null"; }
  { rank=same; "0018"; "missingId = \"email\""; }
  { rank=same; "001a"; "if missingId == null"; }
  { rank=same; "001c"; "return MainBinding(…)"; }
  { rank=same; "0022"; "throw NPE(missingId)"; }
}
-->

The first thing that comes to my mind for trying to eliminate the needless jumps is nesting the conditionals.

```diff
 public static MainBinding bind(View root) {
-  String missingId = null;
+  String missingId;
   TextView name = root.findViewById(R.id.name);
-  if (name == null) {
-    missingId = "name";
-  }
-  TextView email = root.findViewById(R.id.email);
-  if (email == null) {
-    missingId = "email";
-  }
-  if (missingId == null) {
-    return new MainBinding(root, name, email);
+  if (name != null) {
+    TextView email = root.findViewById(R.id.email);
+    if (email != null) {
+      return new MainBinding(root, name, email);
+    } else {
+      missingId = "email";
+    }
+  } else {
+    missingId = "name";
   }
   throw new NullPointerException("Missing required view with ID: ".concat(missingId));
 }
```

Lo and behold, we've done it!

```
[000258] MainBinding.bind:(Landroid/view/View;)LMainBinding;
0000: sget v0, LR$id;.name:I
0002: invoke-virtual {v3, v0}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
0005: move-result-object v0
0006: check-cast v0, Landroid/widget/TextView;

0008: if-eqz v0, 001d

000a: sget v1, LR$id;.email:I
000c: invoke-virtual {v3, v1}, Landroid/view/View;.findViewById:(I)Landroid/view/View;
000f: move-result-object v1
0010: check-cast v1, Landroid/widget/TextView;

0012: if-eqz v1, 001a

0014: new-instance v2, LMainBinding;
0016: invoke-direct {v2, v3, v0, v1}, LMainBinding;.<init>:(Landroid/view/View;Landroid/widget/TextView;Landroid/widget/TextView;)V
0019: return-object v2

001a: const-string v3, "email"
001c: goto 001f

001d: const-string v3, "name"

001f: new-instance v0, Ljava/lang/NullPointerException;
0021: const-string v1, "Missing required view with ID: "
0023: invoke-virtual {v1, v3}, Ljava/lang/String;.concat:(Ljava/lang/String;)Ljava/lang/String;
0026: move-result-object v3
0027: invoke-direct {v0, v3}, Ljava/lang/NullPointerException;.<init>:(Ljava/lang/String;)V
002a: throw v0
```

Modulo a few register re-numberings, this is _exactly_ the same bytecode as the ideal case we crafted above. The key which makes this work is mostly in the `else` branches. Once an `else` branch is taken, it then immediately jumps down to the exception code because it's the last statement in the `if` branch in every layer above.

![](/static/post-image/bytecode-economics-5.png)
<!--
digraph {
  ranksep=.2;
  nodesep=0;
  node [fontsize=10, height=.05,  fontname="menlo"];
  {
    node [shape=plaintext];
    edge [style=invis];
    "0000" -> "0008" -> "000a" -> "0012" -> "0014" -> "001a" -> "001d" -> "001f";
  }
  {
    node [shape=plaintext, width=2];
    edge [style=invis];
    "findViewById(name)" -> "if name != null" -> "findViewById(email)" -> "if email != null" -> "return MainBinding(…)" -> "const-string \"name\"" -> "const-string \"email\"" -> "throw NPE(missingId)"
  }
  {
    edge [arrowsize=0.5];
    "findViewById(name)" -> "if name != null"[constraint=false];
    "if name != null" -> "const-string \"name\""[constraint=false];
    "if name != null" -> "findViewById(email)"[constraint=false, margin="4,4"];
    "findViewById(email)" -> "if email != null"[constraint=false];
    "if email != null" -> "const-string \"email\""[constraint=false];
    "if email != null" -> "return MainBinding(…)"[constraint=false];
    "const-string \"name\"" -> "throw NPE(missingId)"[constraint=false];
    "const-string \"email\"" -> "throw NPE(missingId)"[constraint=false];
  }
  { rank=same; "0000"; "findViewById(name)"; }
  { rank=same; "0008"; "if name != null"; }
  { rank=same; "000a"; "findViewById(email)"; }
  { rank=same; "0012"; "if email != null"; }
  { rank=same; "0014"; "return MainBinding(…)"; }
  { rank=same; "001a"; "const-string \"name\""; }
  { rank=same; "001d"; "const-string \"email\""; }
  { rank=same; "001f"; "throw NPE(missingId)"; }
}
-->

So are we done?

While we shouldn't care too much about how generated code looks, I still find this solution to be unsatisfactory. If you have 20 views in a layout you'll get 20 levels of nesting. Even though generated code isn't written by hand, you still might find yourself reading it when clicking through elements of a stacktrace or during debugging. As a result, if a more readable solution is available without sacrificing the value we should prefer it.

In order to flatten the generated code, we need a similar mechanism which allows control flow to jump to a particular point. This sounds awfully similar to a "goto", and it is, but _all_ control flow is a form of "goto" so we might as well use whatever the language provides. For Java, the `break` statement of a `switch` or loop comes to mind as something to try.

```diff
 public static MainBinding bind(View root) {
   String missingId;
-  TextView name = root.findViewById(R.id.name);
-  if (name != null) {
+  while (true) {
+    TextView name = root.findViewById(R.id.name);
+    if (name == null) {
+      missingId = "name";
+      break;
+    }
     TextView email = root.findViewById(R.id.email);
-    if (email != null) {
-      return new MainBinding(root, name, email);
-    } else {
+    if (email == null) {
       missingId = "email";
+      break;
     }
-  } else {
-    missingId = "name";
+    return new MainBinding(root, name, email);
   }
   throw new NullPointerException("Missing required view with ID: ".concat(missingId));
 }
```

By using a `return` statement as the last of the infinite loop, we never actually loop and instead just borrow the `break` feature. This is functionality equivalent to the previous version and it produces the exact same bytecode but without nesting.

Does a loop that doesn't actually loop offend your sensibilities? It certainly does for IntelliJ IDEA which produces a warning: "'while' loop does not loop". We could generate a suppression, but it would be nice to just use something else more suited for this case. There's actually one more construct where a `break` can be used: labeled blocks.

```diff
 public static MainBinding bind(View root) {
   String missingId;
-  while (true) {
+  missingId: {
     TextView name = root.findViewById(R.id.name);
     if (name == null) {
       missingId = "name";
-      break;
+      break missingId;
     }
     TextView email = root.findViewById(R.id.email);
     if (email == null) {
       missingId = "email";
-      break;
+      break missingId;
     }
     return new MainBinding(root, name, email);
   }
   throw new NullPointerException("Missing required view with ID: ".concat(missingId));
 }
```

Now this _really_ looks like a "goto", but the compiler will still validate that `missingId` is initialized in all execution paths that lead to the exception just like it did with `while (true)` and the nested `if`/`else`s. And, unsurprisingly, the bytecode remains the same.

This is the final form of this specific example of generated code as it stands right now. The bytecode size was reduced from 55 bytes to 31. The duplication was removed and the control flow is now tailored for all views being present. The source code actually got a little bit longer, but it's still very readable. The labeled block is admittedly something you don't see often and probably wouldn't use in manually written code unless it was for breaking across nested loops.

You don't need to dig this deep if you're building something that generates code. Start with generating a good API and producing correct behavior. All of this optimization can be done later, or even never. I get involved in this optimization because it's a fun exploration, but also because _the economics of generated code_ mean that the work almost always pays for itself.
