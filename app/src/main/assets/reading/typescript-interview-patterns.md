# The TypeScript Interview Cheat Sheet: Patterns & Problems

A companion to the main TypeScript cheat sheet — the type-level tricks and coding questions that actually come up, with working solutions.

---

## 1. Implement Utility Types From Scratch

Interviewers love these because they test whether you understand *how* `Partial`, `Pick`, etc. actually work under the hood, not just that you can import them.

### MyPartial<T> — make every property optional

```typescript
type MyPartial<T> = {
    [K in keyof T]?: T[K];
};

interface User { id: number; name: string; }
type PartialUser = MyPartial<User>;   // { id?: number; name?: string; }
```

This is a **mapped type** — `[K in keyof T]` iterates over every key of `T`, and `?:` marks each resulting property optional. `keyof T` produces a union of `T`'s key names (`"id" | "name"`); the mapped type then rebuilds an object type by looping over that union.

### MyReadonly<T> — make every property readonly

```typescript
type MyReadonly<T> = {
    readonly [K in keyof T]: T[K];
};
```

### MyPick<T, K> — select a subset of keys

```typescript
type MyPick<T, K extends keyof T> = {
    [P in K]: T[P];
};

type UserName = MyPick<User, "name">;   // { name: string; }
```

`K extends keyof T` is a **generic constraint** — it forces `K` to actually be one of `T`'s real key names, so `MyPick<User, "nonexistent">` fails to compile instead of silently producing garbage.

### MyOmit<T, K> — the inverse of Pick

```typescript
type MyOmit<T, K extends keyof T> = MyPick<T, Exclude<keyof T, K>>;
```

`Exclude<A, B>` removes every member of union `A` that's also in `B` — so `Exclude<keyof T, K>` computes "every key of T except the ones in K," then hands that surviving key set to `MyPick`. This is the pattern worth remembering: a lot of utility types are built by composing a small handful of primitives (`keyof`, mapped types, conditional types) rather than each needing its own from-scratch mechanism.

### MyRecord<K, V> — the dictionary type

```typescript
type MyRecord<K extends string | number | symbol, V> = {
    [P in K]: V;
};
```

---

## 2. Conditional Types — TypeScript's `if` Statement, at the Type Level

```typescript
type IsString<T> = T extends string ? true : false;

type A = IsString<"hello">;   // true
type B = IsString<42>;          // false
```

`extends ? :` at the type level checks "does this type match this shape," and branches — the direct type-system equivalent of an `if`/`else`, just evaluated by the compiler instead of at runtime.

### Extract & Exclude, implemented

```typescript
type MyExtract<T, U> = T extends U ? T : never;
type MyExclude<T, U> = T extends U ? never : T;

type Status = "active" | "inactive" | "pending";
type ActiveOnly = MyExtract<Status, "active">;      // "active"
type NotActive = MyExclude<Status, "active">;         // "inactive" | "pending"
```

**The genuinely subtle part interviewers probe for:** when `T` in `T extends U ? X : Y` is itself a union (like `Status` above), the conditional type **distributes** across each member of the union individually, rather than checking the whole union at once. This is called a **distributive conditional type**, and it's *why* `MyExclude` works at all — TypeScript effectively checks `"active" extends "active" ? never : "active"`, then `"inactive" extends "active" ? never : "inactive"`, and so on, for every member separately, then unions the surviving results back together.

### ReturnType<T> and Parameters<T>, implemented

```typescript
type MyReturnType<T extends (...args: any[]) => any> = T extends (...args: any[]) => infer R ? R : never;

function getUser(): { id: number; name: string } {
    return { id: 1, name: "Alice" };
}

type UserReturn = MyReturnType<typeof getUser>;   // { id: number; name: string }
```

`infer R` is the key mechanic here — inside a conditional type, `infer` lets you **capture** a piece of the type you're matching against and bind it to a new type variable, usable in the "true" branch. It's the type-system equivalent of a regex capture group: you're not just checking "does this shape match," you're pulling a specific part of the matched shape out and naming it.

```typescript
type MyParameters<T extends (...args: any[]) => any> = T extends (...args: infer P) => any ? P : never;

type GetUserParams = MyParameters<(id: number, includeDeleted: boolean) => void>;
// [number, boolean]
```

---

## 3. Template Literal Types — String Manipulation at the Type Level

```typescript
type Greeting = `Hello, ${string}`;

const g1: Greeting = "Hello, Alice";   // fine
const g2: Greeting = "Hi, Alice";        // ERROR — doesn't match the template

type EventName<T extends string> = `on${Capitalize<T>}`;

type ClickEvent = EventName<"click">;   // "onClick"
type HoverEvent = EventName<"hover">;     // "onHover"
```

Template literal types let you build and constrain string types the way template literals build actual strings at runtime — combined with `Capitalize`, `Uncapitalize`, `Uppercase`, `Lowercase` (all built-in), this is the mechanism behind strongly-typed event names, typed route paths, and typed CSS-in-JS property names in real production libraries.

### A realistic interview flavor: type-safe object path getter

```typescript
type PathKeys<T> = T extends object
    ? { [K in keyof T]: K extends string
        ? T[K] extends object
            ? K | `${K}.${PathKeys<T[K]>}`
            : K
        : never
      }[keyof T]
    : never;

interface Nested {
    user: {
        name: string;
        address: {
            city: string;
        };
    };
}

type Paths = PathKeys<Nested>;
// "user" | "user.name" | "user.address" | "user.address.city"
```

This one's genuinely advanced — it recursively walks a nested object type and builds a union of every valid dot-notation path through it, which is exactly the type backing a type-safe `get(obj, "user.address.city")` helper. Rarely asked to be written cold in an interview, but recognizing the recursive-conditional-type-plus-template-literal pattern is a strong signal you actually understand how the pieces compose.

---

## 4. Deep Partial / Deep Readonly — Recursive Mapped Types

```typescript
type DeepPartial<T> = T extends object
    ? { [K in keyof T]?: DeepPartial<T[K]> }
    : T;

interface Config {
    server: {
        host: string;
        port: number;
    };
    debug: boolean;
}

type PartialConfig = DeepPartial<Config>;
// { server?: { host?: string; port?: number; }; debug?: boolean; }
```

Built-in `Partial<T>` only makes the **top-level** properties optional — nested objects stay fully required. This recursive version checks `T extends object` and, if true, recurses into every property with `DeepPartial` again instead of just `?:`, continuing all the way down until it hits a primitive (`T extends object` fails, and the recursion bottoms out by just returning `T` unchanged). This exact pattern — check if it's an object, if so recurse, if not return as-is — is the backbone of `DeepReadonly`, `DeepRequired`, and most other "deep" utility type variants you'll be asked to reason about.

---

## 5. Function Overloading

```typescript
function createElement(tag: "img"): HTMLImageElement;
function createElement(tag: "input"): HTMLInputElement;
function createElement(tag: string): HTMLElement;
function createElement(tag: string): HTMLElement {
    return document.createElement(tag);
}

const img = createElement("img");     // typed as HTMLImageElement, specifically
const input = createElement("input");   // typed as HTMLInputElement, specifically
```

Multiple signatures declared above one actual implementation — the implementation signature itself (the last one) must be general enough to satisfy every overload above it, but callers only ever see and get autocomplete against the specific overloads, not the broad implementation signature. A classic interview follow-up: "why not just use a union return type?" — because a union (`HTMLImageElement | HTMLInputElement`) would force every caller to narrow the result themselves even when they passed a specific, known literal tag; overloading lets the compiler give back the exact specific type immediately, based on what was actually passed in.

---

## 6. The Builder Pattern, Typed

```typescript
class QueryBuilder<T> {
    private conditions: string[] = [];

    where(condition: string): this {      // returning `this` — the type-safe key to chainability
        this.conditions.push(condition);
        return this;
    }

    build(): string {
        return this.conditions.join(" AND ");
    }
}

const query = new QueryBuilder()
    .where("age > 18")
    .where("active = true")
    .build();
```

Returning `this` (not just some generic object, the literal `this` type) is what keeps method chaining fully type-safe through subclassing too — a subclass calling an inherited chainable method still gets back the subclass's own type, not the parent class's, which a hardcoded return type would break.

---

## 7. Discriminated Unions for State Machines — a Very Common "Design This" Prompt

```typescript
type RequestState<T> =
    | { status: "idle" }
    | { status: "loading" }
    | { status: "success"; data: T }
    | { status: "error"; error: string };

function render<T>(state: RequestState<T>) {
    switch (state.status) {
        case "idle":
            return "Not started";
        case "loading":
            return "Loading...";
        case "success":
            return `Data: ${JSON.stringify(state.data)}`;    // TS knows `.data` exists ONLY in this branch
        case "error":
            return `Error: ${state.error}`;                     // and `.error` exists ONLY in this branch
    }
}
```

This is the pattern interviewers are really fishing for when they ask "how would you model a network request's state" — a discriminated union makes it **structurally impossible** to accidentally read `.data` while `status` is `"error"`, because that combination doesn't exist as a valid type at all. Compare this against the common bad answer — one flat interface with every field optional (`data?`, `error?`, `isLoading?`) — which compiles fine but allows nonsensical states (`isLoading: true` and `data` both set at once) that the type system does nothing to prevent.

---

## 8. Common "Spot the Bug" Interview Questions

### Q: What's wrong here?

```typescript
interface User { name: string; age: number; }

function printAge(user: User) {
    console.log(user.age.toFixed(2));
}

const partialUser = { name: "Alice" } as User;   // "as" forces it through, unchecked
printAge(partialUser);    // runtime crash: user.age is undefined, .toFixed() doesn't exist on undefined
```

**The answer:** `as User` is a type assertion, not a validation — it tells the compiler "trust me, this is a `User`," with zero runtime check backing that claim. The object genuinely doesn't have an `age` field, TypeScript has no way to know that at compile time once you've asserted past it, and the crash happens at runtime exactly the way it would in plain JavaScript. This is the single most common "gotcha" pattern in TS interviews — testing whether you understand that `as` bypasses safety rather than provides it.

### Q: Why does this not narrow the way you'd expect?

```typescript
interface Config { timeout?: number; }

function process(config: Config) {
    if (config.timeout) {
        setTimeout(() => {}, config.timeout);   // fine
    }

    const fn = () => {
        if (config.timeout) {
            setTimeout(() => {}, config.timeout);  // ERROR in some TS configs: 'config.timeout' possibly undefined
        }
    };
}
```

**The answer:** TypeScript's narrowing (section 6 of the main cheat sheet) only holds as long as the compiler can prove nothing could have changed the value in between the check and the use. Once you're inside a **closure** that could run at some arbitrary later time, TypeScript can no longer guarantee `config.timeout` is still what it was at the time of the `if` check — something else could theoretically mutate `config` before that inner function actually runs. The fix is to capture the narrowed value into a local `const` right after the check:

```typescript
if (config.timeout) {
    const timeout = config.timeout;    // captured, immutable, safe to use inside any closure
    const fn = () => setTimeout(() => {}, timeout);
}
```

### Q: What's the actual difference between these two, and which is safer?

```typescript
function process(data: any) { data.whatever(); }        // compiles, crashes at runtime if wrong
function process2(data: unknown) { data.whatever(); }     // does NOT compile — forces you to check first
```

**The answer:** covered in the main cheat sheet, but it's asked constantly as a standalone question — `any` disables checking entirely, `unknown` requires narrowing before use. `process2` won't even compile until you add a check (`typeof data === "object" && data !== null && "whatever" in data`, or a type guard), which is precisely the point — `unknown` forces the safety `any` lets you skip.

---

## 9. Practical Coding Problems (Not Just Type-Level)

### Debounce, typed

```typescript
function debounce<T extends (...args: any[]) => void>(fn: T, delay: number): (...args: Parameters<T>) => void {
    let timeoutId: ReturnType<typeof setTimeout>;
    return (...args: Parameters<T>) => {
        clearTimeout(timeoutId);
        timeoutId = setTimeout(() => fn(...args), delay);
    };
}

const debouncedSearch = debounce((query: string) => console.log("searching:", query), 300);
```

`Parameters<T>` (from section 2) is what lets the returned wrapper function accept exactly the same argument types as whatever function was passed in, fully type-checked, without hardcoding or duplicating the original function's signature anywhere.

### A type-safe event emitter

```typescript
type EventMap = {
    login: { userId: string };
    logout: undefined;
    error: { message: string; code: number };
};

class TypedEmitter<T extends Record<string, any>> {
    private listeners: { [K in keyof T]?: Array<(payload: T[K]) => void> } = {};

    on<K extends keyof T>(event: K, callback: (payload: T[K]) => void) {
        if (!this.listeners[event]) this.listeners[event] = [];
        this.listeners[event]!.push(callback);
    }

    emit<K extends keyof T>(event: K, payload: T[K]) {
        this.listeners[event]?.forEach(cb => cb(payload));
    }
}

const emitter = new TypedEmitter<EventMap>();
emitter.on("login", (payload) => console.log(payload.userId));    // payload is correctly typed as { userId: string }
emitter.emit("login", { userId: "abc123" });
emitter.emit("login", { userId: 123 });    // ERROR — userId must be a string, caught at compile time
```

This is a genuinely strong interview answer for "design a type-safe pub/sub system" — the generic `EventMap` parameter means every event name and its exact payload shape is checked together, so calling `emit` or `on` with a mismatched event name or wrong payload shape fails to compile, instead of silently misfiring at runtime the way an untyped `EventEmitter` would.

### Implementing a simple `Pick`-based form validator

```typescript
type Validator<T> = {
    [K in keyof T]: (value: T[K]) => boolean;
};

function validate<T>(obj: T, validators: Validator<T>): boolean {
    return (Object.keys(validators) as (keyof T)[]).every(key => validators[key](obj[key]));
}

interface SignupForm { email: string; age: number; }

const isValid = validate(
    { email: "a@b.com", age: 25 },
    {
        email: (v) => v.includes("@"),
        age: (v) => v >= 18,
    }
);
```

The interesting bit interviewers listen for here: `Object.keys()` in TypeScript always returns `string[]`, not `(keyof T)[]`, because JavaScript objects can technically have extra keys at runtime that the type system doesn't know about — hence the explicit `as (keyof T)[]` cast, which is a deliberate, narrow, justified use of a type assertion (unlike the "spot the bug" example in section 8), because you genuinely do control the shape here and know the cast is safe.

---

## 10. Quick Reference: The Type-Level Toolkit

```typescript
keyof T                    // union of T's key names
T[K]                        // the type of property K on T
T extends U ? X : Y          // conditional type — type-level if/else
infer R                        // capture a piece of a matched type inside a conditional type
[K in keyof T]: ...              // mapped type — loop over T's keys, build a new object type
{ [K in keyof T as NewKey]: ... }   // mapped type with key remapping (Go/TS 4.1+)
`prefix${string}`                     // template literal type
A & B                                    // intersection — combine two types
A | B                                     // union — either type
```

---

*Companion to the main TypeScript cheat sheet in this series — that one covers the language day-to-day, this one covers what you'll actually get asked to reason about live, on a call, under time pressure.*
