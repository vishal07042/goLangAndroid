# The Java Cheat Sheet: Syntax to the JVM

A reference doc — basics through the runtime that's been running half the internet's backend since before some of you were born.

---

## 1. Hello World & Compilation Model

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

- Every file's public class name must match the filename exactly (`Main.java` for `class Main`).
- Java doesn't compile straight to native machine code — `javac` compiles to **bytecode** (a `.class` file), and the **JVM (Java Virtual Machine)** interprets/JIT-compiles that bytecode at runtime. This is the single biggest architectural difference from Go and Rust in this series — "write once, run anywhere" means the JVM is the actual thing touching your specific OS and CPU, not your compiled code directly.

```bash
javac Main.java     # compiles to Main.class (bytecode)
java Main             # runs it — the JVM loads and executes the bytecode
```

```bash
mvn compile              # Maven build
mvn test
mvn package
gradle build              # Gradle build (the other dominant build tool)
```

---

## 2. Variables & Types

```java
int x = 5;
double d = 3.14;
boolean flag = true;
char c = 'A';
String s = "hello";        // NOT a primitive — a full object, capital S

final int MAX = 100;        // final == Java's const — assign once, never reassign
var y = 10;                    // type inference (Java 10+) — still statically typed, just inferred
```

```java
// primitives — 8 of them, always lowercase, live on the stack when local
byte, short, int, long, float, double, boolean, char

// wrapper classes — the object version of each primitive
Integer, Long, Double, Boolean, Character
```

**Primitives vs. wrapper classes is a distinction every other language in this series avoids and Java leans directly into.** A primitive `int` is a raw value, stack-allocated, fast, no method calls possible on it. An `Integer` is a full heap-allocated object wrapping that value, with methods, and critically, capable of being `null` — an `int` can never be `null`, an `Integer` can, and forgetting that difference is the source of a whole category of `NullPointerException`s from unboxing a null `Integer` back into a primitive context.

```java
Integer a = 127;
Integer b = 127;
a == b;              // true — cached, small Integer values (-128 to 127) are interned

Integer c = 200;
Integer d = 200;
c == d;              // false! — outside the cache range, these are different objects
c.equals(d);         // true — always use .equals() for wrapper/object comparison, never ==
```

This is a genuinely famous Java gotcha, asked in interviews specifically because it catches people who've internalized "== compares values" from other languages — in Java, `==` on objects compares **reference identity**, not value equality, and the small-integer cache just happens to make it *look* like value comparison for a narrow range.

---

## 3. Operators & Control Flow

```java
+ - * / %
== != < > <= >=
&& || !
& | ^ ~ << >> >>>          // >>> is unsigned right shift — no equivalent in Go/Rust/Python
```

```java
if (age >= 18) {
    System.out.println("adult");
} else if (age >= 13) {
    System.out.println("teen");
} else {
    System.out.println("child");
}

for (int i = 0; i < 5; i++) {
    System.out.println(i);
}

for (String item : items) {          // enhanced for-loop ("for-each")
    System.out.println(item);
}

int i = 0;
while (i < 5) { i++; }

switch (day) {
    case "MON", "TUE", "WED", "THU", "FRI" -> System.out.println("weekday");   // arrow form, Java 14+
    case "SAT", "SUN" -> System.out.println("weekend");
    default -> System.out.println("unknown");
}

String result = switch (day) {          // switch as an EXPRESSION, Java 14+
    case "MON" -> "Start of week";
    default -> "Some other day";
};
```

The classic colon-style `switch` still exists and still requires explicit `break` per case (fall-through by default, same trap as C) — the newer arrow syntax fixes that entirely and is the idiomatic choice in any codebase targeting Java 14+.

---

## 4. Methods & the `static` Keyword

```java
public class MathUtils {
    public static int add(int a, int b) {      // static — called on the CLASS, no instance needed
        return a + b;
    }

    public int instanceAdd(int a, int b) {        // instance method — needs an actual object to call it on
        return a + b;
    }
}

MathUtils.add(2, 3);                    // static — MathUtils.method()
new MathUtils().instanceAdd(2, 3);         // instance — object.method()
```

**`static` means "belongs to the class itself, not to any particular instance."** `main()` is `static` for exactly this reason — the JVM needs to call it before any object of your class has ever been created. Overusing `static` for things that should be instance state is a classic beginner anti-pattern — it silently turns what should be per-object data into globally shared state across your entire program.

```java
public int add(int a, int b) { return a + b; }
public double add(double a, double b) { return a + b; }    // overloading — same name, different parameter types
```

---

## 5. Classes, Objects & Constructors

```java
public class Person {
    private String name;         // private — encapsulation, the OOP pillar Java takes most seriously
    private int age;

    public Person(String name, int age) {     // constructor — same name as the class, no return type
        this.name = name;                        // `this` disambiguates the field from the parameter
        this.age = age;
    }

    public String getName() {           // getter
        return name;
    }

    public void setAge(int age) {          // setter
        this.age = age;
    }

    @Override
    public String toString() {              // controls what println(person) actually shows
        return "Person{name='" + name + "', age=" + age + "}";
    }

    @Override
    public boolean equals(Object o) {         // controls what .equals() means for this type
        if (this == o) return true;
        if (!(o instanceof Person p)) return false;
        return age == p.age && name.equals(p.name);
    }

    @Override
    public int hashCode() {                     // MUST be overridden alongside equals() — see below
        return Objects.hash(name, age);
    }
}

Person p = new Person("Alice", 30);
```

**The `equals`/`hashCode` contract is one of Java's most important, most-violated rules:** if you override `equals()`, you must also override `hashCode()`, consistently — two objects that are `.equals()` to each other **must** produce the same `hashCode()`. Break this and objects silently, mysteriously fail to be found in `HashMap`s and `HashSet`s, because those collections use `hashCode()` to decide which bucket to even look in before ever calling `equals()` to confirm a match.

### Records — Java's answer to boilerplate data classes (Java 16+)

```java
public record Point(int x, int y) { }

Point p1 = new Point(1, 2);
Point p2 = new Point(1, 2);
p1.equals(p2);       // true — auto-generated
p1.x();                // 1 — auto-generated accessor (no "get" prefix)
System.out.println(p1);  // Point[x=1, y=2] — auto-generated toString
```

A `record` auto-generates the constructor, accessors, `equals()`, `hashCode()`, and `toString()` from the declared fields in one line — Java's much-delayed answer to the exact same boilerplate problem Rust's `#[derive(...)]` and Python's `@dataclass` solve in this same series, just arriving considerably later in the language's life.

---

## 6. Inheritance & Interfaces

```java
public class Animal {
    protected String name;

    public Animal(String name) { this.name = name; }

    public String speak() { return name + " makes a sound"; }
}

public class Dog extends Animal {          // single inheritance only — one parent class, always
    public Dog(String name) { super(name); }       // super() calls the parent constructor

    @Override
    public String speak() { return name + " barks"; }    // override — same signature, new behavior
}
```

Java allows extending exactly **one** class — no multiple inheritance of implementation, deliberately, to dodge the classic diamond-inheritance ambiguity problem C++ has to solve with rules nobody fully remembers.

```java
public interface Shape {
    double area();                          // abstract by default — no body

    default String describe() {                // default method (Java 8+) — DOES have a body
        return "A shape with area " + area();
    }
}

public class Circle implements Shape {        // "implements" for interfaces, explicit, unlike Go's implicit satisfaction
    private double radius;

    public Circle(double radius) { this.radius = radius; }

    @Override
    public double area() { return Math.PI * radius * radius; }
}
```

A class **can** implement multiple interfaces (`class Foo implements A, B, C`), which is how Java gets most of the practical benefit of multiple inheritance without the diamond problem — interfaces (mostly) don't carry state, only behavior contracts, `default` methods notwithstanding.

---

## 7. Collections — Java's Standard Library Workhorses

```java
import java.util.*;

List<Integer> list = new ArrayList<>();
list.add(1);
list.add(2);
list.get(0);          // 1
list.remove(0);
list.size();

List<Integer> linked = new LinkedList<>();   // better for frequent insert/remove in the middle

Set<Integer> set = new HashSet<>();           // unique elements, no guaranteed order
set.add(1);
set.contains(1);          // true

Set<Integer> ordered = new TreeSet<>();          // unique elements, SORTED order
Set<Integer> insertion = new LinkedHashSet<>();   // unique elements, INSERTION order preserved

Map<String, Integer> map = new HashMap<>();
map.put("a", 1);
map.get("a");                // 1
map.getOrDefault("z", 0);       // 0 — safe lookup with a fallback
map.containsKey("a");
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    System.out.println(entry.getKey() + " = " + entry.getValue());
}

Map<String, Integer> ordered2 = new TreeMap<>();      // sorted by key
Map<String, Integer> insertion2 = new LinkedHashMap<>();  // insertion order preserved
```

**`ArrayList` vs. `LinkedList`, and `HashMap` vs. `TreeMap` vs. `LinkedHashMap` — the choice always comes down to what you're optimizing for**, same way Go's `[]T` vs a map or Rust's `Vec` vs `BTreeMap` force the same underlying question: `ArrayList` gives fast random access, `LinkedList` gives fast insertion/removal mid-list at the cost of slow indexed access. `HashMap` is fastest for lookups but gives no ordering guarantee at all; `LinkedHashMap` keeps insertion order at a small overhead; `TreeMap` keeps sorted key order at the cost of `O(log n)` operations instead of `HashMap`'s average `O(1)`.

```java
List<Integer> nums = List.of(1, 2, 3);        // immutable list literal (Java 9+)
Map<String, Integer> m = Map.of("a", 1, "b", 2);   // immutable map literal
```

---

## 8. Streams — Java's Answer to Functional-Style Data Pipelines (Java 8+)

```java
List<Integer> nums = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

List<Integer> evenSquares = nums.stream()
    .filter(n -> n % 2 == 0)
    .map(n -> n * n)
    .collect(Collectors.toList());
// [4, 16, 36, 64, 100]

int sum = nums.stream()
    .mapToInt(Integer::intValue)
    .sum();

Optional<Integer> max = nums.stream().max(Integer::compareTo);

Map<Boolean, List<Integer>> partitioned = nums.stream()
    .collect(Collectors.partitioningBy(n -> n % 2 == 0));

String joined = nums.stream()
    .map(String::valueOf)
    .collect(Collectors.joining(", "));
```

This is the direct Java equivalent of Python's list comprehensions or the functional `Map`/`Filter`/`Reduce` patterns you'd hand-roll with generics in Go — a declarative pipeline over a collection instead of a manual loop with an accumulator variable. **Streams are lazy** — nothing in the chain actually executes until a terminal operation (`.collect()`, `.sum()`, `.forEach()`, etc.) is called; `.filter()` and `.map()` alone just build up a description of the pipeline.

```java
Optional<String> name = findUser(1);
name.ifPresent(n -> System.out.println("found: " + n));
String result = name.orElse("unknown");
String result2 = name.orElseThrow(() -> new RuntimeException("not found"));
```

`Optional<T>` is Java's (much later, opt-in) answer to the same "this might not have a value" problem that Rust's `Option<T>` solves at the language level — the crucial difference being Java still has actual `null` everywhere underneath, so `Optional` is a convention you can always choose to skip, not a structural guarantee the compiler enforces the way Rust's is.

---

## 9. Generics

```java
public class Box<T> {
    private T content;
    public void set(T content) { this.content = content; }
    public T get() { return content; }
}

Box<String> box = new Box<>();
box.set("hello");

public static <T extends Comparable<T>> T max(List<T> list) {    // bounded type parameter
    T result = list.get(0);
    for (T item : list) {
        if (item.compareTo(result) > 0) result = item;
    }
    return result;
}
```

Java generics use **type erasure** — generic type information exists at compile time for checking, then gets erased at runtime, so a `List<String>` and a `List<Integer>` are, at the bytecode level, both just `List`. This is a meaningfully different tradeoff from Rust's monomorphization (a specialized version generated per type, zero runtime cost, larger binary) — Java pays no binary-size cost for generics, but loses the ability to check a generic type at runtime the way you might expect (`list instanceof List<String>` doesn't even compile).

---

## 10. Exceptions — Checked vs. Unchecked, Java's Own Peculiar Split

```java
try {
    int result = 10 / 0;
} catch (ArithmeticException e) {
    System.out.println("caught: " + e.getMessage());
} catch (Exception e) {              // broader catch-all, order matters — most specific first
    System.out.println("some other exception");
} finally {
    System.out.println("always runs");
}

throw new IllegalArgumentException("bad input");

public void riskyMethod() throws IOException {     // checked exception — MUST be declared or caught
    // ...
}

class CustomException extends Exception {           // custom checked exception
    public CustomException(String message) { super(message); }
}
```

**Checked vs. unchecked exceptions is Java's most distinctive, most argued-about error-handling design decision.** `Exception` subclasses (other than `RuntimeException`) are **checked** — the compiler forces every caller to either catch them or explicitly declare `throws` in their own signature, propagating the obligation up the call stack. `RuntimeException` subclasses (`NullPointerException`, `IllegalArgumentException`, `ArrayIndexOutOfBoundsException`) are **unchecked** — no such enforcement, they can occur anywhere without any signature declaring them. Checked exceptions were meant to force deliberate handling of recoverable conditions (a missing file, a network failure); in practice, a huge chunk of the Java ecosystem considers them a design mistake that led to swallowed exceptions and `throws Exception` used as an escape hatch — worth knowing the intent and the controversy both.

```java
try (BufferedReader reader = new BufferedReader(new FileReader("file.txt"))) {
    String line = reader.readLine();
}   // reader.close() called automatically, even if an exception was thrown — try-with-resources (Java 7+)
```

Same job as Go's `defer f.Close()` and Python's `with` block — guaranteed cleanup for anything implementing `AutoCloseable`, without a manual `finally { reader.close(); }` block.

---

## 11. The JVM, Bytecode & Garbage Collection

This is the part that makes Java genuinely different from every other language in this series, and worth understanding past "it's slower because it's not compiled."

**Compilation model:** `javac` compiles Java source into **bytecode** — a portable, platform-independent instruction format, not native machine code. The JVM, running on the actual target machine, loads that bytecode and executes it. This is the literal mechanism behind "write once, run anywhere" — the same `.class` file runs unmodified on Windows, Linux, or macOS, because the JVM (a different binary per platform) is the thing actually translating to native instructions, not your compiled code doing it directly the way Go's or Rust's output does.

**JIT compilation:** modern JVMs don't just interpret bytecode line by line forever — they profile the running program, identify **hot paths** (methods called extremely frequently), and **Just-In-Time compile** those specific hot methods down to actual native machine code at runtime, replacing the slower interpreted path once a method's proven itself worth the optimization investment. This is why Java programs are famously slower to start (JIT hasn't kicked in yet, everything's still interpreted) and can become genuinely very fast once warmed up — a fundamentally different performance curve from Go or Rust's "fast from instruction one, no warm-up needed" story.

**Garbage collection:** same conceptual job as Go's GC — automatic memory management, no manual `free()`/`delete` — but with decades of JVM-specific GC algorithm evolution (Serial, Parallel, CMS, G1, and the newer low-pause ZGC and Shenandoah collectors), each with different tradeoffs between throughput and pause time, tunable via JVM flags (`-XX:+UseG1GC`, `-Xmx4g` for max heap size, and so on) — a level of GC-tuning knob-turning that Go deliberately doesn't expose nearly as much of, by design.

```bash
java -Xmx512m -Xms256m Main       # max/initial heap size
java -XX:+UseG1GC Main               # pick a specific garbage collector
javap -c Main.class                    # disassemble bytecode — the Java equivalent of `go build -gcflags="-S"`
```

---

## 12. Multithreading & Concurrency

```java
Thread t = new Thread(() -> {
    System.out.println("running in a thread");
});
t.start();
t.join();          // wait for it to finish
```

```java
import java.util.concurrent.*;

ExecutorService executor = Executors.newFixedThreadPool(4);
Future<Integer> future = executor.submit(() -> {
    return 42;
});
Integer result = future.get();       // blocks until the task completes
executor.shutdown();
```

```java
private final Object lock = new Object();
synchronized (lock) {                    // Java's built-in mutex mechanism — a keyword, not a library type
    sharedCounter++;
}

// or, on an entire method:
public synchronized void increment() {
    sharedCounter++;
}
```

`synchronized` is Java's built-in equivalent of Go's `sync.Mutex` — every object in Java has an implicit **monitor lock**, and `synchronized` acquires it for the duration of the block or method. Unlike Go's goroutines (cheap, green-thread-like, multiplexed onto few OS threads by a runtime scheduler), a Java `Thread` has historically mapped **directly to one OS thread** — genuinely more expensive to spawn thousands of, which is exactly the gap **virtual threads** (Java 21+, Project Loom) were built to close:

```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> {
        System.out.println("running on a virtual thread");
    });
}
```

Virtual threads are the JVM's own, much later answer to exactly the problem Go's goroutines solved from day one — lightweight, JVM-managed threads that get multiplexed onto a small pool of real OS threads, so spinning up hundreds of thousands of concurrent tasks stops requiring hundreds of thousands of expensive OS threads. A genuinely direct echo of the entire goroutines article from earlier in this series, just arriving at the JVM roughly fifteen years later.

---

## 13. Building a Basic Web Server (Spring Boot)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

```java
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.save(user);
    }
}

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

No built-in production HTTP framework in the standard library the way Go ships `net/http` — **Spring Boot** is the overwhelming ecosystem default, built heavily on annotations (`@RestController`, `@GetMapping`, `@Autowired`) and **dependency injection**, where Spring's container constructs and wires your objects together for you based on annotations, rather than you manually constructing and passing dependencies the way Go or Rust code typically would. This annotation-and-container-driven style is a genuinely distinct architectural philosophy from every other language in this series — more "the framework builds and owns your object graph" than "you write explicit constructor calls."

### Basic JWT auth, same shape as the rest of the series

```java
@Component
public class JwtUtil {
    private final String SECRET = System.getenv("JWT_SECRET");

    public String generateToken(String userId, String role) {
        return Jwts.builder()
            .setSubject(userId)
            .claim("role", role)
            .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
            .signWith(SignatureAlgorithm.HS256, SECRET)
            .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser().setSigningKey(SECRET).parseClaimsJws(token).getBody();
    }
}
```

Same non-negotiable list as every other closing web-server section in this series: secret from an environment variable, always set an expiry, hash passwords with something like BCrypt (Spring Security ships this), HTTPS in production, validate the signing algorithm explicitly.

---

## 14. Testing (JUnit)

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MathUtilsTest {
    @Test
    void testAdd() {
        assertEquals(5, MathUtils.add(2, 3));
    }

    @Test
    void testDivideByZero() {
        assertThrows(ArithmeticException.class, () -> {
            MathUtils.divide(10, 0);
        });
    }
}
```

```bash
mvn test
gradle test
```

JUnit 5 (Jupiter) is the modern standard, annotation-driven (`@Test`, `@BeforeEach`, `@ParameterizedTest`) the same way most of Java's ecosystem leans on annotations for configuration rather than convention or naming patterns alone.

---

## 15. What Java Is Actually Made For

Java was built by Sun Microsystems, released in 1995, with a founding mission that was radical for its time: **"write once, run anywhere."** In an era when compiled C/C++ code had to be recompiled per platform, per architecture, per OS, Java's pitch was a portable bytecode format running on a virtual machine present on every major platform — genuinely revolutionary, and the entire reason the JVM architecture (bytecode + JIT, section 11) exists at all.

That founding goal, plus three decades of enterprise adoption on top of it, shows up directly in where Java still dominates:

- **Enterprise backend systems** — banking, insurance, large-scale corporate software; Java's stability, the JVM's decades of GC and performance tuning, and Spring's ecosystem maturity make it the default choice for large, long-lived enterprise codebases maintained by big teams over many years.
- **Android development** — Java (and now Kotlin, running on the same JVM-derived tooling) was the original, and for a long time only, first-class language for native Android apps.
- **Large-scale distributed systems** — Hadoop, Kafka, Elasticsearch, and a large share of the big-data ecosystem are written in Java specifically because the JVM's decades of GC and performance engineering handle sustained, long-running, high-throughput workloads well once warmed up.
- **Cross-platform desktop and embedded systems** — less dominant today, but still genuinely present, directly tracing back to the original "run anywhere" mission.

What Java is **not** particularly aimed at, and shows real friction if you try:

- CLI tools and quick scripts — the JVM startup cost and general ceremony (compile, then run, then wait for the JVM to spin up) makes Java a poor fit next to Go's instant-start static binaries or Python's zero-compile scripting.
- Systems programming or anything requiring precise, manual memory control — the entire JVM/GC architecture is specifically the opposite bet from Rust's ownership model.
- Situations where startup latency genuinely matters (serverless functions, CLI tools invoked constantly) — the JIT-warm-up curve described in section 11 is a real, measurable cost in exactly these scenarios, which is part of why GraalVM native-image compilation (ahead-of-time compiling Java to a native binary, skipping the JVM's runtime warm-up entirely) has become an increasingly common answer for Java in these specific contexts.

The whole language reads as a direct reflection of its 1995 founding bet: bytecode and a virtual machine because true cross-platform portability was the actual product being sold, checked exceptions because Sun believed compiler-enforced error handling would produce more robust enterprise software (a bet history has judged as genuinely mixed), verbose ceremony (getters/setters, explicit types before `var` arrived) because Java's era prioritized IDE-assisted, large-team maintainability over individual typing speed, and three decades of careful backward compatibility because breaking a language millions of enterprise systems depend on was never going to be an acceptable tradeoff for elegance.

---

*Pairs with the Go, Rust, and Python cheat sheets in this same series — four languages, four different bets on what a compiler, a runtime, and a type system should actually be trusted to do for you.*
