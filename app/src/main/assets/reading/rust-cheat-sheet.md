# The Rust Cheat Sheet: Ownership to Oxidation

A reference doc, forged with zero-cost abstractions and zero patience for dangling pointers. Bookmark it — the borrow checker will still yell at you sometimes, but at least you'll know why.

---

## 1. Hello World & Project Basics

```rust
fn main() {
    println!("Hello, World!");
}
```

- `fn main()` is the entry point — no `package main` ceremony, just the function itself.
- `println!` has a `!` because it's a **macro**, not a function — it expands at compile time into actual formatting code. Macros are Rust's polite way of saying "this looks like a function call but is doing something cleverer under the hood."
- No garbage collector, no runtime VM — Rust compiles straight to native machine code, same category of artifact as C or Go.

```bash
cargo new my_project     # scaffolds a new project with Cargo.toml + src/main.rs
cargo run                 # compile + run
cargo build                # compile only
cargo build --release      # compile with full optimizations, slower build, faster binary
cargo check                 # type-check without producing a binary — fast feedback loop
```

`cargo` is Rust's build tool, package manager, and test runner rolled into one — the `go` command's counterpart, minus the identity crisis of also being called `gc`.

---

## 2. Variables, Mutability & Constants

```rust
let x = 5;              // immutable by default — this is the Rust way
let mut y = 5;           // explicitly mutable
y = 6;                    // fine, y is mut

const MAX_POINTS: u32 = 100_000;   // always immutable, type annotation required, SCREAMING_CASE by convention

let x = 5;
let x = x + 1;             // shadowing — a NEW binding, not a mutation, can even change type
let x = "now I'm a string";
```

**Immutable by default is the whole personality of the language in one keyword.** Every other language defaults to mutable and makes you opt into safety. Rust defaults to safety and makes you opt into danger with `mut`, loudly, on purpose, so every mutable variable in a codebase is visibly flagged as "pay attention here."

---

## 3. Basic Types

```rust
// integers
i8, i16, i32, i64, i128, isize     // signed
u8, u16, u32, u64, u128, usize     // unsigned

// floats
f32, f64

// other primitives
bool
char                                 // a full Unicode scalar value, 4 bytes — not a byte like C's char

let a: i32 = 42;
let b: f64 = 3.14;
let c: bool = true;
let d: char = 'R';
```

- `i32` is the default integer type if you don't specify — reach for it unless you have a reason not to.
- `usize`/`isize` are pointer-width — used for indexing, sizes, anything tied to memory addressing.
- Integer overflow **panics** in debug builds and silently wraps in release builds — a deliberate, slightly uncomfortable tradeoff between safety during development and speed in production.

---

## 4. Operators

```rust
+ - * / %                // arithmetic
== != < > <= >=            // comparison
&& || !                     // logical
& | ^ << >>                  // bitwise
```

No `++`/`--` at all — Rust looked at C's favorite footgun-in-a-loop and just declined to include it. Write `x += 1` like a responsible adult.

No ternary operator either, same as Go — but `if` is an **expression** in Rust, so you get the ternary's job done differently:

```rust
let status = if age >= 18 { "adult" } else { "minor" };
```

---

## 5. Control Flow

### if / else — condition needs no parens, braces mandatory, and it's an expression

```rust
let number = 7;

if number < 5 {
    println!("small");
} else if number < 10 {
    println!("medium");
} else {
    println!("large");
}

let x = if number % 2 == 0 { "even" } else { "odd" };  // if as an expression
```

### Loops — three flavors, one keyword each

```rust
loop {                        // infinite, breaks with a value
    println!("forever, until told otherwise");
    break;
}

let result = loop {
    counter += 1;
    if counter == 10 {
        break counter * 2;     // loop can RETURN a value via break
    }
};

while condition {
    // classic while
}

for x in 0..5 {                 // range, exclusive of 5
    println!("{x}");
}
for x in 0..=5 {                 // inclusive range
    println!("{x}");
}
for item in collection.iter() {
    println!("{item}");
}
```

`loop` returning a value via `break` is a genuinely nice touch nobody asks for until they need it — retry logic that needs to hand back the eventual successful result is dramatically cleaner than the equivalent in most languages.

### match — Go's switch went to the gym and came back exhaustive

```rust
let number = 3;

match number {
    1 => println!("one"),
    2 | 3 => println!("two or three"),         // multiple patterns
    4..=9 => println!("four through nine"),      // range pattern
    _ => println!("something else"),               // catch-all, MANDATORY unless every case is covered
}
```

`match` must be **exhaustive** — the compiler refuses to build if you haven't accounted for every possible value, and will tell you exactly which ones you missed. This is not a suggestion. This is Rust standing at your desk with crossed arms until you handle the case you were hoping nobody would notice you skipped.

---

## 6. Functions

```rust
fn add(a: i32, b: i32) -> i32 {
    a + b   // no semicolon = this is the return value (an expression, not a statement)
}

fn greet(name: &str) {
    println!("Hello, {name}!");
}

fn swap(a: i32, b: i32) -> (i32, i32) {   // tuple for multiple returns
    (b, a)
}

let (x, y) = swap(1, 2);
```

**The missing semicolon is not a typo, it's the whole point.** A line without a trailing `;` is an *expression*, and the value of the last expression in a block is what that block evaluates to. Add a semicolon and you turn it into a *statement* that evaluates to nothing (`()`, the unit type) — which is exactly the classic beginner bug of writing `a + b;` and getting a confusing type-mismatch error, because you accidentally told the function to return nothing.

```rust
let add = |a: i32, b: i32| a + b;   // closure

fn apply<F: Fn(i32) -> i32>(f: F, x: i32) -> i32 {
    f(x)
}
```

---

## 7. Ownership — The Whole Reason You're Here

This is Rust's actual headline feature, the thing no other mainstream language does quite like this, and the reason the compiler feels like it's personally invested in your code's wellbeing.

**Three rules, and the entire language's safety story rests on them:**

1. Each value has exactly one **owner**.
2. When the owner goes out of scope, the value is dropped (freed) — automatically, deterministically, no garbage collector involved.
3. Ownership can be **moved** or **borrowed**, but never silently duplicated.

```rust
let s1 = String::from("hello");
let s2 = s1;               // s1 is MOVED into s2 — s1 is no longer valid
// println!("{s1}");        // compile error: value borrowed after move

let s3 = s2.clone();        // explicit deep copy — now both s2 and s3 are valid, independently
```

This is the part that trips up literally everyone coming from a garbage-collected language on day one — assignment doesn't copy by default for heap-allocated types, it **transfers ownership**, and the old variable becomes unusable. It feels hostile for about a week and then starts feeling like the compiler catching a use-after-free bug before it ever had the chance to exist.

Types that are small and live entirely on the stack (integers, floats, `bool`, `char`, and tuples of those) implement `Copy` instead of moving — assigning them duplicates the value cheaply, no ownership drama:

```rust
let x = 5;
let y = x;      // x is COPIED, not moved — both are valid, x didn't disappear
println!("{x} {y}");   // totally fine
```

### Borrowing — using a value without taking ownership of it

```rust
fn calculate_length(s: &String) -> usize {   // borrows s, doesn't own it
    s.len()
}

let s1 = String::from("hello");
let len = calculate_length(&s1);    // pass a reference
println!("{s1} has length {len}");   // s1 still valid — it was only borrowed
```

**The borrowing rules, enforced entirely at compile time, zero runtime cost:**

- You can have **any number of immutable references** (`&T`) at once.
- OR exactly **one mutable reference** (`&mut T`) — never both kinds at the same time.
- References must always be valid — no dangling references, ever, checked statically.

```rust
let mut s = String::from("hello");

let r1 = &s;
let r2 = &s;          // fine — multiple immutable borrows
println!("{r1} {r2}");

let r3 = &mut s;       // fine, AFTER r1/r2 are done being used
r3.push_str(" world");
```

This is the mutex from the Go series, except instead of a runtime lock you fight with at 2 AM after a bug report, it's a compile-time proof you fight with at 2 PM before the code even runs. Data races become, structurally, impossible to compile — not "unlikely," not "caught by a race detector if you're lucky," actually impossible to express in a program that builds.

---

## 8. Structs & Methods

```rust
struct Person {
    name: String,
    age: u32,
}

let p = Person {
    name: String::from("Alice"),
    age: 30,
};

println!("{}", p.name);
```

### impl blocks — methods live separately from the struct definition

```rust
impl Person {
    fn new(name: &str, age: u32) -> Person {     // associated function, not a method (no &self)
        Person { name: String::from(name), age }
    }

    fn greet(&self) -> String {                    // method — borrows self immutably
        format!("Hi, I'm {}", self.name)
    }

    fn birthday(&mut self) {                        // method — borrows self mutably
        self.age += 1;
    }
}

let mut p = Person::new("Bob", 25);
println!("{}", p.greet());
p.birthday();
```

`Person::new` is Rust's constructor pattern — there's no dedicated constructor syntax, just a convention of writing an associated function (no `self` parameter) called `new`, called with `::` instead of `.`.

### Tuple structs & unit structs

```rust
struct Point(f64, f64);              // fields accessed as .0, .1
struct Marker;                        // no fields at all — a type that just exists
```

---

## 9. Enums & Pattern Matching — Where Rust Genuinely Shows Off

```rust
enum IpAddr {
    V4(u8, u8, u8, u8),
    V6(String),
}

let home = IpAddr::V4(127, 0, 0, 1);

match home {
    IpAddr::V4(a, b, c, d) => println!("{a}.{b}.{c}.{d}"),
    IpAddr::V6(addr) => println!("{addr}"),
}
```

Rust enums aren't C-style integer labels — each variant can carry its own distinct data, structurally. Combined with exhaustive `match`, this is how Rust solves entire categories of bugs other languages solve with `null` and a prayer.

### Option — Rust's answer to "this might not have a value" (no null, anywhere, ever)

```rust
enum Option<T> {
    Some(T),
    None,
}

fn find_user(id: u32) -> Option<String> {
    if id == 1 {
        Some(String::from("Alice"))
    } else {
        None
    }
}

match find_user(1) {
    Some(name) => println!("found: {name}"),
    None => println!("not found"),
}

// or, more idiomatically for the common cases:
let name = find_user(1).unwrap_or(String::from("unknown"));
if let Some(name) = find_user(1) {
    println!("found: {name}");
}
```

**Rust has no `null`.** Full stop. Every possibly-absent value is modeled explicitly as `Option<T>`, and the compiler forces you to handle the `None` case before you're allowed to touch the value inside `Some`. The entire category of null-pointer-dereference bugs — the one Tony Hoare himself called his "billion dollar mistake" — is compiled out of existence, not caught at runtime, not linted against, structurally unrepresentable.

### Result — Rust's answer to errors (no exceptions here either)

```rust
enum Result<T, E> {
    Ok(T),
    Err(E),
}

fn divide(a: f64, b: f64) -> Result<f64, String> {
    if b == 0.0 {
        Err(String::from("division by zero"))
    } else {
        Ok(a / b)
    }
}

match divide(10.0, 0.0) {
    Ok(result) => println!("result: {result}"),
    Err(e) => println!("error: {e}"),
}

// the ? operator — propagate an error upward, one character, no boilerplate
fn calculate() -> Result<f64, String> {
    let result = divide(10.0, 2.0)?;    // returns early with the Err if it's an Err
    Ok(result * 2.0)
}
```

Same philosophy as Go's `(result, error)` tuple — errors are values, checked explicitly — but Rust bakes it into the type system as `Result<T, E>` and gives you the `?` operator to propagate failures upward without writing `if err != nil` by hand every single time. It's Go's error philosophy with the ceremony surgically removed.

---

## 10. Vectors, Strings & Slices

### Vec<T> — the growable array you'll use constantly

```rust
let mut v: Vec<i32> = Vec::new();
v.push(1);
v.push(2);
v.push(3);

let v2 = vec![1, 2, 3];             // macro shorthand

let third = v[2];                    // panics if out of bounds
let third: Option<&i32> = v.get(2);   // safe — returns None instead of panicking

for i in &v {
    println!("{i}");
}
```

### Slices — a borrowed view into a sequence

```rust
let v = vec![1, 2, 3, 4, 5];
let slice = &v[1..3];    // [2, 3] — borrows, doesn't own, doesn't copy

let s = String::from("hello world");
let hello = &s[0..5];     // "hello" — string slice, &str
```

### Strings — genuinely two types, and it matters

```rust
let s1: String = String::from("hello");   // owned, growable, heap-allocated
let s2: &str = "hello";                     // borrowed string slice, often a literal

let mut owned = String::from("hello");
owned.push_str(", world");
owned.push('!');

let combined = s1 + " " + s2;    // String + &str works; consumes s1
let formatted = format!("{s1} {s2}");   // doesn't consume anything, just builds a new String
```

`String` is the owned, mutable, growable one (comparable to Go's `strings.Builder` output). `&str` is a borrowed view into string data that lives somewhere else — a literal baked into the binary, or a slice of a `String`. Function signatures overwhelmingly prefer `&str` for parameters (you're just reading it) and `String` for things you actually own and intend to keep or mutate.

---

## 11. Traits — Interfaces, but the Compiler Actually Checks Your Work

```rust
trait Shape {
    fn area(&self) -> f64;
    fn describe(&self) -> String {          // default implementation — optional to override
        format!("A shape with area {:.2}", self.area())
    }
}

struct Circle {
    radius: f64,
}

impl Shape for Circle {                       // explicit — unlike Go, you DO declare intent
    fn area(&self) -> f64 {
        std::f64::consts::PI * self.radius * self.radius
    }
}

fn print_area(shape: &impl Shape) {            // accepts anything implementing Shape
    println!("{}", shape.describe());
}
```

Unlike Go's implicit, structural interfaces, Rust traits are **explicit** — you write `impl Shape for Circle` on purpose, so a type's trait implementations are always grep-able and never accidental. You also get default method bodies for free, which Go's interfaces don't offer at all.

```rust
fn largest<T: PartialOrd>(list: &[T]) -> &T {   // generic function with a trait bound
    let mut largest = &list[0];
    for item in list {
        if item > largest {
            largest = item;
        }
    }
    largest
}
```

---

## 12. Generics

```rust
fn largest<T: PartialOrd>(list: &[T]) -> &T { /* ... */ }

struct Point<T> {
    x: T,
    y: T,
}

impl<T> Point<T> {
    fn x(&self) -> &T {
        &self.x
    }
}

enum Option<T> { Some(T), None }    // yes, Option itself is generic — you've been using generics this whole time
```

Rust's generics are **monomorphized** at compile time — the compiler generates a specialized, concrete version of the function or struct for every type it's actually used with, so there's zero runtime cost for the abstraction. This is a genuinely different tradeoff from Go's generics (which use a mix of stenciling and dictionary-passing) or Java's type erasure — Rust pays the cost at compile time (larger binaries, slower builds) in exchange for paying nothing at runtime.

---

## 13. Error Handling, panic!, and unwrap

```rust
fn might_fail(x: i32) -> Result<i32, String> {
    if x < 0 {
        return Err(String::from("negative input"));
    }
    Ok(x * 2)
}

let value = might_fail(5).unwrap();          // panics on Err — fine for prototypes, risky in production
let value = might_fail(5).expect("should have been positive");   // panics with a custom message
let value = might_fail(5).unwrap_or(0);        // falls back to a default instead of panicking

fn process() -> Result<i32, String> {
    let value = might_fail(5)?;                  // propagate the error upward, cleanly
    Ok(value + 1)
}
```

`unwrap()` and `expect()` are the "I am extremely confident this can't actually fail, and if I'm wrong I want to know immediately and loudly" tools — genuinely fine in a quick script, a genuine liability left unguarded in a production service. `panic!` itself unwinds the stack (or aborts, depending on build config) and is meant for truly unrecoverable programmer-error situations, same spirit as Go's `panic`/`recover`, just with less of a safety net expected around it by convention.

---

## 14. Lifetimes — The Concept That Makes People Nervous For No Reason

```rust
fn longest<'a>(x: &'a str, y: &'a str) -> &'a str {
    if x.len() > y.len() { x } else { y }
}
```

That `'a` isn't changing what the function does at runtime — it's a compile-time annotation telling the borrow checker "the reference this function returns will live at least as long as the shorter-lived of these two input references." It exists purely so the compiler can verify, statically, that you're never handing back a reference to something that's already been cleaned up.

Most of the time you never write a lifetime annotation at all — the compiler infers them via a set of rules called **lifetime elision**, and they only need to be spelled out explicitly when a function's signature is ambiguous enough that the compiler genuinely can't guess. It looks intimidating in every tutorial's scariest example and is, in practice, something most Rust developers write rarely and think about even less.

---

## 15. Smart Pointers: Box, Rc, RefCell

```rust
let b = Box::new(5);              // heap-allocated value, single owner — the simplest smart pointer
println!("{}", *b);

use std::rc::Rc;
let a = Rc::new(String::from("shared"));
let b = Rc::clone(&a);              // increments a reference count, doesn't deep-copy
println!("count: {}", Rc::strong_count(&a));   // 2

use std::cell::RefCell;
let data = RefCell::new(5);
*data.borrow_mut() += 1;             // mutate through a shared reference, checked at RUNTIME instead of compile time
```

- `Box<T>` — a value on the heap with a single, ordinary owner. Use it for recursive types (a struct that contains itself) or when you need a known, fixed size for something whose actual size isn't known until runtime.
- `Rc<T>` — reference-counted shared ownership, for when multiple parts of your program genuinely need to co-own the same data. Not thread-safe (`Arc<T>` is the thread-safe version, standing for "Atomic Rc").
- `RefCell<T>` — moves Rust's borrow-checking rules from compile time to runtime, panicking instead of refusing to compile if you violate them. The escape hatch for the rare cases where the compiler's static analysis is too conservative to accept a pattern you know is actually safe.

---

## 16. Concurrency — Fearless, Allegedly

```rust
use std::thread;

let handle = thread::spawn(|| {
    for i in 1..5 {
        println!("thread: {i}");
    }
});
handle.join().unwrap();      // wait for the thread to finish
```

```rust
use std::sync::mpsc;

let (tx, rx) = mpsc::channel();    // multiple-producer, single-consumer channel

thread::spawn(move || {
    tx.send(String::from("hi")).unwrap();
});

let received = rx.recv().unwrap();
println!("{received}");
```

```rust
use std::sync::{Arc, Mutex};

let counter = Arc::new(Mutex::new(0));
let mut handles = vec![];

for _ in 0..10 {
    let counter = Arc::clone(&counter);
    let handle = thread::spawn(move || {
        let mut num = counter.lock().unwrap();
        *num += 1;
    });
    handles.push(handle);
}

for handle in handles {
    handle.join().unwrap();
}
println!("result: {}", *counter.lock().unwrap());
```

"Fearless concurrency" is Rust's own marketing phrase, and it earns it more than most marketing phrases do — the same ownership and borrowing rules that prevent use-after-free bugs at compile time also prevent data races at compile time. A type either implements `Send` (safe to move to another thread) and `Sync` (safe to share a reference across threads) or it doesn't, and the compiler enforces this automatically for every type based on what it's made of. Try to share unsynchronized mutable state across threads and, same as ownership violations, it's not a runtime bug waiting to happen — it's a compile error, before the program ever runs once.

---

## 17. Testing

```rust
fn add(a: i32, b: i32) -> i32 {
    a + b
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn it_adds() {
        assert_eq!(add(2, 3), 5);
    }

    #[test]
    #[should_panic]
    fn it_panics() {
        panic!("this is expected");
    }
}
```

```bash
cargo test
cargo test it_adds
cargo test -- --nocapture     # show println! output even for passing tests
```

Tests live directly alongside the code they test, in the same file, behind a `#[cfg(test)]` module that only gets compiled when you actually run `cargo test` — no separate `_test.go` file convention, no test binary shipped by accident.

---

## 18. Cargo & the Crates Ecosystem

```toml
# Cargo.toml
[package]
name = "my_project"
version = "0.1.0"
edition = "2021"

[dependencies]
serde = { version = "1.0", features = ["derive"] }
tokio = { version = "1", features = ["full"] }
```

```bash
cargo add serde         # adds a dependency directly, no manual Cargo.toml editing needed
cargo update              # updates dependencies within semver constraints
cargo doc --open           # builds and opens local docs for your project + dependencies
```

A "crate" is Rust's word for a package/library — you'll hear it constantly, it's not just flavor text, it's the actual official term used in error messages, docs, and `Cargo.toml` itself.

---

## 19. Common Derives — Free Functionality, One Line

```rust
#[derive(Debug, Clone, PartialEq)]
struct Point {
    x: i32,
    y: i32,
}

let p1 = Point { x: 1, y: 2 };
let p2 = p1.clone();
println!("{:?}", p1);          // Debug formatting: Point { x: 1, y: 2 }
println!("{}", p1 == p2);       // PartialEq: true
```

Common derives worth knowing: `Debug` (enables `{:?}` printing), `Clone` (enables `.clone()`), `Copy` (makes the type implicitly copyable, only valid for simple stack-only data), `PartialEq`/`Eq` (enables `==`), `Default` (enables `Point::default()`). One line on top of a struct, and the compiler writes the boilerplate implementation for you — the closest thing Rust has to Go's `gofmt`-adjacent "stop arguing, here's the boring correct version, automatically."

---

## 20. serde & JSON

```toml
[dependencies]
serde = { version = "1.0", features = ["derive"] }
serde_json = "1.0"
```

```rust
use serde::{Serialize, Deserialize};

#[derive(Serialize, Deserialize, Debug)]
struct User {
    name: String,
    email: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    age: Option<u32>,
}

let user = User { name: "Alice".into(), email: "alice@example.com".into(), age: Some(30) };

let json = serde_json::to_string(&user)?;                 // struct -> JSON string
let parsed: User = serde_json::from_str(&json)?;            // JSON string -> struct
```

Not in the standard library — `serde` is the community's de facto standard, the way `golang-jwt` was the reach-outside-stdlib pick in the Go series. `#[derive(Serialize, Deserialize)]` does what Go's `json:"..."` struct tags do, just via the derive-macro system instead of string tags.

---

## 21. Building a Basic Web Server (Axum)

```toml
[dependencies]
axum = "0.7"
tokio = { version = "1", features = ["full"] }
serde = { version = "1.0", features = ["derive"] }
```

```rust
use axum::{routing::get, routing::post, Router, Json, extract::Path};
use serde::{Serialize, Deserialize};

#[derive(Serialize, Deserialize)]
struct User {
    id: u32,
    name: String,
}

async fn get_user(Path(id): Path<u32>) -> Json<User> {
    Json(User { id, name: String::from("Alice") })
}

async fn create_user(Json(payload): Json<User>) -> Json<User> {
    Json(payload)
}

#[tokio::main]
async fn main() {
    let app = Router::new()
        .route("/users/:id", get(get_user))
        .route("/users", post(create_user));

    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080").await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
```

No built-in HTTP server in Rust's standard library the way Go ships `net/http` — Rust deliberately keeps its stdlib minimal and leans on the crate ecosystem, with `tokio` (async runtime) plus `axum` (or `actix-web`) as the dominant, community-standard combination for real services. `#[tokio::main]` is what turns `async fn main()` into something actually runnable — Rust's `async`/`await` needs an executor to drive it, and unlike goroutines, that runtime doesn't come free with the language, you opt into one explicitly.

---

## 22. What Rust Is Actually Made For

Rust wasn't built to be everyone's next favorite scripting language — it was built at Mozilla, starting around 2010, to answer a very specific, very painful problem: **how do you write systems-level code — browsers, OS components, anything historically written in C or C++ — without the memory-safety bugs that category of code has been quietly shipping for four decades?**

That origin shows up directly in what Rust is genuinely excellent at:

- **Systems programming** — OS kernels, browser engines (Firefox's Servo, parts of Firefox itself), embedded firmware — anywhere C/C++ used to be the only serious option, and memory-safety bugs used to be an accepted cost of doing business.
- **Performance-critical services** — no garbage collector means no GC pause, ever, which matters enormously for latency-sensitive infrastructure (Discord, Cloudflare, and a growing chunk of the database and networking world have rewritten hot paths in Rust specifically for this).
- **CLI tools** — same pitch as Go here: fast startup, a single static binary, nothing to install on the target machine (`ripgrep`, `fd`, `bat`, and a huge share of the modern "better Unix tool" renaissance are Rust).
- **WebAssembly** — Rust compiles to Wasm cleanly and is one of the most popular languages for running genuinely performance-sensitive code inside a browser.
- **Anywhere "this absolutely cannot have a memory-safety bug" is a hard requirement** — safety-critical embedded systems, cryptography libraries, anything where a CVE isn't an inconvenience but a headline.

What Rust is **not** particularly aimed at, and will make you work for it if you try:

- Quick throwaway scripts (Python/Ruby will get you there in a tenth of the typing, and the borrow checker has zero patience for "I'll clean this up later")
- Rapid prototyping where you'd rather iterate fast and refactor later than get it right the first time — Rust's whole design philosophy actively resists "just make it work for now"
- Teams without appetite for a genuinely steep initial learning curve — ownership and borrowing are not optional concepts you can skip past, they're the load-bearing walls of the entire language

The whole language reads as a direct answer to Rust's original problem statement: no garbage collector because systems code can't tolerate GC pauses, ownership and borrowing enforced at compile time because runtime memory bugs were exactly the disease this language was invented to cure, an initially steep learning curve because the compiler is doing, upfront, all the reasoning about memory safety that C/C++ historically left for a debugger, a crash log, and a very bad day to discover later.

---

*Pairs well with the Go cheat sheet in this same series — same goal, same reference-doc format, two very different answers to "how much should the compiler trust you."*
