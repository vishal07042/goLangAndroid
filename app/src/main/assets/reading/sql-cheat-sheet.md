# The SQL Cheat Sheet: Queries to Query Plans

A reference doc — basics through window functions, plus the interview questions that show up everywhere. Keep it open next to any database that's ever disappointed you.

---

## 1. What Problem SQL Actually Solves

Before SQL, retrieving and manipulating data meant writing procedural code that described **how** to walk through records, one at a time — open this file, read this record, check this condition, write to this other file. Every question you wanted answered required a custom program to answer it.

SQL's actual innovation, dating back to Codd's 1970 relational model paper: **describe what data you want, not how to fetch it.** You write `SELECT name FROM users WHERE age > 18`, and the database engine — not you — figures out the actual retrieval strategy: which index to use, which order to scan tables in, whether to sort before or after filtering. This is a **declarative** language, the same fundamental distinction as Rust's `Result` vs. Go's manual error checks, just applied to data retrieval instead of error handling: you declare the desired outcome, the engine's query planner decides the mechanism.

This matters enormously in practice — it means the same SQL query can get dramatically faster over time purely because the database engine's query planner improved, or because someone added an index, with **zero changes to the query itself.** You're not describing a retrieval algorithm. You're describing a question, and trusting a very sophisticated piece of software to answer it efficiently.

---

## 2. Basic Setup & SELECT

```sql
CREATE TABLE users (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100),
    age INT,
    department_id INT
);

INSERT INTO users (id, name, email, age, department_id)
VALUES (1, 'Alice', 'alice@example.com', 30, 1);

SELECT * FROM users;                        -- everything, every column
SELECT name, email FROM users;                -- specific columns only
SELECT DISTINCT department_id FROM users;       -- unique values only
```

---

## 3. Filtering — WHERE

```sql
SELECT * FROM users WHERE age > 25;
SELECT * FROM users WHERE age >= 25 AND age <= 40;
SELECT * FROM users WHERE age BETWEEN 25 AND 40;      -- inclusive on both ends

SELECT * FROM users WHERE department_id IN (1, 2, 3);
SELECT * FROM users WHERE department_id NOT IN (1, 2);

SELECT * FROM users WHERE name LIKE 'A%';       -- starts with A
SELECT * FROM users WHERE name LIKE '%son';        -- ends with "son"
SELECT * FROM users WHERE name LIKE '%li%';          -- contains "li"
SELECT * FROM users WHERE email LIKE '_@%';            -- _ matches exactly one character

SELECT * FROM users WHERE department_id IS NULL;
SELECT * FROM users WHERE department_id IS NOT NULL;
```

**`= NULL` is always wrong, and it's the single most common beginner mistake in this whole document.** `NULL` represents "unknown," not a comparable value — `NULL = NULL` evaluates to `NULL` (not `true`), so a `WHERE column = NULL` clause matches literally nothing, ever, silently, with no error to warn you. Always use `IS NULL` / `IS NOT NULL`.

---

## 4. Sorting & Limiting

```sql
SELECT * FROM users ORDER BY age;                  -- ascending, the default
SELECT * FROM users ORDER BY age DESC;                -- descending
SELECT * FROM users ORDER BY department_id, age DESC;   -- multiple columns, each can have its own direction

SELECT * FROM users LIMIT 10;                      -- first 10 rows
SELECT * FROM users LIMIT 10 OFFSET 20;              -- skip 20, then take 10 — classic pagination
```

---

## 5. Aggregate Functions & GROUP BY

```sql
SELECT COUNT(*) FROM users;
SELECT COUNT(department_id) FROM users;    -- counts non-NULL values only — different from COUNT(*)
SELECT AVG(age) FROM users;
SELECT SUM(age) FROM users;
SELECT MIN(age), MAX(age) FROM users;

SELECT department_id, COUNT(*) AS employee_count
FROM users
GROUP BY department_id;

SELECT department_id, AVG(age) AS avg_age
FROM users
GROUP BY department_id
HAVING AVG(age) > 30;          -- filters GROUPS, not individual rows
```

**`WHERE` vs. `HAVING`, the distinction everyone mixes up once:** `WHERE` filters individual rows **before** grouping happens. `HAVING` filters entire groups **after** aggregation. You cannot write `WHERE AVG(age) > 30` — at the point `WHERE` executes, no averaging has happened yet, there's no aggregate to filter on. This ordering (filter rows, then group, then filter groups) is baked into SQL's actual logical execution order, not an arbitrary rule.

---

## 6. JOINs — The Concept That Actually Deserves a Diagram

```sql
-- sample tables
-- users: id, name, department_id
-- departments: id, name
```

### INNER JOIN — only rows that match on both sides

```sql
SELECT u.name, d.name AS department_name
FROM users u
INNER JOIN departments d ON u.department_id = d.id;
```

Users with a `department_id` that doesn't match any department get **excluded entirely**. Departments with no users get excluded entirely too. Only the overlap survives.

### LEFT JOIN — everything from the left table, matched data from the right where it exists

```sql
SELECT u.name, d.name AS department_name
FROM users u
LEFT JOIN departments d ON u.department_id = d.id;
```

Every user appears in the result, even ones with no matching department — for those, `department_name` comes back as `NULL`. This is the join you reach for whenever the question is "show me all of X, and whatever related Y exists, if any" — the single most commonly needed join type in real reporting queries, more often than INNER JOIN gets used in practice.

### RIGHT JOIN — the mirror image of LEFT JOIN

```sql
SELECT u.name, d.name AS department_name
FROM users u
RIGHT JOIN departments d ON u.department_id = d.id;
```

Every department appears, even ones with zero users. Rare in practice mostly because you can always rewrite a RIGHT JOIN as a LEFT JOIN by swapping which table you list first — most style guides prefer standardizing on LEFT JOIN everywhere for that reason, purely for consistency.

### FULL OUTER JOIN — everything from both sides, matched where possible

```sql
SELECT u.name, d.name AS department_name
FROM users u
FULL OUTER JOIN departments d ON u.department_id = d.id;
```

Unmatched rows from **either** side appear, with `NULL` filling in for whichever side didn't have a match. Not supported natively in MySQL (as of common versions) — usually emulated there with a `UNION` of a LEFT JOIN and a RIGHT JOIN.

### CROSS JOIN — every combination of every row, no matching condition at all

```sql
SELECT u.name, d.name
FROM users u
CROSS JOIN departments d;
```

If `users` has 10 rows and `departments` has 5, this returns 50 rows — every possible pairing. Rarely what you actually want by accident; genuinely useful for generating combinations on purpose (every product × every size, for instance).

### Self JOIN — a table joined to itself

```sql
SELECT e.name AS employee, m.name AS manager
FROM employees e
LEFT JOIN employees m ON e.manager_id = m.id;
```

Same table, aliased twice, treated as if it were two separate tables — the standard pattern for hierarchical data (employee/manager, category/parent-category) living in one flat table.

---

## 7. Subqueries

```sql
-- subquery in WHERE
SELECT name FROM users
WHERE department_id IN (SELECT id FROM departments WHERE name = 'Engineering');

-- subquery in SELECT (scalar subquery — must return exactly one value)
SELECT name, (SELECT COUNT(*) FROM orders WHERE orders.user_id = users.id) AS order_count
FROM users;

-- subquery in FROM (derived table)
SELECT dept_avg.department_id, dept_avg.avg_age
FROM (
    SELECT department_id, AVG(age) AS avg_age
    FROM users
    GROUP BY department_id
) AS dept_avg
WHERE dept_avg.avg_age > 30;

-- correlated subquery — references the OUTER query, re-runs per outer row
SELECT name, age FROM users u
WHERE age > (SELECT AVG(age) FROM users WHERE department_id = u.department_id);
```

A **correlated subquery** is meaningfully different from the others — it references a column from the outer query, which means, conceptually, it re-executes once per row of the outer query rather than running once as a standalone unit. Correct, but potentially slow at scale — often rewritable as a JOIN or a window function (section 9) for better performance on large tables.

---

## 8. CTEs — Common Table Expressions (WITH clause)

```sql
WITH department_averages AS (
    SELECT department_id, AVG(age) AS avg_age
    FROM users
    GROUP BY department_id
)
SELECT u.name, u.age, da.avg_age
FROM users u
JOIN department_averages da ON u.department_id = da.department_id
WHERE u.age > da.avg_age;
```

A CTE is a named, temporary result set, scoped to just the one query that follows it — functionally similar to the subquery-in-FROM pattern above, but readable top-to-bottom instead of nested inside-out, and reusable multiple times within the same query without repeating the logic. Once a query has more than one or two nested subqueries, CTEs are almost always the more maintainable choice — this is a readability and structure win more than a performance one in most engines.

### Recursive CTEs — for hierarchical or graph-like data

```sql
WITH RECURSIVE org_chart AS (
    -- anchor: the top of the hierarchy
    SELECT id, name, manager_id, 0 AS level
    FROM employees
    WHERE manager_id IS NULL

    UNION ALL

    -- recursive step: join back to the CTE itself, one level down each time
    SELECT e.id, e.name, e.manager_id, oc.level + 1
    FROM employees e
    JOIN org_chart oc ON e.manager_id = oc.id
)
SELECT * FROM org_chart ORDER BY level;
```

This is genuinely the only clean way to walk an arbitrary-depth hierarchy (org charts, category trees, bill-of-materials structures) in pure SQL without knowing the depth ahead of time — the query keeps joining the CTE back to itself, one level deeper each pass, until no new rows are produced.

---

## 9. Window Functions — The Feature That Separates "Knows SQL" from "Actually Knows SQL"

```sql
SELECT
    name,
    department_id,
    salary,
    ROW_NUMBER() OVER (PARTITION BY department_id ORDER BY salary DESC) AS rank_in_dept,
    RANK() OVER (PARTITION BY department_id ORDER BY salary DESC) AS rank_with_ties,
    DENSE_RANK() OVER (PARTITION BY department_id ORDER BY salary DESC) AS dense_rank_with_ties,
    AVG(salary) OVER (PARTITION BY department_id) AS dept_avg_salary,
    salary - LAG(salary) OVER (PARTITION BY department_id ORDER BY salary) AS diff_from_prev,
    LEAD(salary) OVER (PARTITION BY department_id ORDER BY salary) AS next_salary,
    SUM(salary) OVER (PARTITION BY department_id ORDER BY salary) AS running_total
FROM employees;
```

**The core idea window functions solve:** normally, `GROUP BY` collapses many rows into one summary row per group — you lose the individual rows entirely. Window functions compute an aggregate-like value **per row**, using a defined "window" of related rows, **without collapsing anything** — every original row survives, now annotated with something computed across its group.

- `ROW_NUMBER()` — a unique sequential number per row within its partition, no ties, ever (1, 2, 3, 4...).
- `RANK()` — same value gets the same rank, but leaves gaps afterward (1, 2, 2, 4 — skips 3 because two rows tied for 2nd).
- `DENSE_RANK()` — same value gets the same rank, no gaps (1, 2, 2, 3).
- `LAG()` / `LEAD()` — look at the previous / next row's value within the same partition — the standard tool for period-over-period comparisons (this month vs. last month) without a self-join.
- Aggregate functions (`AVG`, `SUM`, etc.) `OVER (...)` — compute the aggregate but keep every individual row instead of collapsing them, and with `ORDER BY` inside the window, produce a **running total** instead of a single grand total.

`PARTITION BY` is doing the same conceptual job as `GROUP BY` here — dividing rows into buckets — except the rows aren't actually collapsed into one row per bucket afterward.

---

## 10. UNION, INTERSECT, EXCEPT

```sql
SELECT name FROM current_employees
UNION
SELECT name FROM former_employees;          -- combines both, removes duplicates

SELECT name FROM current_employees
UNION ALL
SELECT name FROM former_employees;          -- combines both, KEEPS duplicates — faster, no dedup work

SELECT name FROM current_employees
INTERSECT
SELECT name FROM former_employees;          -- only names present in BOTH

SELECT name FROM current_employees
EXCEPT
SELECT name FROM former_employees;          -- names in the first set, NOT in the second
```

**`UNION` vs `UNION ALL`:** `UNION` deduplicates, which requires an actual sort/hash step under the hood — genuinely more expensive at scale. If you already know there's no overlap, or duplicates are fine for your purpose, `UNION ALL` is strictly faster and should be the default unless deduplication is specifically the point.

---

## 11. Indexes — Why Some Queries Are Instant and Others Aren't

```sql
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_dept_age ON users(department_id, age);   -- composite index, column order matters
DROP INDEX idx_users_email;
```

Without an index, finding a row matching a `WHERE` condition means a **full table scan** — checking every single row, one at a time, the database equivalent of finding a name in a phone book by reading it cover to cover. An index is a separate, sorted data structure (a B-tree, in most engines) that lets the database jump almost directly to matching rows instead — the actual phone-book-alphabetical-order trick, maintained automatically as data changes.

The tradeoff, always worth stating plainly: indexes make **reads** faster and **writes** slightly slower — every `INSERT`/`UPDATE`/`DELETE` now also has to update every index on that table, not just the table's own data. Index the columns you filter, join, and sort on frequently; don't reflexively index every column, especially on tables with heavy write traffic.

```sql
EXPLAIN SELECT * FROM users WHERE email = 'alice@example.com';
```

`EXPLAIN` (or `EXPLAIN ANALYZE` for actual runtime numbers, not just the plan) shows you the query planner's actual execution strategy — whether it used an index or fell back to a full scan, in what order it joined tables, roughly how many rows it expects at each step. This is the actual tool for "why is this query slow," not guessing.

---

## 12. Transactions & ACID

```sql
BEGIN;

UPDATE accounts SET balance = balance - 100 WHERE id = 1;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;

COMMIT;      -- or ROLLBACK; to undo everything since BEGIN
```

A transaction groups multiple statements into one all-or-nothing unit — if anything fails partway through, `ROLLBACK` undoes everything already done in that transaction, so you never end up in a state where money left one account and never arrived in the other. The classic ACID guarantees this is built on:

- **Atomicity** — the whole transaction succeeds, or none of it does.
- **Consistency** — the database moves from one valid state to another, never leaving data in a state that violates its own constraints.
- **Isolation** — concurrent transactions don't see each other's uncommitted, in-progress changes.
- **Durability** — once committed, the change survives, even if the server crashes a millisecond later.

---

## 13. Normalization — Organizing Tables to Avoid Data Chaos

- **1NF (First Normal Form):** every column holds a single, atomic value — no comma-separated lists crammed into one cell.
- **2NF:** 1NF, plus every non-key column depends on the **entire** primary key, not just part of it (relevant for composite keys).
- **3NF:** 2NF, plus no non-key column depends on another non-key column — every column depends directly on the key, the whole key, and nothing but the key.

The practical payoff of normalization: avoiding **update anomalies** — if a customer's address is duplicated across a hundred order rows instead of stored once in a `customers` table, updating their address means updating a hundred rows, and missing even one leaves your data quietly inconsistent with itself. Normalization trades some query complexity (more joins) for data integrity (one source of truth per fact). **Denormalization** — deliberately duplicating data back in — is a legitimate, common performance tradeoff for read-heavy systems where join cost matters more than update convenience, done knowingly, not by accident.

---

## 14. Important SQL Interview Questions

The recurring set that shows up across data analyst, backend, and data engineering interviews — worth being able to write cold, not just recognize.

### Q1: Find the second-highest salary

```sql
SELECT MAX(salary) FROM employees
WHERE salary < (SELECT MAX(salary) FROM employees);

-- or, more general, works for Nth highest:
SELECT DISTINCT salary FROM employees
ORDER BY salary DESC
LIMIT 1 OFFSET 1;

-- window function version, handles ties more explicitly:
SELECT salary FROM (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM employees
) ranked
WHERE rnk = 2;
```

### Q2: Find duplicate rows in a table

```sql
SELECT email, COUNT(*) AS occurrences
FROM users
GROUP BY email
HAVING COUNT(*) > 1;
```

### Q3: Delete duplicate rows, keeping only one copy

```sql
DELETE FROM users
WHERE id NOT IN (
    SELECT MIN(id) FROM users GROUP BY email
);
```

### Q4: Find employees who earn more than their manager

```sql
SELECT e.name AS employee, e.salary, m.name AS manager, m.salary AS manager_salary
FROM employees e
JOIN employees m ON e.manager_id = m.id
WHERE e.salary > m.salary;
```

### Q5: Find the Nth highest value per group

```sql
SELECT * FROM (
    SELECT name, department_id, salary,
           DENSE_RANK() OVER (PARTITION BY department_id ORDER BY salary DESC) AS rnk
    FROM employees
) ranked
WHERE rnk = 2;      -- 2nd highest salary WITHIN each department
```

### Q6: Running total / cumulative sum

```sql
SELECT date, amount,
       SUM(amount) OVER (ORDER BY date) AS running_total
FROM transactions;
```

### Q7: Find users with no orders (classic anti-join pattern)

```sql
SELECT u.name
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE o.id IS NULL;

-- equivalent, sometimes clearer:
SELECT name FROM users
WHERE id NOT IN (SELECT user_id FROM orders WHERE user_id IS NOT NULL);
```

The `NOT IN` version has a genuine, well-known trap: if the subquery can return even one `NULL`, `NOT IN` silently returns **zero rows** for the entire query, because `x NOT IN (1, 2, NULL)` evaluates to unknown for every `x`, not true. The `WHERE user_id IS NOT NULL` guard above exists specifically to dodge that — or just use the LEFT JOIN version, which doesn't have this trap at all.

### Q8: Pivot rows into columns (conditional aggregation)

```sql
SELECT
    department_id,
    SUM(CASE WHEN gender = 'M' THEN 1 ELSE 0 END) AS male_count,
    SUM(CASE WHEN gender = 'F' THEN 1 ELSE 0 END) AS female_count
FROM employees
GROUP BY department_id;
```

### Q9: Find consecutive dates / detect gaps in a sequence

```sql
SELECT id, date,
       date - ROW_NUMBER() OVER (ORDER BY date)::int AS grp
FROM events;
-- rows with the same "grp" value belong to the same unbroken run of consecutive dates
```

This one relies on a genuinely clever trick worth understanding rather than memorizing: for a truly consecutive run of dates, `date` minus its own row number (in date order) stays **constant** across the whole run — because both the date and the row number increase by exactly 1 together. The moment there's a gap, that difference jumps to a new value, cleanly marking the start of a new group.

### Q10: Calculate month-over-month percentage growth

```sql
SELECT
    month,
    revenue,
    LAG(revenue) OVER (ORDER BY month) AS prev_month_revenue,
    ROUND(
        (revenue - LAG(revenue) OVER (ORDER BY month)) * 100.0
        / LAG(revenue) OVER (ORDER BY month), 2
    ) AS growth_pct
FROM monthly_revenue;
```

---

## 15. Quick Reference: Logical Query Execution Order

```
FROM  ->  WHERE  ->  GROUP BY  ->  HAVING  ->  SELECT  ->  ORDER BY  ->  LIMIT
```

This is worth memorizing on its own, separately from any specific query — it explains, from first principles, every "why can't I do X" moment in this document: why `WHERE` can't reference an alias defined in `SELECT` (SELECT hasn't run yet), why `HAVING` can filter on aggregates but `WHERE` can't (grouping hasn't happened yet at the `WHERE` stage), and why `ORDER BY` is one of the only clauses that *can* reference a `SELECT` alias (it's nearly the last thing to run). SQL is written top-to-bottom in the order you type it, but it does **not execute** top-to-bottom in that same order — this table is the actual execution order underneath.

---

*Pairs with the rest of the technical cheat sheet series — same reference-doc format, same "why does this exist, then how do you actually use it" structure.*
