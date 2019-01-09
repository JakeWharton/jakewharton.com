---
title: Inline Classes Make Great Database IDs
layout: post

categories: post
tags:
- Android
- Kotlin
- SQLDelight
- Database
---

Kotlin 1.3's experimental [inline class](https://kotlinlang.org/docs/reference/inline-classes.html) feature allows creating type-safe, semantic wrappers around values which are erased at runtime. Database IDs are a perfect use case for this functionality. Combined with [SQLDelight](https://github.com/square/sqldelight) which automatically generates model objects and APIs for querying, different table's IDs become different types which prevent erroneous use.

In modeling an app that sends payments, the domain includes customers, instruments (like debit cards and bank accounts), and payments. These otherwise would all have their IDs represented by a `Long` allowing programming bugs such as passing a payment ID as a customer ID to go undetected.

Instead, define an `inline class` for each ID around a `Long` (or whatever your ID type is).

```kotlin
package com.example.db

inline class CustomerId(val value: Long)
inline class InstrumentId(val value: Long)
inline class PaymentId(val value: Long)
```

When defining your schema, tell SQLDelight to use these types for the ID columns.

```sql
-- src/main/sqldelight/com/example/db/Customer.sq
CREATE TABLE customer(
  id INTEGER AS CustomerId PRIMARY KEY,

  -- other columns…
);

-- src/main/sqldelight/com/example/db/Instrument.sq
CREATE TABLE instrument(
  id INTEGER AS InstrumentId PRIMARY KEY,

  -- other columns…
);
```

_(Just like when specifying any other Kotlin type for a column, you will need to register a `ColumnAdapter` for these types)_

The payment table also uses these types for its own ID as well as the foreign key IDs to other tables.

```sql
-- src/main/sqldelight/com/example/db/Payment.sq
CREATE TABLE payment(
  id INTEGER AS PaymentId PRIMARY KEY,

  sender_id INTEGER AS CustomerId NOT NULL,
  recipient_id INTEGER AS CustomerId NOT NULL,
  instrument_id INTEGER AS InstrumentId NOT NULL,

  -- other columns…

  FOREIGN KEY(sender_id) REFERENCES customer(id),
  FOREIGN KEY(recipient_id) REFERENCES customer(id),
  FOREIGN KEY(instrument_id) REFERENCES instrument(id)
);
```

*(Note: SQLDelight will [soon enforce](https://github.com/square/sqldelight/issues/1138) that these `FOREIGN KEY` relationships use the same type so that you can't mix them up)*

Named queries whose arguments or selected columns reference these IDs will now automatically use these types.

```sql
paymentsBySender:
SELECT id
FROM payment
WHERE sender_id = ?;
```

The generated Kotlin signature for this query accepts a `CustomerId` and the query returns `PaymentId`s as expected.

```kotlin
fun paymentsBySender(sender_id: CustomerId): Query<PaymentId> {
  // …
}
```

If you're already looking at a single payment in this app, you might want to fetch all payments sent from that sender. With a reference to the current `Payment` object, you can invoke the named query to get the list.

```kotlin
val payment: Payment = // …

val bySender = queries.paymentsBySender(payment.id).executeAsList()
```

Before using inline classes, this code would have compiled and returned an empty list at runtime because of programmer error. The `Payment`'s own `id` was erroneously supplied for the sender ID instead of the `sender_id`.

But because inline classes were used, this mistake can be caught at compile-time.

```
PaymentPresenter.kt:189:43: error: type mismatch: inferred type is PaymentId but CustomerId was expected
  val bySender = queries.paymentsBySender(payment.id).executeAsList()
                                          ^
```

After defining and using the inline classes in the schema once, this extra validation is effectively free because SQLDelight generates both the `Payment` model object and the function for the query.

A quick fix to pass `sender_id` allows the code to compile and also reflect the original intended behavior.

```diff
 val payment: Payment = // …
 
-val bySender = queries.paymentsBySender(payment.id).executeAsList()
+val bySender = queries.paymentsBySender(payment.sender_id).executeAsList()
```

When moving data around inside the database domain, the use of inline classes can prevent using semantically incorrect IDs. When combined with SQLDelight, these inline classes automatically apply to all of your models and query arguments adding an additional layer of safety to your database interaction. Enjoy!