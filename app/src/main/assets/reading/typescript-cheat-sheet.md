# The TypeScript Cheat Sheet: Types to Tooling

A reference doc — JavaScript with a type checker bolted on, and why that bolt-on turned out to matter more than anyone expected.

---

## 1. What Problem TypeScript Actually Solves

JavaScript has no static type system at all — a variable can hold a number, then a string, then an object, with nothing checking any of it until the exact line that breaks at runtime, often in production, often in a code path nobody tested. At small scale this is fine, even convenient. At the scale of a real codebase with dozens of contributors, this becomes the actual root cause of a huge share of production bugs: a function expecting an object gets `undefined`, a field gets renamed in one file and forty call sites elsewhere silently keep referencing the old name, an API response shape changes and nothing catches it until a user hits the broken path.

**TypeScript's actual pitch: add a static type system on top of JavaScript, checked entirely at compile time, that compiles down to plain JavaScript at the end — zero runtime cost, all the safety paid for upfront by the compiler instead of discovered later by your users.** It's not a new language runtime, not a new execution environment — it's JavaScript, plus a checker that reads your code before it ever runs and catches an entire category of "this doesn't line up" bugs that JavaScript would otherwise only reveal by actually crashing.

```typescript
function add(a: number, b: number): number {
    return a + b;
}

add(5, "10");   // compile-time error — caught before this code ever runs, not after
```

The exact same code with no types would happily run, silently coerce `"10"` in some JavaScript-specific way, and produce a value that's technically "working" and almost certainly not what anyone intended.

---

## 2. How It Actually Solves It — Structural Typing & Compile-Time Erasure

Two mechanical decisions define TypeScript's whole character, and both are worth understanding rather than memorizing.

**Structural typing:** TypeScript doesn't care what a type is *named* — it cares what *shape* a value has. If two types have the same structure, they're compatible, regardless of whether one was ever declared to "implement" the other.

```typescript
interface Point { x: number; y: number; }

function printPoint(p: Point) {
    console.log(`${p.x}, ${p.y}`);
}

const obj = { x: 1, y: 2, z: 3 };   // never declared as a Point, doesn't matter
printPoint(obj);                       // fine — obj HAS the required shape, extra fields are allowed here
```

This is the same "if it has the method, it satisfies the interface" philosophy Go uses for its interfaces (from earlier in this series) — TypeScript checks shape, not declared identity, which is a meaningfully different design decision from nominally-typed languages like Java or Rust, where a type has to explicitly declare which interface or trait it implements.

**Type erasure:** every single type annotation is stripped away entirely during compilation — types exist purely to help the compiler check your code before runtime, and have **zero presence, zero cost, zero existence** in the actual JavaScript that ships and runs.

```typescript
function greet(name: string): string {
    return `Hello, ${name}`;
}
```

compiles down to:

```javascript
function greet(name) {
    return `Hello, ${name}`;
}
```

Every `: string`, every `interface`, every generic parameter — gone, completely, by the time this code actually runs in a browser or Node.js. This means you genuinely cannot check a TypeScript type at runtime (`typeof` still only knows JavaScript's own runtime types) and it means TypeScript adds **zero** runtime performance overhead, ever — the entire value proposition is paid for and consumed at compile time, then vanishes.

```bash
npx tsc file.ts          # compile a .ts file to .js
npx tsc --init              # generate a tsconfig.json
npx ts-node file.ts            # compile and run in one step, for quick iteration
```

---

## 3. Basic Types

```typescript
let age: number = 30;
let name: string = "Alice";
let isActive: boolean = true;
let items: string[] = ["a", "b", "c"];
let items2: Array<string> = ["a", "b", "c"];    // equivalent generic syntax
let tuple: [string, number] = ["Alice", 30];       // fixed-length, fixed-type-per-position

let anything: any = 42;         // opts OUT of type checking entirely — the "I give up" type, avoid it
let value: unknown = fetchData();   // the SAFE version of any — must narrow before use, see section 6

function logMessage(msg: string): void { console.log(msg); }    // no meaningful return value
function fail(): never { throw new Error("always throws"); }      // never actually returns at all

let user: null = null;
let missing: undefined = undefined;
```

**`any` vs. `unknown` is the single most important distinction in this whole section.** `any` genuinely disables type checking for that value — you can call any method, access any property, and the compiler will never complain, which quietly defeats the entire point of using TypeScript in the first place. `unknown` is the type-safe alternative for "I genuinely don't know this type yet" — you're forced to narrow it (check what it actually is) before TypeScript will let you do anything with it. Reaching for `any` is almost always a shortcut you'll regret; `unknown` gets you the same "I don't know yet" flexibility without opting out of safety entirely.

---

## 4. Interfaces & Type Aliases

```typescript
interface User {
    name: string;
    age: number;
    email?: string;              // optional property
    readonly id: number;           // can be set once, never reassigned after
}

type UserAlias = {
    name: string;
    age: number;
};
```

**Interfaces vs. type aliases — genuinely overlapping, with a few real differences worth knowing:**

```typescript
interface Animal { name: string; }
interface Animal { sound: string; }     // declaration merging — interfaces with the same name COMBINE
// Animal is now { name: string; sound: string; }

type Shape = { name: string; };
type Shape = { sound: string; };         // ERROR — type aliases cannot be redeclared like this
```

```typescript
type ID = string | number;                 // type aliases CAN represent unions — interfaces cannot
type Status = "active" | "inactive" | "pending";   // a "literal union" — TypeScript's lightweight enum alternative
```

Rule of thumb genuinely used in most style guides: prefer `interface` for object shapes that might need to be extended or merged (especially public API surfaces, library definitions), prefer `type` for unions, tuples, and anything that isn't a straightforward object shape. Both compile away to nothing at runtime either way — this is purely a compile-time, developer-ergonomics choice.

```typescript
interface Employee extends User {           // interface extension
    salary: number;
}

type EmployeeAlias = UserAlias & { salary: number; };   // type intersection — same practical effect
```

---

## 5. Functions

```typescript
function add(a: number, b: number): number {
    return a + b;
}

const multiply = (a: number, b: number): number => a * b;

function greet(name: string, greeting: string = "Hello"): string {   // default parameter
    return `${greeting}, ${name}`;
}

function sum(...nums: number[]): number {          // rest parameters, same idea as Go's variadic or Python's *args
    return nums.reduce((total, n) => total + n, 0);
}

function findUser(id: number): User | undefined {      // union return type — explicitly signals "might not find one"
    // ...
}

interface MathOp {
    (a: number, b: number): number;      // a function type, expressed as an interface with a call signature
}

const subtract: MathOp = (a, b) => a - b;
```

---

## 6. Union Types, Type Narrowing & Type Guards

```typescript
function printId(id: string | number) {
    if (typeof id === "string") {
        console.log(id.toUpperCase());     // TypeScript KNOWS id is a string here — narrowed
    } else {
        console.log(id.toFixed(2));           // and knows it's a number here
    }
}
```

This is TypeScript's own version of Rust's exhaustive `match` on an enum, or Go's type switch on an interface — the compiler tracks, at each point in your code, exactly which subset of a union type is actually possible, and narrows accordingly. `typeof`, `instanceof`, `in`, and equality checks against literal values (`if (status === "active")`) are all recognized narrowing patterns the compiler understands automatically.

```typescript
interface Cat { type: "cat"; meow(): void; }
interface Dog { type: "dog"; bark(): void; }
type Pet = Cat | Dog;

function makeSound(pet: Pet) {
    switch (pet.type) {          // "discriminated union" — a shared literal field tells TS which shape you have
        case "cat": pet.meow(); break;
        case "dog": pet.bark(); break;
    }
}
```

**Discriminated unions** are one of TypeScript's most genuinely powerful patterns — a shared literal field (`type: "cat"` vs `type: "dog"`) lets the compiler narrow the *entire rest of the object's shape* based on a single field check, closely mirroring the ergonomics of Rust's enum variants carrying different data, just built entirely out of plain object shapes and a discriminant field rather than a dedicated language construct.

```typescript
function isString(value: unknown): value is string {    // custom type guard — a user-defined narrowing function
    return typeof value === "string";
}

function process(value: unknown) {
    if (isString(value)) {
        console.log(value.toUpperCase());    // narrowed to string here, because of the custom guard above
    }
}
```

---

## 7. Generics

```typescript
function identity<T>(value: T): T {
    return value;
}

identity<string>("hello");    // T explicitly set to string
identity(42);                    // T inferred as number, no need to specify it

interface Box<T> {
    content: T;
}

const stringBox: Box<string> = { content: "hello" };

function firstElement<T>(arr: T[]): T | undefined {
    return arr[0];
}

function longest<T extends { length: number }>(a: T, b: T): T {   // constrained generic
    return a.length >= b.length ? a : b;
}
```

Same core idea as generics in every other language in this series — write one function or type that works across many concrete types, checked at compile time — but remember section 2's erasure rule: `T` has **zero** runtime existence in TypeScript. You cannot do `if (T === string)` inside a generic function; the generic parameter is purely a compile-time bookkeeping device, gone completely by the time the code actually runs.

---

## 8. Utility Types — The Built-In Type Transformers

```typescript
interface User {
    id: number;
    name: string;
    email: string;
}

type PartialUser = Partial<User>;       // { id?: number; name?: string; email?: string; } — all optional
type ReadonlyUser = Readonly<User>;         // every field becomes readonly
type UserNameOnly = Pick<User, "name" | "email">;    // only the listed keys
type UserWithoutEmail = Omit<User, "email">;             // everything EXCEPT the listed keys
type UserRecord = Record<string, User>;                     // a dictionary type — { [key: string]: User }

type UserKeys = keyof User;             // "id" | "name" | "email" — a union of the type's own key names
type EmailType = User["email"];             // string — extracting one property's type directly

function updateUser(id: number, updates: Partial<User>): void {
    // caller only needs to supply the fields they're actually changing
}
```

These exist to avoid retyping slightly-modified versions of the same shape over and over — `Partial<User>` for an update payload, `Omit<User, "id">` for a creation payload where the ID doesn't exist yet, `Pick<User, "name">` for a minimal display type — all **derived** from one source-of-truth type instead of hand-duplicated and inevitably drifting out of sync with it over time.

---

## 9. Classes

```typescript
class Animal {
    protected name: string;               // accessible in this class and subclasses, not outside
    private age: number;                     // accessible only within this exact class
    public species: string;                     // accessible everywhere (the default, if unspecified)
    readonly id: number;                          // settable once, in the constructor, never again

    constructor(name: string, age: number, species: string, id: number) {
        this.name = name;
        this.age = age;
        this.species = species;
        this.id = id;
    }

    speak(): string {
        return `${this.name} makes a sound`;
    }
}

class Dog extends Animal {
    constructor(name: string, age: number, id: number) {
        super(name, age, "Canine", id);
    }

    speak(): string {                  // override
        return `${this.name} barks`;
    }
}

abstract class Shape {                  // cannot be instantiated directly
    abstract area(): number;               // must be implemented by any concrete subclass

    describe(): string {
        return `Area: ${this.area()}`;
    }
}

class Circle extends Shape {
    constructor(private radius: number) {}    // constructor parameter properties — shorthand for declare + assign

    area(): number {
        return Math.PI * this.radius ** 2;
    }
}
```

`private radius: number` directly in the constructor's parameter list is TypeScript-specific shorthand — it declares the class field **and** assigns it from the constructor argument in one line, skipping the separate `this.radius = radius;` boilerplate every other language in this series requires explicitly.

```typescript
interface Flyable { fly(): void; }
interface Swimmable { swim(): void; }

class Duck implements Flyable, Swimmable {     // a class can implement multiple interfaces, same as Java
    fly() { console.log("flying"); }
    swim() { console.log("swimming"); }
}
```

---

## 10. Enums

```typescript
enum Direction {
    Up,       // 0
    Down,      // 1
    Left,       // 2
    Right,       // 3
}

enum Status {
    Active = "ACTIVE",       // string enum — explicit values, no implicit numbering
    Inactive = "INACTIVE",
}

let dir: Direction = Direction.Up;
```

Enums are a genuinely TypeScript-specific feature — they don't erase to nothing the way most type-only constructs do; a numeric enum actually generates real runtime JavaScript objects for the forward and reverse lookup. This is exactly why many style guides — and TypeScript's own more recent guidance — lean toward the **literal union type** from section 4 (`type Status = "active" | "inactive"`) instead: it achieves the same "restrict to one of these known values" goal, checked identically at compile time, but erases away completely at runtime with zero generated JavaScript, unlike a true `enum`.

---

## 11. Modules

```typescript
// user.ts
export interface User {
    name: string;
}

export function createUser(name: string): User {
    return { name };
}

export default class UserService { /* ... */ }
```

```typescript
// main.ts
import { User, createUser } from "./user";
import UserService from "./user";               // default export, any local name works
import * as UserModule from "./user";              // import everything under one namespace
```

Standard ES module syntax — TypeScript doesn't invent a new module system, it just type-checks whatever you're already importing and exporting.

---

## 12. Async/Await & Promises, Typed

```typescript
function fetchUser(id: number): Promise<User> {
    return fetch(`/api/users/${id}`).then(res => res.json());
}

async function getUser(id: number): Promise<User> {
    const response = await fetch(`/api/users/${id}`);
    const data: User = await response.json();
    return data;
}

async function getAllUsers(ids: number[]): Promise<User[]> {
    return Promise.all(ids.map(id => getUser(id)));
}
```

The type system doesn't change JavaScript's actual async execution model at all (still the same single-threaded event loop under the hood) — it just makes sure the **shape** of what a promise eventually resolves to is checked and known ahead of time, catching "you forgot to `await` this" and "this doesn't actually return what you think it returns" mistakes before runtime.

---

## 13. tsconfig.json — The Settings That Actually Matter

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "commonjs",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "noImplicitAny": true,
    "outDir": "./dist"
  }
}
```

**`"strict": true` is the single setting worth caring about most** — it's actually a bundle of several stricter checks turned on together (`noImplicitAny`, `strictNullChecks`, `strictFunctionTypes`, and others). Without it, TypeScript is considerably more lenient by default than most people assume — a variable with no annotation and no inferable type silently becomes `any` rather than raising an error, which quietly reintroduces the exact "no type safety" problem TypeScript exists to prevent. Turning `strict` off to "make errors go away" is almost always trading a real bug for temporary silence, not actually fixing anything.

---

## 14. Building a Basic Web Server (Express + TypeScript)

```bash
npm install express
npm install -D typescript @types/express @types/node
```

```typescript
import express, { Request, Response } from "express";

const app = express();
app.use(express.json());

interface User {
    id: number;
    name: string;
    email: string;
}

const users: Record<number, User> = {};

app.get("/users/:id", (req: Request, res: Response) => {
    const id = Number(req.params.id);
    const user = users[id];
    if (!user) {
        return res.status(404).json({ error: "user not found" });
    }
    res.json(user);
});

app.post("/users/:id", (req: Request, res: Response) => {
    const id = Number(req.params.id);
    const user: User = req.body;
    users[id] = user;
    res.status(201).json(user);
});

app.listen(3000, () => console.log("listening on port 3000"));
```

`@types/express` packages are what make this work at all — Express itself is written in plain JavaScript, and `@types/*` packages (the **DefinitelyTyped** project, an enormous community-maintained repository of type definitions for JavaScript libraries that don't ship their own types) provide the type information TypeScript needs to check code that calls into untyped JavaScript. This is a genuinely distinct wrinkle from every other language in this series — a huge fraction of "is this library well-typed" in the TypeScript ecosystem depends on whether a matching `@types` package exists and is kept up to date.

### Basic JWT auth, same shape as the rest of the series

```bash
npm install jsonwebtoken bcrypt
npm install -D @types/jsonwebtoken @types/bcrypt
```

```typescript
import jwt from "jsonwebtoken";

const SECRET = process.env.JWT_SECRET!;    // ! asserts to TS "trust me, this isn't undefined"

interface TokenPayload {
    userId: string;
    role: string;
}

function generateToken(payload: TokenPayload): string {
    return jwt.sign(payload, SECRET, { expiresIn: "1h" });
}

function verifyToken(token: string): TokenPayload {
    return jwt.verify(token, SECRET) as TokenPayload;    // `as` — a type assertion, checked by NOTHING at runtime
}
```

```typescript
import { Request, Response, NextFunction } from "express";

interface AuthRequest extends Request {
    user?: TokenPayload;
}

function authMiddleware(req: AuthRequest, res: Response, next: NextFunction) {
    const authHeader = req.headers.authorization;
    if (!authHeader?.startsWith("Bearer ")) {
        return res.status(401).json({ error: "missing or malformed token" });
    }
    try {
        req.user = verifyToken(authHeader.slice(7));
        next();
    } catch {
        return res.status(401).json({ error: "invalid or expired token" });
    }
}

function requireAdmin(req: AuthRequest, res: Response, next: NextFunction) {
    if (req.user?.role !== "admin") {
        return res.status(403).json({ error: "forbidden" });
    }
    next();
}

app.delete("/users/:id", authMiddleware, requireAdmin, (req: AuthRequest, res: Response) => {
    // ...
});
```

**The `!` and `as` in the snippets above are worth calling out explicitly, because they're both ways of telling the compiler "trust me" — and both bypass real safety when you're wrong.** The non-null assertion (`process.env.JWT_SECRET!`) tells TypeScript to stop worrying that this might be `undefined`, with zero actual runtime check backing that promise up — if the environment variable genuinely is missing, this fails at runtime exactly the way untyped JavaScript would have, just later, after you told the compiler not to worry about it. A type assertion (`as TokenPayload`) is the same trust-me move applied to reshaping a value's type — `jwt.verify` actually returns a much less specific type, and `as` is you overriding that with no runtime verification that the payload actually has the shape you claimed. Same non-negotiable list as every other closing web-server section in this series applies here too: real secret from an environment variable, always set an expiry, hash passwords with bcrypt, HTTPS in production.

---

## 15. Testing (Jest, Typed)

```typescript
import { add } from "./math";

describe("add", () => {
    it("adds two numbers", () => {
        expect(add(2, 3)).toBe(5);
    });

    it("handles negative numbers", () => {
        expect(add(-1, -1)).toBe(-2);
    });
});
```

```bash
npm install -D jest ts-jest @types/jest
npx jest
```

`ts-jest` type-checks your test files the same way it checks your application code — a test itself can fail to even compile if its types don't line up, catching a class of test-authoring mistakes plain JavaScript testing would only surface at actual test-run time, if at all.

---

## 16. What TypeScript Is Actually Made For

TypeScript was built at Microsoft, released in 2012, to solve a problem that had become undeniable by that point: **JavaScript, unmodified, does not scale well to large codebases with many contributors**, precisely because it has no compile-time type checking at all — every "this doesn't line up" bug is discovered only by actually running the broken code path, which for a large enough team, on a large enough codebase, happens constantly, expensively, and often in production.

That founding motivation shows up directly in where TypeScript has become the dominant, near-default choice:

- **Large-scale frontend applications** — React, Angular, and Vue codebases beyond a small scale overwhelmingly reach for TypeScript specifically because refactoring confidence (rename a field, and the compiler tells you every place that breaks) matters enormously once a codebase and team both grow past a certain size.
- **Node.js backends** — same argument, applied server-side; a typed request/response shape (as in section 14) catches an entire class of "the API contract silently changed" bugs before deployment rather than after.
- **Any JavaScript codebase maintained by more than a handful of people over more than a few months** — this is really the actual threshold where TypeScript's value proposition (compile-time safety, in exchange for writing type annotations) clearly outweighs its cost (more upfront typing, a build step where none previously existed).
- **Library and API design** — published npm packages increasingly ship their own TypeScript types (or a community-maintained `@types` package) specifically because consumers expect and rely on autocomplete and compile-time checking against a library's actual API surface.

What TypeScript is **not** particularly aimed at, and adds real friction if you reach for it there:

- Tiny scripts and quick prototypes — the ceremony of type annotations and a compile step is a genuine cost with little payoff at a scale where nothing is being refactored by a team, and plain JavaScript (or Python, from this series) usually wins here.
- Situations demanding runtime type safety — remember section 2's erasure: TypeScript's checks vanish completely by the time code actually runs, so it offers **zero** protection against a malformed API response, invalid user input, or any data crossing a genuine runtime boundary. Runtime validation libraries (Zod, io-ts) exist specifically to fill this real gap that TypeScript's compile-time-only model cannot cover on its own.
- Anywhere the underlying JavaScript performance ceiling itself is the actual constraint — TypeScript adds zero runtime overhead, but it also adds zero runtime speed; it's purely a development-time tool layered on the same JavaScript engine underneath, not a path to Go- or Rust-level raw performance.

The whole language reads as a direct, pragmatic answer to its founding problem: structural typing because it layers cleanly onto JavaScript's already-duck-typed object conventions rather than fighting against them, complete type erasure because backward compatibility with the existing JavaScript ecosystem and zero runtime cost were both non-negotiable constraints from day one, and a strictness level that's opt-in and configurable (section 13) because TypeScript needed to be adoptable incrementally, file by file, inside massive pre-existing JavaScript codebases — not a rewrite-everything-at-once proposition, but a gradual, layered fix for a real, well-documented, expensive problem.

---

*Pairs with the Go, Rust, Python, and Java cheat sheets in this same series — five languages, five different bets on when and how much a compiler should be trusted to catch your mistakes before your users do.*
