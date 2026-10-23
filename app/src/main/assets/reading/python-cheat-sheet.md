# The Python Cheat Sheet: Indentation to Interpreters

A reference doc for the language that made whitespace load-bearing and somehow got away with it. Keep it open — the syntax is simple, the ecosystem is where you'll actually get lost.

---

## 1. Hello World & Running Code

```python
print("Hello, World!")
```

- No `main()` required, no entry-point ceremony — a `.py` file just executes top to bottom the moment you run it.
- Indentation **is** the block syntax — no `{}`, no `begin`/`end`. Get the whitespace wrong and it's not a style complaint, it's a syntax error.

```bash
python3 script.py         # run a file directly
python3 -i script.py        # run, then drop into an interactive shell with its state loaded
python3                       # REPL — the fastest way to check "wait, does this actually work"
```

```bash
python3 -m venv venv           # create an isolated virtual environment
source venv/bin/activate        # activate it (Windows: venv\Scripts\activate)
pip install requests             # install a package into the active environment
pip freeze > requirements.txt     # snapshot installed packages
```

Virtual environments exist because Python has no per-project dependency isolation baked in the way `go.mod` or `Cargo.toml` give you by default — skip this step and you will, eventually, watch two unrelated projects fight over which version of a library gets to exist on your machine.

---

## 2. Variables & Basic Types

```python
x = 5                  # no declaration keyword, no type annotation required
y = "hello"
z = 3.14
is_valid = True          # capital T/F — this isn't JavaScript

a, b, c = 1, 2, 3          # tuple unpacking assignment
a, b = b, a                  # swap, no temp variable needed
```

```python
type(x)          # <class 'int'>
isinstance(x, int)    # True
```

Python is **dynamically typed** — a variable name is just a label you can point at anything, and it can point at something else entirely a line later. This is either the language's biggest convenience or its most infamous footgun depending on how large your codebase has gotten and how many people are touching it.

### Type hints — optional, checked by nobody at runtime, checked by tools that care

```python
def greet(name: str) -> str:
    return f"Hello, {name}"

age: int = 30
```

Type hints are pure decoration as far as the Python interpreter is concerned — it will happily run `greet(42)` without complaint. Tools like `mypy` or `pyright` read the hints and yell at you separately, ahead of time, which is the entire point: opt-in static checking layered on top of a language that fundamentally doesn't care.

---

## 3. Numbers, Strings & f-strings

```python
10 / 3       # 3.3333... — always float division
10 // 3      # 3 — floor (integer) division
10 % 3       # 1 — modulo
2 ** 10      # 1024 — exponentiation, not ^

int("42")     # 42
str(42)        # "42"
float("3.14")   # 3.14
```

```python
name = "Alice"
age = 30

greeting = f"Hi, {name}, you are {age} years old"       # f-string — the one you'll use constantly
greeting2 = "Hi, {}, you are {} years old".format(name, age)   # older style, still valid
greeting3 = "Hi, " + name                                        # concatenation, fine for one-offs

f"{3.14159:.2f}"     # "3.14" — formatting inside the braces
f"{age = }"            # "age = 30" — debug-print shorthand, Python 3.8+
```

f-strings (`f"..."`) are the idiomatic default for anything with a variable in it — readable, fast, and the thing every style guide will push you toward the moment you write your first `.format()` call.

---

## 4. Control Flow

### if / elif / else — colon-and-indent, no parens required

```python
age = 20

if age >= 18:
    print("adult")
elif age >= 13:
    print("teen")
else:
    print("child")
```

No `switch` statement existed in Python at all until 3.10 introduced `match` — for two decades the idiomatic answer was chained `elif` or a dictionary lookup, and plenty of code still does exactly that.

### Loops

```python
for i in range(5):            # 0, 1, 2, 3, 4
    print(i)

for i in range(2, 10, 2):      # start, stop, step -> 2, 4, 6, 8
    print(i)

for item in ["a", "b", "c"]:
    print(item)

for i, item in enumerate(["a", "b", "c"]):    # index + value together
    print(i, item)

while count < 5:
    count += 1                  # no ++ in Python — spell it out

for i in range(10):
    if i == 5:
        break
    if i % 2 == 0:
        continue
else:                            # for/while can have an else — runs if the loop completed WITHOUT break
    print("loop finished normally")
```

The `for...else` construct is one of Python's most genuinely obscure features — the `else` block runs only if the loop never hit a `break`. Most Python developers go years without needing it, and it's worth knowing purely so it doesn't ambush you the one time you see it in someone else's code.

### match — structural pattern matching (Python 3.10+)

```python
match command.split():
    case ["go", direction]:
        print(f"going {direction}")
    case ["look"]:
        print("looking around")
    case ["take", item] if item in inventory:      # guard clause
        print(f"taking {item}")
    case _:
        print("unknown command")
```

Newer and considerably more powerful than a simple value-equality switch — it can destructure lists, dicts, and objects directly in the pattern, closer in spirit to Rust's `match` than to a C-style switch.

---

## 5. Functions

```python
def add(a, b):
    return a + b

def greet(name, greeting="Hello"):     # default argument
    return f"{greeting}, {name}"

greet("Alice")                  # "Hello, Alice"
greet("Alice", "Hi")              # "Hi, Alice"
greet(name="Alice", greeting="Hey")  # keyword arguments, order doesn't matter

def total(*args):                # variadic positional — args is a tuple
    return sum(args)

def config(**kwargs):             # variadic keyword — kwargs is a dict
    for key, value in kwargs.items():
        print(key, value)

total(1, 2, 3)                    # 6
config(host="localhost", port=8080)
```

```python
add = lambda a, b: a + b     # anonymous function, single expression only

def make_adder(x):             # closures — completely normal in Python
    def adder(y):
        return x + y
    return adder
```

**The mutable default argument trap** — the one bug every Python developer writes exactly once and never forgets:

```python
def append_item(item, items=[]):    # DANGER — this default list is created ONCE, at function definition
    items.append(item)
    return items

append_item(1)   # [1]
append_item(2)   # [1, 2] — NOT [2] — same list object reused across every call with no argument
```

The fix is the idiom you'll see everywhere in real code:

```python
def append_item(item, items=None):
    if items is None:
        items = []
    items.append(item)
    return items
```

---

## 6. Lists, Tuples, Sets, Dicts

### Lists — mutable, ordered, the workhorse collection

```python
nums = [1, 2, 3, 4, 5]
nums.append(6)
nums.insert(0, 0)
nums.remove(3)         # removes the VALUE 3, not the index
nums.pop()               # removes and returns the last item
nums[1:3]                  # slicing -> [1, 2] (values may differ post-mutation above)
nums[::-1]                 # reversed copy
len(nums)

squares = [x**2 for x in range(10)]                     # list comprehension
evens = [x for x in range(20) if x % 2 == 0]              # with a filter
pairs = [(x, y) for x in range(3) for y in range(3)]        # nested
```

List comprehensions are Python's most idiomatic feature end to end — the "Pythonic" way to build a list is almost never a manual `for` loop with `.append()` calls if a comprehension can say the same thing in one readable line.

### Tuples — immutable, ordered

```python
point = (3, 4)
x, y = point               # unpacking
single = (5,)                # the trailing comma matters — (5) is just the int 5 in parens
```

### Sets — unordered, unique elements

```python
s = {1, 2, 3}
s.add(4)
s2 = {3, 4, 5}
s & s2       # intersection -> {3, 4}
s | s2       # union -> {1, 2, 3, 4, 5}
s - s2       # difference -> {1, 2}
```

### Dicts — key-value, ordered by insertion since Python 3.7

```python
person = {"name": "Alice", "age": 30}
person["email"] = "alice@example.com"
person.get("phone", "unknown")            # safe lookup, default if missing — no KeyError

for key, value in person.items():
    print(key, value)

squared_dict = {x: x**2 for x in range(5)}      # dict comprehension
```

Unlike Go's explicitly-random map iteration order, Python dicts have preserved **insertion order** as a language guarantee since 3.7 — code that relies on it is portable and correct, not accidentally lucky.

---

## 7. Strings in Depth

```python
s = "Hello, World"

s.upper()             # "HELLO, WORLD"
s.lower()               # "hello, world"
s.strip()                 # trims leading/trailing whitespace
s.split(",")               # ["Hello", " World"]
",".join(["a", "b", "c"])    # "a,b,c"
s.replace("World", "Python")   # "Hello, Python"
s.startswith("Hello")            # True
s.find("World")                    # 7 — index, -1 if not found
len(s)

s[0]        # "H" — indexing
s[7:12]      # "World" — slicing
s[::-1]       # reversed string

"  ".join(str(x) for x in [1, 2, 3])   # generator expression fed directly into join
```

Strings are **immutable** in Python, same as Go — every "modification" method returns a new string rather than mutating in place, which is why `s.upper()` alone, with the result discarded, does nothing at all.

---

## 8. Classes & OOP

```python
class Person:
    species = "Homo sapiens"          # class attribute — shared across all instances

    def __init__(self, name, age):      # constructor
        self.name = name                  # instance attribute
        self.age = age

    def greet(self):                     # instance method — self is explicit, always the first param
        return f"Hi, I'm {self.name}"

    def __str__(self):                    # controls what str(obj) / print(obj) shows
        return f"Person({self.name}, {self.age})"

    def __repr__(self):                    # controls the developer-facing / REPL representation
        return f"Person(name={self.name!r}, age={self.age})"

p = Person("Alice", 30)
print(p.greet())
print(p)              # uses __str__
```

`self` being an explicit first parameter, every time, on every method, is the thing every Python beginner from a different language finds strange and every experienced Python developer has fully stopped noticing.

### Inheritance

```python
class Employee(Person):
    def __init__(self, name, age, salary):
        super().__init__(name, age)        # call the parent constructor explicitly
        self.salary = salary

    def greet(self):                          # method override
        base = super().greet()
        return f"{base}, I earn {self.salary}"
```

### Dataclasses — the boilerplate killer

```python
from dataclasses import dataclass

@dataclass
class Point:
    x: int
    y: int

p1 = Point(1, 2)
p2 = Point(1, 2)
p1 == p2         # True — __eq__ generated automatically
print(p1)          # Point(x=1, y=2) — __repr__ generated automatically
```

`@dataclass` auto-generates `__init__`, `__repr__`, and `__eq__` from the type-annotated fields — Python's answer to writing `__init__` and `__repr__` by hand for every simple data-holding class, roughly analogous to Rust's `#[derive(Debug, PartialEq)]`.

### Properties — computed attributes that still look like plain attributes

```python
class Circle:
    def __init__(self, radius):
        self._radius = radius

    @property
    def area(self):
        return 3.14159 * self._radius ** 2

c = Circle(5)
print(c.area)     # called like an attribute, not a method — no parens
```

---

## 9. Decorators — Functions Wrapping Functions, the Whole Way Down

```python
def my_decorator(func):
    def wrapper(*args, **kwargs):
        print(f"calling {func.__name__}")
        result = func(*args, **kwargs)
        print(f"{func.__name__} finished")
        return result
    return wrapper

@my_decorator
def say_hello(name):
    print(f"Hello, {name}")

say_hello("Alice")
# calling say_hello
# Hello, Alice
# say_hello finished
```

`@my_decorator` above `def say_hello` is exactly, literally equivalent to writing `say_hello = my_decorator(say_hello)` — decorators are ordinary functions, syntax sugar makes them read like an annotation instead of a reassignment. This is the mechanism behind `@property`, `@staticmethod`, `@dataclass`, Flask/FastAPI route registration (`@app.get("/users")`), and most of the "magic-looking" syntax you'll encounter in real Python codebases.

```python
@staticmethod
def utility(): ...        # no self, callable on the class itself

@classmethod
def from_string(cls, s): ...     # receives the class itself as first arg, not an instance
```

---

## 10. Error Handling

```python
try:
    result = 10 / 0
except ZeroDivisionError as e:
    print(f"caught: {e}")
except (TypeError, ValueError) as e:      # catch multiple exception types
    print(f"caught: {e}")
else:
    print("no exception occurred")           # runs only if no exception was raised
finally:
    print("this always runs")                 # cleanup, runs no matter what

raise ValueError("something went wrong")

class CustomError(Exception):               # custom exceptions — just subclass Exception
    pass

raise CustomError("a specific problem")
```

Unlike Go and Rust, Python **does** use exceptions as its primary error-handling mechanism — the opposite philosophy from both languages in this series. There's no `Result<T, E>` or `(value, error)` convention baked into the standard idiom; a function either returns a value or raises, and callers wrap risky calls in `try`/`except` rather than checking a return value every time. "Ask forgiveness, not permission" (EAFP) is the actual named Python idiom for this — attempt the operation, catch the exception if it fails, rather than checking every precondition up front.

---

## 11. Modules & Packages

```python
# mymodule.py
def greet(name):
    return f"Hello, {name}"

# main.py
import mymodule
mymodule.greet("Alice")

from mymodule import greet
greet("Alice")

from mymodule import greet as hi     # aliasing
import mymodule as mm                   # aliasing the whole module
```

```python
if __name__ == "__main__":
    main()
```

This last idiom is everywhere in real Python code and confuses everyone once — `__name__` is `"__main__"` only when the file is run directly, and is the module's actual name when it's imported by something else. It's the one-line way to say "run this code only if I was executed, not if I was imported."

```
myproject/
    __init__.py       # marks a directory as a package (optional since Python 3.3, still conventional)
    module_a.py
    module_b.py
```

---

## 12. Iterators & Generators

```python
def count_up_to(n):
    i = 1
    while i <= n:
        yield i          # pauses here, resumes on the next call — this is what makes it a generator
        i += 1

for num in count_up_to(5):
    print(num)

gen = (x**2 for x in range(10))    # generator expression — like a list comprehension, but lazy
```

`yield` turns an ordinary-looking function into a generator — instead of computing and returning everything at once, it produces values one at a time, on demand, pausing its execution state between each `yield`. This matters enormously for large or infinite sequences: a list comprehension `[x**2 for x in range(10_000_000)]` builds the entire list in memory immediately; the generator equivalent produces one value at a time and never holds more than that in memory at once.

---

## 13. Context Managers — the `with` Statement

```python
with open("file.txt") as f:
    contents = f.read()
# file is automatically closed here, even if an exception was raised inside the block
```

```python
class Timer:
    def __enter__(self):
        self.start = time.time()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        print(f"elapsed: {time.time() - self.start:.2f}s")

with Timer():
    do_something_slow()
```

`with` is Python's answer to Go's `defer f.Close()` — guaranteed cleanup, but scoped to a block rather than the whole function, and driven by `__enter__`/`__exit__` methods instead of a `defer` keyword. Anything that acquires a resource (files, locks, database connections, network sockets) idiomatically supports `with` for exactly this reason.

---

## 14. Type Hints, in More Depth

```python
from typing import Optional, Union

def find_user(id: int) -> Optional[str]:      # Optional[str] == Union[str, None]
    ...

def process(value: Union[int, str]) -> str:      # accepts either type
    ...

def process(value: int | str) -> str:              # same thing, Python 3.10+ shorthand
    ...

from typing import List, Dict

def get_names() -> List[str]: ...
def get_scores() -> Dict[str, int]: ...

# Python 3.9+ — built-in generics, no typing import needed for these
def get_names() -> list[str]: ...
def get_scores() -> dict[str, int]: ...
```

Type hints are Python's version of the compiler feedback Go and Rust give you for free — except here it's opt-in, checked by a separate tool (`mypy`, `pyright`), and enforced by team convention rather than the interpreter itself. Large, serious Python codebases lean on this heavily; quick scripts almost never bother, and both are considered completely normal.

---

## 15. Async / Await

```python
import asyncio

async def fetch_data():
    await asyncio.sleep(1)     # simulates an async I/O wait
    return "data"

async def main():
    result = await fetch_data()
    print(result)

    results = await asyncio.gather(fetch_data(), fetch_data(), fetch_data())   # run concurrently

asyncio.run(main())
```

Python's `async`/`await` is cooperative concurrency on a **single thread**, driven by an event loop (`asyncio`) — genuinely good for I/O-bound work (network calls, file I/O) where a task is mostly waiting, structurally unable to help with CPU-bound work the way Go's goroutines spread across real OS threads can. This is the direct consequence of the infamous **GIL (Global Interpreter Lock)** — the standard CPython interpreter only ever executes one thread's Python bytecode at a time, full stop, which is the single biggest philosophical divergence from the Go series' entire concurrency story.

```python
import threading
import multiprocessing

t = threading.Thread(target=some_function)     # real OS thread, still bound by the GIL for Python code
t.start()
t.join()

p = multiprocessing.Process(target=some_function)   # separate process — sidesteps the GIL entirely, real parallelism
p.start()
p.join()
```

**The honest one-paragraph version:** `threading` gives you real OS threads but they still take turns executing Python bytecode one at a time because of the GIL — genuinely useful for I/O-bound work, not for CPU-bound parallelism. `multiprocessing` sidesteps the GIL completely by using separate processes with separate interpreters, which gets you real parallel CPU work at the cost of heavier processes and no shared memory by default. `asyncio` is neither threads nor processes — it's cooperative single-threaded concurrency, closest in spirit to a single goroutine's worth of Go concurrency rather than the whole GMP scheduler story.

---

## 16. JSON

```python
import json

data = {"name": "Alice", "age": 30}

json_str = json.dumps(data)                       # dict -> JSON string
json_str_pretty = json.dumps(data, indent=2)         # human-readable

parsed = json.loads('{"name": "Bob", "age": 25}')     # JSON string -> dict

with open("data.json", "w") as f:
    json.dump(data, f)             # write directly to a file

with open("data.json") as f:
    loaded = json.load(f)             # read directly from a file
```

No struct tags, no derive macros — `json` module works directly with plain `dict`/`list`/`str`/`int`/etc., since Python's dynamic typing means there's no dedicated "shape" to declare ahead of time the way Go's structs or Rust's `#[derive(Serialize)]` require. For typed JSON handling closer to Go/Rust ergonomics, libraries like `pydantic` layer validation and typed models on top of this.

---

## 17. Testing

```python
# test_math.py
def add(a, b):
    return a + b

def test_add():
    assert add(2, 3) == 5

def test_add_negative():
    assert add(-1, -1) == -2
```

```bash
pip install pytest
pytest                    # discovers and runs every test_*.py / *_test.py automatically
pytest -v                   # verbose
pytest test_math.py::test_add    # run one specific test
```

`pytest` is the de facto standard, despite Python's own built-in `unittest` module existing in the standard library — plain `assert` statements instead of `self.assertEqual(...)` boilerplate is most of the reason. Fixtures handle setup/teardown:

```python
import pytest

@pytest.fixture
def sample_data():
    return {"name": "Alice", "age": 30}

def test_with_fixture(sample_data):
    assert sample_data["name"] == "Alice"
```

---

## 18. Building a Basic Web Server (FastAPI)

```bash
pip install fastapi uvicorn
```

```python
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

class User(BaseModel):
    name: str
    email: str
    age: int | None = None

users = {}

@app.get("/users/{user_id}")
def get_user(user_id: int):
    if user_id not in users:
        raise HTTPException(status_code=404, detail="user not found")
    return users[user_id]

@app.post("/users/{user_id}")
def create_user(user_id: int, user: User):
    users[user_id] = user
    return user
```

```bash
uvicorn main:app --reload      # runs the dev server, auto-reloads on file changes
```

Same story as Rust's Axum — no HTTP server in the standard library aimed at real production use (there's a bare-bones `http.server` module, genuinely meant only for local testing, not production traffic). `FastAPI` is the modern, type-hint-driven pick: request/response bodies are just `pydantic` model classes (built on the same type hints from §14), and it auto-generates interactive API docs from those type hints for free — arguably the single feature that made it the default choice over older frameworks like Flask for new API projects.

### Basic JWT auth, same shape as the Go series' closing section

```bash
pip install python-jose[cryptography] passlib[bcrypt]
```

```python
from jose import jwt
from datetime import datetime, timedelta

SECRET_KEY = "replace-with-a-real-secret-from-env"

def create_token(user_id: str, role: str) -> str:
    payload = {
        "sub": user_id,
        "role": role,
        "exp": datetime.utcnow() + timedelta(hours=1),
    }
    return jwt.encode(payload, SECRET_KEY, algorithm="HS256")

def verify_token(token: str) -> dict:
    return jwt.decode(token, SECRET_KEY, algorithms=["HS256"])   # raises on invalid/expired
```

```python
from fastapi import Depends, Header, HTTPException

def get_current_user(authorization: str = Header(...)) -> dict:
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="missing or malformed token")
    token = authorization.removeprefix("Bearer ")
    try:
        return verify_token(token)
    except Exception:
        raise HTTPException(status_code=401, detail="invalid or expired token")

def require_admin(user: dict = Depends(get_current_user)) -> dict:
    if user.get("role") != "admin":
        raise HTTPException(status_code=403, detail="forbidden")
    return user

@app.get("/users/{user_id}")
def get_user(user_id: int, current_user: dict = Depends(get_current_user)):
    ...

@app.delete("/users/{user_id}")
def delete_user(user_id: int, current_user: dict = Depends(require_admin)):
    ...
```

`Depends(...)` is FastAPI's dependency-injection system doing the same job Go's middleware chain did in the earlier series' closing section — `get_current_user` answers "is this a valid token" (401 if not), `require_admin` layers "is this token allowed to do this" on top (403 if not), and the same non-negotiable list applies here too: real secret from an environment variable, always set an expiry, hash passwords with `passlib`/bcrypt rather than storing plaintext, HTTPS in production.

---

## 19. What Python Is Actually Made For

Python wasn't built as a systems language or a concurrency-first network-services language the way Go and Rust in this series were — it was built by Guido van Rossum starting in 1989 with a much broader, much gentler goal: **readable, expressive code that gets out of the way between an idea and a working program**, explicitly prioritizing developer time and clarity over raw execution speed.

That founding goal shows up directly in where Python actually dominates:

- **Data science & machine learning** — `numpy`, `pandas`, `pytorch`, `scikit-learn`; this is arguably Python's single strongest claim to "the language," to the point that most ML research code and most production ML pipelines are Python by overwhelming default.
- **Scripting & automation** — quick, throwaway tooling, glue code between other systems, anything where "get it working in twenty minutes" matters more than "make it maximally fast."
- **Web backends** — Django and FastAPI are both genuinely production-grade, widely deployed at real scale, even without the raw throughput of a Go or Rust service underneath.
- **Prototyping & research** — the low ceremony (no compiler, no type declarations required, a REPL for instant feedback) makes it the default choice for "I don't know what I'm building yet, I just need to try things fast."
- **Teaching** — routinely the first language taught in CS courses worldwide, precisely because the syntax gets out of the way of the actual concepts being taught.

What Python is **not** particularly aimed at, and will make you feel it if you try:

- CPU-bound, high-performance, low-latency systems — the GIL and interpreted execution put a hard ceiling well below what Go or Rust hit natively, without reaching for C extensions or a different runtime entirely.
- Mobile or desktop application distribution — no clean story for "ship a single binary to an end user's machine" the way Go and Rust's static binaries do; a Python app needs an interpreter and a dependency environment to exist on the target machine.
- Anywhere memory safety and zero-cost abstractions are the actual requirement — Rust's entire pitch is close to Python's polar opposite here, and that's by design on both sides.

The whole language reads as a direct reflection of that founding priority: dynamic typing because forcing type declarations slows down exactly the kind of fast iteration Python was built for, a REPL and instant script execution because compile times shouldn't stand between a person and testing an idea, one obvious idiomatic way to do most things (list comprehensions, f-strings, `with` blocks) because "readability counts" is literally written into the language's own guiding philosophy (`import this`), and an accepted performance ceiling because the tradeoff was always meant to favor the human writing the code over the machine running it.

---

*Pairs with the Go and Rust cheat sheets in this same series — three languages, three different answers to how much the machine should trust you, and how much you should have to think about it.*
