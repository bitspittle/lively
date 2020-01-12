# Lively

*Lively* is a library that makes it easy to define values and relationships between them.

A concrete example can help here. At a glance, *Lively* code looks something like this:

```kotlin
    val lively = Lively()

    val w = lively.source(640)
    val h = lively.source(480)

    // `area` automatically updates if `w` or `h` change
    val area = lively.observing { w.get() * h.get() }

    // Callback triggered if `w` or `h` change
    lively.sideEffect { screen.repaint(Color.BLACK, w.get(), h.get()) }
```

There are three core concepts demonstrated above:

**Source values**: `lively.source(...)` creates a value that doesn't depend on any other
values. They are created with an initial value but can be `set` with new values later.

`w` and `h` are source integers.

**Observing values**: `lively.observing { ... }` creates a value which depends on one or
more other live values. They cannot be `set` directly but always derive their values from the
*observing block*.

`area` is an observing integer, whose value will automatically be updated whenever `w`, `h`, or
both change.

Although it's not demonstrated above, observing values can depend on other observing values, in a
chain.

*Note: If an observing block doesn't actually query any other live values, then the result
won't ever change over time, and it might as well not be a live value.*

**Side effects**: `lively.sideEffect { ... }` is similar to the `observing` method, but it doesn't
produce a new live value. This is a useful place to handle external side effects that should occur
as a result of one or more live values changing.

## Defining relationships

Earlier, we claimed that *Lively* allows us to define relationships. We'll dive into that more in
this section. 

Imagine you had an editable `User` class, something created with some initial values but that can
further be modified:

```kotlin
class User {
    var firstName: String
    var lastName: String
    var nickname: String?
    var age: Int
    var country: Country
}
```

Other parts of your codebase might want to declare new values that are built on top of them. For
example:

```kotlin
val fullName = "${user.firstName} ${user.lastName}"
val displayName = user.nickname ?: fullName
val isDrinkingAge = (user.country == Country.US && user.age >= 21)
```

We'd also like these to update whenever any dependent `User` values change.

A common solution in many codebases is to add listeners:

```kotlin
class ListenableUser {
    val onFirstNameChanged: List<() -> Unit>
    var firstName: String

    val onLastNameChanged: List<() -> Unit>
    var lastName: String
}

val user = ListenableUser()
var fullName = "${user.firstName} ${user.lastName}"
user.onFirstNameChanged += { fullName = "${user.firstName} ${user.lastName}" }
user.onLastNameChanged += { fullName = "${user.firstName} ${user.lastName}" }

val displayName = user.nickname ?: fullName
user.onNicknameChanged += { displayName = user.nickname ?: fullName }
user.onFirstNameChanged += { displayName = user.nickname ?: fullName }
user.onLastNameChanged += { displayName = user.nickname ?: fullName }
```

It works, and there's a lot of UI code out in the wild that looks like this! But there's so much
boilerplate -- it's easy to make a copy/paste mistake, and if requirements change and you ever need
to update the logic, it can be easy to miss a location (especially if the initialization code and
update code aren't in the same place). Listeners also make it easy to accidentally introduce memory
leaks, so you also have to be careful!

### Live fields

With *Lively*, handling all this is quite a bit easier. To get started, simply upgrade your
primitive types to `Live` types. Our `User` class *almost* looks the same:

```kotlin
class User {
    val firstName: Live<String>
    val lastName: Live<String>
    val nickname: Live<String?>
    val age: Live<Int>
    val country: Live<Country>
}
```

Next, let's work on the code that wants to depend on these `User` fields.

Here, we instantiate a `Lively` instance (more on this in the next section), and then we simply
create some observing live values:

```kotlin
    val user = User()
    val lively = Lively()

    val fullName = lively.observing { "${user.firstName.get()} ${user.lastName.get()}"}
    val displayName = lively.observing { user.nickname.get() ?: fullName.get() }
```

Done! That's it.

As a bonus, your code won't waste time firing unnecessary updates. For example, if you edit both
first and last names in the same frame, `fullName` will only be updated once. Also, if `nickname`
is set, then no matter how often you change `fullName`, the `displayName` observing block will not
get triggered.

## Creating a Lively instance

One might ask, "Why do we even need to create a `Lively` instance? Why can't we just create `Live`
values directly?"

The short answer is that a `Lively` instance encapsulates access to an underlying dependency graph
(which, as a user, you'll never interact with directly). It also helps with memory management, as
we'll see shortly.

A `Lively` instance does not represent the whole graph but just a subset of it. In this way, it's
easy to allow independent parts of your codebase to create, manage, and reason about their own
local group of live values without worrying about the global picture.

A common pattern can help demonstrate this. When writing UIs, you benefit a lot by separating the
view and data layers:

```kotlin
// In a "data" module
class CharacterData {
  private val lively = Lively()
  val name = lively.sourceString()
  val hp = lively.sourceInt()
  val stats = lively.source(Stats())
}

// In a "view" module, which depends on "data"
class CharacterViewer(data: CharacterData) {
  private val lively = Lively()
  val nameLabel = lively.observing { data.name.get().toUpperCase() }
  val hpLabel = lively.observing { data.hp.get().toString() }

  val strengthLabel = lively.observing { data.stats.get().strength }
  val dexterityLabel = lively.observing { data.stats.get().dexterity }
}
```

Note that each class in the above scenario created their own `Lively` instances. This is especially
useful because the lifetime of `CharacterViewer` will often be much shorter than the lifetime of
the `CharacterData` class. Presumably `CharacterViewer` will get garbage collected first, and at
that time, so will all of its live values.

*Note: You can also more aggressively indicate you are finished with live values without waiting
for the garbage collector to run. We'll discuss this more in the section on freezing.*

If we were using a traditional listener model here, we might accidentally leak `CharacterViewer`
because it's easy to register listeners against `CharacterData` fields that could keep the viewer's
`this` reference alive.

And finally, recall that `Lively` exposes the concept of *side effects*. By having users get used
to using a `Lively` instance to create and manage `Live` values, it becomes an intuitive location
to create and manage side effects as well.

### Lively executor

*Lively* doesn't work right out of the box -- it needs to be initialized. What's missing is an
executor it uses to run graph updates on.

If you want to skip this section for now, you can just set *Lively* up with an immediate executor,
even if you don't really understand what that means. For those interested, I'll explain more in the
rest of this section.

```kotlin
fun main() {
  Lively.executor = RunImmediatelyExecutor()
  // You can now use Lively safely from this point on
}
```

If you don't initialize an executor, then when you try to interact with Lively, it will throw an
exception with a descriptive error message (that will hopefully get some users to read this
section).

So, what the heck is this all about?

*Lively* works best if it can delay doing some operations. This allows it to collapse redundant
requests, which can matter if those requests end up running expensive logic. It also helps it
avoid triggering unnecessary updates.

Take the following code for example, which performs a file save automatically if any values change:

```kotlin
// This example is somewhat contrived but hopefully illustrates the point

class Config {
  private val lively = Lively()

  val resolution = lively.source(Resolution.ULTRA_HIGH)
  val vsyncEnabled = lively.source(true)
  val shadowsEnabled = lively.source(true)
  // ... and many more

  lively.sideEffect {
    File("config.txt").printWriter().use { out ->
      out.println(resolution.get())
      out.println(vsyncEnabled.get())
      out.println(shadowsEnabled.get())
      // ... and many more
    }
  }
```

Say that you, in a single frame, change 20 config values all at once. You would only want that to
cause a single file write, not 20 separate calls that tear down and build up the file each time. By
specifying an executor that waits until the end of a frame to run, *Lively* will be able to collapse
all 20 changes into one.

In other words, given the following dependency graph:

```
A <--┐
B <--┼-- D
C <--┘

# Note: 'D' can be another live value or a side effect
```

then, if we change A, then B, then C in a single frame, we'd get the following behaviors based on
the executor we used:

```
Run immediately executor:
A' -> D'
B' -> D''
C' -> D'''

Run later executor:
A' -> postponed
B' -> postponed
C' -> postponed
// sometime later...
(A', B', C') -> D'
```

*Note: Some might ask, why not just initialize Lively with a `RunImmediatelyExecutor` and let the
user update it to something more efficient only if they want to? This is a decision I might revisit
later, but at the moment, I'm worried about the discoverability of this feature, so I'm triggering
a "fail fast" path for now, ensuring users can make an intentional decision here.*

For additional information about executors, see the "Custom executor demo" section at the end of
this document.

## getSnapshot vs get

`Live#get` represents the concept of querying a `Live`'s value over time. In earlier examples, we
already demonstrated calling `get`, but we'll repeat those again here for your convenience:

```kotlin
val area = lively.observing { w.get() * h.get() }
lively.sideEffect { screen.repaint(Color.BLACK, w.get(), h.get()) }
```

`Live` also has a `getSnapshot` method, which just returns its current value without any regard to
future changes.

```kotlin
val source = lively.source(123)
val snapshot = source.getSnapshot()
source.set(456)          // Even though we updated `source`...
assert (snapshot == 123) // `snapshot` won't ever get updated
```

Why have both methods? Why would you ever call `getSnapshot` when you could just call `get`
instead?

The answer is that `get` is *only* available inside an observing block (it's a context-sensitive
extension method):

```kotlin
val source = lively.source(123)
// val doubled = source.get() * 2 <-- Compile error!! `get` doesn't exist here
val doubled = lively.observing { source.get() * 2 } // This is OK
val doubledSnapshot = source.getSnapshot() * 2      // Also OK (but doesn't update over time)
```

The purpose of this API is to make it easy to express your intention, whether you are interested in
 observing a live value over time or just for a moment in time.

*Note: `get` is actually the magic sauce that allows Lively to dynamically update its dependencies
behind the scenes as values change - all calls to `get` are recorded each time an observing block
is run.*

The different names are also hopefully easy to read at a glance. For example, this code would
compile, but it's hoped that it wouldn't pass code review:

```kotlin
val liveInt = lively.source(123)
val doubled = lively.observing { liveInt.getSnapshot() * 2}
//               get() was intended here ^^^^^^^^^^^^^

assert (doubled.getSnapshot() == 456) // `doubled` is initialized correctly, but...
liveInt.set(10)                       // it won't get updated after the source value changes
```

In short, with live values, you almost always want to call `get`, but `getSnapshot` is useful when
you need to query a `Live` instance outside of an observing context.

## Freezing values

`Live` instances can be frozen anytime, which is a way of indicating that, at this point onward,
you expect its value to never change again. If you freeze a *source live* and then call `set`, it
will throw an exception.

Although live values will be garbage collected eventually if you remove all references to them,
freezing is still recommended when possible, because

1. it allows your code to fail fast if some code path tries to update a `Live` unexpectedly later.
1. it can result in *immediate* releasing of dependent resources.

For this second point, imagine you have the following dependency chain:

```
"<--" means "depends on"

A <-- B <-- C <-- D
```

Here, if you change `A`, then `D` may also change, so the graph needs to keep track of all
intermediate relationships. But if you freeze `A`, then the graph realizes `B` has no dependencies,
so it also gets frozen, all the way up the chain. In other words, freezing `A` locks down the whole
chain, allowing many nodes to be removed from the graph automatically.

In addition to freezing one `Live` at a time, `Lively` instances also provide a `freeze` method
that freezes *all* the live values that were created by it.

```kotlin

class SomeUiDialog {
  private val lively = Lively()
  private val label1 = lively.create()
  private val label2 = lively.create()
  // ... and many more ...
  private val label9 = lively.create()

  fun dispose() {
    // Freeze label1 through label9 all at once
    // Freezing isn't strictly necessary, but it's a recommended practice when possible
    lively.freeze()
  }
}
```

## Live mutability

Most of the time, a creator of a live value will only want to expose it to external callers with an
immutable API. To accomplish this with `Lively`, you should be aware of the various ways to expose
a live value.

* `Live<T>`: A common choice. This API is the most restrictive. Provides `get` and `getSnapshot`,
  and does not allow `freeze`.
* `SourceLive<T>`: Returned by `Lively#source`. This is a super mutable API. Provides both `set`
   and `freeze` capabilities in addition to getters.
* `ObservingLive<T>`: Returned by `Lively#observing`. Indicates this value is intermediate,
  completely dependent on other live values. Provides `freeze` in addition to getters.
* `FreezableLive<T>`: Same immutable API as `Live<T>` but allows calls to `freeze`. It's not
  expected that most users will need to use this.

Note that the API for `ObservingLive` and `FreezableLive` are identical (at least at the time of
writing this), but `FreezableLive` can be a useful way to collect both `SourceLive` and
`ObservingLive` instances into a single collection, as they both implement the interface.

A data class might look something like the following, if all modifications to live values can happen
inside the class so only immutable APIs can be exposed:

```kotlin
class User {
  private val lively = Lively()

  private val _firstName = lively.sourceString()
  val firstName: Live<String> get() = _firstName

  private val _lastName = lively.sourceString()
  val firstName: Live<String> get() = _lastName

  private val _fullName = lively.observing { "${firstName.get()} ${lastName.get()" }
  val fullName: Live<String> get() = _fullName

  fun randomizeName() {
    _firstName.set(FIRST_NAMES.random())
    _lastName.set(LAST_NAMES.random())
  }
}
```

Another useful pattern is to create an immutable interface and mutable implementation, allowing
some parts of your codebase write-access to your live values and others only read-access:

```kotlin
interface ImmutableUser {
  val firstName: Live<String>
  val lastName: Live<String>
}

class User : ImmutableUser {
  private val lively = Lively()

  // Types here are `SourceLive<String>`, not `Live<String>`!
  override val firstName = lively.sourceString()
  override val lastName = lively.sourceString()

  fun randomizeName() {
    firstName.set(FIRST_NAMES.random())
    lastName.set(LAST_NAMES.random())
  }
}

fun createUser(firstName: String, lastName: String): ImmutableUser {
    return User().apply {
        this.firstName.set(firstName)
        this.lastName.set(lastName)
    }
}
```

## Thread (un)safety

*Lively* is not thread safe. It is expected that all live value interactions happen on the same
thread, and an exception will be thrown if there is ever an attempt to mutate a live value from a
different thread.

*Note: `getSnapshot` can be called from any thread, although the value you get may be stale.*

That being said, it is possible to create *Lively* graphs on separate threads, as long as each
graph is contained within and only modified on its own thread.

As a technical note, when you call `Lively()` for the first time on a thread, it creates a
backing graph automatically on that thread using a `ThreadLocal`. Subsequent `Lively()` invocations
will share the same graph.

## Listeners

In normal use of the *Lively* library, you can do almost everything with just `Lively#source`,
`Lively#observing`, and `Lively#sideEffect`. However, for a handful of edge-cases, the `Live` class
also exposes two listeners:

* `onValueChanged`: Fired immediately whenever the live value is updated.
* `onFroze`: Fired immediately after the live value was frozen.

It should be rare to need to use either of these listeners, but they might be useful occasionally,
such as for debugging a dependency tangle where some value is getting updated and you're not sure
why.

Listeners also enable two-way bindings, which is discussed in the next subsection.

If you use listeners, **be careful**, as they enable you to add infinite, cyclical relationships
normally impossible without them:

```kotlin
fun `impossible to add cycles without listeners`() {
    val liveInt1 = lively.source(123)
    val liveInt2 = lively.observing { liveInt1.get() }
    val liveInt3 = lively.observing { liveInt2.get() }

    // No way to get liveInt1 to retroactively depend on liveInt3!
    liveInt1.set(234) // liveInt3 --> 234, this is fine
}

fun `possible to add cycles using listeners`() {
    val liveInt1 = lively.source(123)
    val liveInt2 = lively.observing { liveInt1.get() }
    val liveInt3 = lively.observing { liveInt2.get() }

    val liveInt3.onValueChanged += { liveInt1.set(liveInt3.getSnapshot() + 111) }

    // Uh oh....
    liveInt1.set(234)
    // liveInt3 --> 234, calls liveInt1.set(345)
    // liveInt3 --> 345, calls liveInt1.set(456)
    // liveInt3 --> 456, ...
}
```

### Two-way bindings (same types)

Although most live value relationships are one-way (that is, one value depends on one or more other
values which don't depend on it back), very occasionally it is useful to specify a two-way
relationship.

For example, imagine you have a `SourceLive<String>` which represents a user's name, and a UI text
input which is backed by a `SourceLive<String>` itself.

*Note: The `swing` module, discussed briefly in the "Swing demo" section, shows one possible way
that a codebase might convert a text input into a `SourceLive<String>`.*

You want to initialize the text entry with the user's name, but after that, any changes to the text
input should be reflected back into the name. The `Live#bindTo` method is provided for this purpose:

```kotlin
class User {
  val name: SourceLive<String>
}

val nameInput: SourceLive<String> = ...
nameInput.bindTo(user.name)

// Later, to release the binding, freeze one of the values
nameInput.freeze()
```

In the above code snipped, `nameInput` will be initially set to whatever the user's name is, and
after that, changing either value will affect the other.

It's important to state that `bindTo` does *not* use the normal dependency graph, but rather
bypasses it, hooking up listeners between the two live values. Calling `bindTo` between more than
two live values may result in unpredictable behavior.

### Two-way bindings (different types)

You won't always have the luxury of binding two live values of the same type together. For example,
let's tweak the above scenario a bit -- instead of binding some text input to a user's name, we want
to bind a text input to a user's weight.

If the user types a valid decimal value into the text input, the weight value should stay in sync,
but if they type an invalid value (e.g. "1.234.567", or "123abc"), the weight value should just keep
its last good state.

There's a second version of `Live#bindTo` for this purpose, which additionally takes two conversion
methods, to convert to and from the different types. These conversion methods are allowed to return
`null`, and if either do, the values are de-synced.

```kotlin
class User {
  val weight: SourceLive<Float>
}

val weightInput: SourceLive<String> = ...
weightInput.bindTo(
    user.name,
    { strValue -> strValue.toFloatOrNull() },
    { floatValue -> floatValue.toString() })

// Later...
weightInput.freeze()
```

## Demos

### Swing demo

A common use-case for a library like *Lively* is binding data models to UI views.

To showcase what such code could like, this project includes both `swing` and `swing.demo` modules.

The `swing` module demonstrates how to wrap Swing properties with `Live` values (for example,
representing the `JCheckBox#isSelected` state as a `Live<Boolean>`). Take a look at `JCheckBox.kt`
and the extension method `Lively.wrapSelected(checkBox: JCheckBox): SourceLive<Boolean>` it defines.

The `swing.demo` module presents isolated UI scenarios that highlight how to accomplish various
goals using *Lively*.

### Custom executor demo

If you aren't building on top of an existing framework that has support for taking over a thread
with its own event poller, then you'll have to create your own executor logic from scratch. This
demo serves the purpose of showing a very simple executor implementation that you might base a more
complex executor upon.

## Special thanks

I owe so much of this library to [sxenos](https://github.com/sxenos). The implementation here is
mine, but the idea and initial demonstration of this concept was his. I've learned a lot from him,
even in the relatively short time we knew each other.
