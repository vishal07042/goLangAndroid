# The Pandas Cheat Sheet: Load to Insight

Reference doc. Basics first, then the patterns that actually come up in analysis work and interviews. SQL equivalents are in section 13.

```python
import pandas as pd
import numpy as np
```

---

## 1. Mental Model

- **Series:** one labeled column (values + index).
- **DataFrame:** a dict of Series sharing one index. Columns can have different dtypes.
- **Index:** labels for rows. Operations **align on index**, not position. This is the source of most surprises.
- **Vectorize:** operate on whole columns. A Python `for` loop over rows is almost always the wrong tool.

```python
df["price"] * df["qty"]        # vectorized, fast
df.apply(lambda r: r.price * r.qty, axis=1)   # row-wise, slow, avoid
```

---

## 2. Creating & Loading Data

```python
df = pd.DataFrame({"name": ["Ann", "Bob"], "age": [30, 25]})
df = pd.DataFrame([{"a": 1, "b": 2}, {"a": 3, "b": 4}])   # list of dicts

df = pd.read_csv("file.csv")
df = pd.read_csv("file.csv",
                 usecols=["id", "date", "amount"],     # load only what you need
                 dtype={"id": "int32"},
                 parse_dates=["date"],
                 na_values=["", "NA", "-"],
                 nrows=1000,                           # peek at a big file
                 chunksize=100_000)                    # returns an iterator of DataFrames

df = pd.read_excel("file.xlsx", sheet_name="Sheet1")
df = pd.read_json("file.json")
df = pd.read_parquet("file.parquet")                   # fast, typed, preferred for storage
df = pd.read_sql("SELECT * FROM users", conn)

df.to_csv("out.csv", index=False)                      # index=False avoids a junk column
df.to_parquet("out.parquet")
```

Prefer **Parquet** over CSV for anything you reload often: smaller, faster, keeps dtypes.

---

## 3. Inspecting

```python
df.head(); df.tail(); df.sample(5)
df.shape                    # (rows, cols)
df.info()                   # dtypes, non-null counts, memory
df.describe()               # numeric summary
df.describe(include="object")
df.dtypes
df.columns; df.index
df.isna().sum()             # missing per column
df["col"].value_counts(dropna=False)
df["col"].nunique()
df.memory_usage(deep=True)
```

First five commands on any new dataset: `shape`, `info()`, `head()`, `isna().sum()`, `describe()`.

---

## 4. Selecting Data

```python
df["a"]                     # Series
df[["a", "b"]]              # DataFrame (note double brackets)

df.loc[row_labels, col_labels]     # label-based, slice endpoints INCLUSIVE
df.iloc[row_pos, col_pos]          # position-based, endpoints EXCLUSIVE

df.loc[0:3, "a":"c"]
df.loc[df["age"] > 25, ["name", "age"]]
df.iloc[:5, :2]

df.at[3, "a"]               # fast single-cell access by label
df.iat[3, 0]                # by position
```

### Boolean filtering

```python
df[df["age"] > 25]
df[(df["age"] > 25) & (df["city"] == "NYC")]     # & | ~ , NOT and/or/not
df[df["city"].isin(["NYC", "LA"])]
df[~df["city"].isin(["NYC"])]
df[df["name"].str.contains("an", case=False, na=False)]
df[df["age"].between(20, 30)]
df.query("age > 25 and city == 'NYC'")           # readable alternative
df.query("age > @min_age")                        # @ references a Python variable
```

Every condition needs **parentheses** when combining with `&` / `|`, because of operator precedence.

---

## 5. Cleaning

### Missing values

```python
df.isna(); df.notna()
df.dropna()                          # drop rows with ANY null
df.dropna(subset=["a", "b"])         # only consider these columns
df.dropna(how="all")
df.fillna(0)
df.fillna({"age": df["age"].median(), "city": "unknown"})
df["a"] = df["a"].ffill()            # forward fill (carry last value down)
df["a"] = df["a"].bfill()
df["a"] = df["a"].interpolate()      # numeric/time interpolation
```

Deciding what to do with nulls is an analysis decision, not a cleanup chore. Ask whether missing means "zero," "unknown," or "not applicable."

### Types

```python
df["a"] = df["a"].astype("int32")
df["a"] = pd.to_numeric(df["a"], errors="coerce")      # bad values become NaN
df["d"] = pd.to_datetime(df["d"], errors="coerce")
df["c"] = df["c"].astype("category")                    # big memory win for low-cardinality strings
df = df.convert_dtypes()                                # nullable dtypes (Int64, string, boolean)
```

`int` columns containing NaN become `float`. Use nullable `Int64` (capital I) to keep integers.

### Duplicates

```python
df.duplicated().sum()
df.duplicated(subset=["email"], keep=False)    # mark ALL copies, not just later ones
df.drop_duplicates(subset=["email"], keep="last")
```

### Rename, drop, reorder

```python
df.rename(columns={"old": "new"})
df.columns = df.columns.str.strip().str.lower().str.replace(" ", "_")   # normalize names
df.drop(columns=["a", "b"])
df.drop(index=[0, 1])
df = df[["b", "a", "c"]]
df.reset_index(drop=True)
df.set_index("id")
```

### Strings (`.str`)

```python
s.str.lower(); s.str.upper(); s.str.strip(); s.str.title()
s.str.replace("a", "b", regex=False)
s.str.contains("x", na=False)
s.str.startswith("ab")
s.str.split(",", expand=True)            # to multiple columns
s.str.extract(r"(\d+)")                  # capture group to column
s.str.len()
s.str.cat(sep=", ")
```

### Outliers (IQR)

```python
q1, q3 = df["x"].quantile([0.25, 0.75])
iqr = q3 - q1
mask = df["x"].between(q1 - 1.5 * iqr, q3 + 1.5 * iqr)
clean = df[mask]
```

---

## 6. Creating & Transforming Columns

```python
df["total"] = df["price"] * df["qty"]
df = df.assign(total=lambda d: d["price"] * d["qty"])    # chainable, returns a new frame

df["tier"] = np.where(df["spend"] > 100, "high", "low")
df["band"] = np.select(
    [df["age"] < 18, df["age"] < 65],
    ["minor", "adult"],
    default="senior")

df["bucket"] = pd.cut(df["age"], bins=[0, 18, 65, 120], labels=["young", "adult", "senior"])
df["quartile"] = pd.qcut(df["spend"], 4, labels=False)   # equal-sized groups

df["x"].map({"a": 1, "b": 2})            # dict/Series lookup, elementwise
df["x"].apply(func)                      # elementwise on a Series
df.apply(func, axis=0)                   # per column
df.apply(func, axis=1)                   # per row (slow)
df["x"].clip(lower=0, upper=100)
df["x"].replace({0: np.nan})
```

Speed order for transforming a column: **vectorized ops** > `np.where`/`np.select` > `map` > `apply` > `iterrows`.

---

## 7. Sorting & Ranking

```python
df.sort_values("age", ascending=False)
df.sort_values(["city", "age"], ascending=[True, False])
df.sort_index()
df.nlargest(5, "spend")
df.nsmallest(5, "spend")
df["rank"] = df["score"].rank(method="dense", ascending=False)
```

`rank` methods: `average` (default), `min`, `max`, `first`, `dense`. `dense` matches SQL `DENSE_RANK`, `min` matches `RANK`.

---

## 8. GroupBy (Split-Apply-Combine)

```python
df.groupby("city")["sales"].sum()
df.groupby(["city", "year"])["sales"].mean()

df.groupby("city").agg(
    total=("sales", "sum"),
    avg=("sales", "mean"),
    n=("id", "count"),
    n_users=("user_id", "nunique"),
)                                            # named aggregation: clearest form

df.groupby("city")["sales"].agg(["sum", "mean", "max"])
df.groupby("city").agg({"sales": ["sum", "mean"], "qty": "max"})

df.groupby("city", as_index=False)["sales"].sum()    # keep group keys as a column
df.groupby("city").size()                             # rows per group (includes NaN rows)
```

### transform: result aligned to the original rows

```python
df["city_avg"] = df.groupby("city")["sales"].transform("mean")
df["pct_of_city"] = df["sales"] / df.groupby("city")["sales"].transform("sum")
df["z"] = df.groupby("city")["sales"].transform(lambda s: (s - s.mean()) / s.std())
```

### filter: keep or drop whole groups

```python
df.groupby("city").filter(lambda g: len(g) >= 10)    # like HAVING COUNT(*) >= 10
```

### Key distinction

| Method | Output shape | SQL analogy |
|---|---|---|
| `agg` | one row per group | `GROUP BY` |
| `transform` | same rows as input | window function `OVER (PARTITION BY ...)` |
| `filter` | subset of original rows | `HAVING` |

`count` ignores NaN; `size` counts all rows.

---

## 9. Combining DataFrames

### merge (SQL JOIN)

```python
pd.merge(left, right, on="id", how="inner")           # inner | left | right | outer | cross
pd.merge(left, right, left_on="uid", right_on="id", how="left")
pd.merge(left, right, on="id", how="left", suffixes=("_l", "_r"), indicator=True)
pd.merge(left, right, on="id", validate="one_to_one")   # raises if keys aren't unique as promised
```

- `indicator=True` adds a `_merge` column (`left_only`, `right_only`, `both`). Great for anti-joins.
- `validate=` catches accidental **row explosion** from duplicate keys. Use it.

```python
# anti-join: rows in left with no match in right
m = left.merge(right, on="id", how="left", indicator=True)
only_left = m[m["_merge"] == "left_only"]
```

### concat

```python
pd.concat([df1, df2], ignore_index=True)       # stack rows (UNION ALL)
pd.concat([df1, df2], axis=1)                  # side by side, aligned on index
```

### join (index-based shortcut)

```python
left.join(right, how="left")                   # joins on index
```

---

## 10. Reshaping

```python
# long to wide
df.pivot(index="date", columns="product", values="sales")            # no duplicates allowed
df.pivot_table(index="region", columns="year", values="sales",
               aggfunc="sum", fill_value=0, margins=True)            # handles duplicates

# wide to long
pd.melt(df, id_vars=["id"], value_vars=["q1", "q2", "q3"],
        var_name="quarter", value_name="revenue")

df.stack(); df.unstack()                      # move between column/index levels
pd.crosstab(df["gender"], df["churned"], normalize="index")   # frequency table

# explode list-like cells into rows
df.explode("tags")
```

Rule of thumb: **tidy (long) data for groupby and plotting, wide data for display and spreadsheets.**

---

## 11. Time Series

```python
df["date"] = pd.to_datetime(df["date"])
df = df.set_index("date").sort_index()

df["date"].dt.year; .dt.month; .dt.day; .dt.dayofweek; .dt.hour
df["date"].dt.to_period("M")
df["date"].dt.strftime("%Y-%m")

df.loc["2025-01"]                      # partial string indexing
df.loc["2025-01-01":"2025-03-31"]

df.resample("M")["sales"].sum()        # downsample: group by time bucket
df.resample("W").agg({"sales": "sum", "id": "count"})
df.asfreq("D")                          # reindex to a regular frequency

pd.date_range("2025-01-01", periods=30, freq="D")
df["delta"] = df["end"] - df["start"]
df["delta"].dt.days
```

Common frequency codes: `D` day, `W` week, `MS`/`ME` month start/end (recent versions renamed month-end to `ME`), `QS` quarter, `h` hour. If a code warns about deprecation, check the pandas version docs.

### Window functions

```python
df["ma7"] = df["sales"].rolling(7).mean()                 # moving average
df["ma7"] = df["sales"].rolling("7D").mean()              # time-based window (needs datetime index)
df["cum"] = df["sales"].cumsum()                          # running total
df["expanding_max"] = df["sales"].expanding().max()
df["prev"] = df["sales"].shift(1)                         # LAG
df["next"] = df["sales"].shift(-1)                        # LEAD
df["pct_chg"] = df["sales"].pct_change()
df["diff"] = df["sales"].diff()
df["ewm"] = df["sales"].ewm(span=7).mean()                # exponential moving average

# per group
df["cum_by_user"] = df.groupby("user")["amount"].cumsum()
df["prev_by_user"] = df.groupby("user")["amount"].shift(1)
```

---

## 12. Gotchas That Bite Everyone

**Chained indexing / SettingWithCopyWarning**
```python
df[df["a"] > 0]["b"] = 1          # may modify a temporary copy, may silently do nothing
df.loc[df["a"] > 0, "b"] = 1      # correct: one .loc call
```
Copy-on-Write semantics are changing this behavior. It is the default in newer major versions and opt-in in older ones (`pd.options.mode.copy_on_write = True`). Either way, **use a single `.loc`** and call `.copy()` when you intend an independent frame.

**`inplace=True` is not faster** and is being discouraged. Reassign instead: `df = df.dropna()`.

**Index alignment**
```python
s1 + s2        # aligns on index labels; non-matching labels become NaN
```
Reset or set indexes deliberately before arithmetic between frames.

**`==` with NaN:** `np.nan == np.nan` is `False`. Use `isna()`.

**`&` vs `and`:** use `&`, `|`, `~` with parentheses. `and`/`or` raise an error on Series.

**Merge row explosion:** duplicate keys on both sides multiply rows. Check `df["key"].is_unique` or use `validate=`.

**Silent dtype changes:** ints become floats when NaN appears; dates stay as strings if parsing fails. Check `df.dtypes` after loading.

**`apply(axis=1)` is slow.** Rewrite as vectorized logic or `np.where`.

**Float equality:** use `np.isclose`, not `==`.

---

## 13. SQL to Pandas Map

| SQL | Pandas |
|---|---|
| `SELECT a, b` | `df[["a", "b"]]` |
| `WHERE x > 5` | `df[df["x"] > 5]` or `df.query("x > 5")` |
| `ORDER BY x DESC` | `df.sort_values("x", ascending=False)` |
| `LIMIT 10` | `df.head(10)` |
| `DISTINCT a` | `df["a"].drop_duplicates()` |
| `COUNT(*)` | `len(df)` or `df.shape[0]` |
| `GROUP BY g` + `SUM(x)` | `df.groupby("g")["x"].sum()` |
| `HAVING COUNT(*) > 5` | `.groupby("g").filter(lambda x: len(x) > 5)` |
| `INNER JOIN` | `pd.merge(a, b, on="k", how="inner")` |
| `LEFT JOIN` | `pd.merge(a, b, on="k", how="left")` |
| `UNION ALL` | `pd.concat([a, b])` |
| `CASE WHEN` | `np.where` / `np.select` |
| `ROW_NUMBER() OVER (PARTITION BY g ORDER BY x)` | `df.sort_values("x").groupby("g").cumcount() + 1` |
| `RANK()` / `DENSE_RANK()` | `groupby("g")["x"].rank(method="min" / "dense")` |
| `SUM(x) OVER (PARTITION BY g)` | `groupby("g")["x"].transform("sum")` |
| `LAG(x)` / `LEAD(x)` | `groupby("g")["x"].shift(1)` / `shift(-1)` |
| `SUM(x) OVER (ORDER BY d)` (running) | `df["x"].cumsum()` |
| `IN (...)` | `df["x"].isin([...])` |
| `IS NULL` | `df["x"].isna()` |
| `COALESCE(a, b)` | `df["a"].fillna(df["b"])` |
| `LIKE '%x%'` | `df["a"].str.contains("x")` |

---

## 14. Common Interview / Analysis Patterns

### Top N per group

```python
top3 = (df.sort_values("sales", ascending=False)
          .groupby("region")
          .head(3))

# or with explicit rank
df["rk"] = df.groupby("region")["sales"].rank(method="dense", ascending=False)
top3 = df[df["rk"] <= 3]
```

### Nth highest value (e.g. second-highest salary)

```python
df["salary"].drop_duplicates().nlargest(2).iloc[-1]
```

### Find duplicates / keep latest per key

```python
df[df.duplicated("email", keep=False)]
latest = df.sort_values("updated_at").drop_duplicates("user_id", keep="last")
```

### Running total and percent of total

```python
df["running"] = df.sort_values("date")["amount"].cumsum()
df["pct"] = df["amount"] / df["amount"].sum() * 100
```

### Month-over-month growth

```python
m = df.groupby(df["date"].dt.to_period("M"))["revenue"].sum()
growth = m.pct_change() * 100
```

### Customers with no orders (anti-join)

```python
res = customers.merge(orders, on="customer_id", how="left", indicator=True)
no_orders = res[res["_merge"] == "left_only"]
```

### Pivot rows into columns (conditional counts)

```python
pd.crosstab(df["dept"], df["gender"])
df.pivot_table(index="dept", columns="gender", values="id", aggfunc="count", fill_value=0)
```

### Consecutive runs / gaps in dates

```python
d = df.sort_values("date").copy()
d["grp"] = (d["date"].diff().dt.days != 1).cumsum()      # new group whenever gap != 1 day
runs = d.groupby("grp").agg(start=("date", "min"), end=("date", "max"), days=("date", "count"))
```

### Cohort retention (sketch)

```python
df["cohort"] = df.groupby("user_id")["date"].transform("min").dt.to_period("M")
df["period"] = df["date"].dt.to_period("M")
df["age"] = (df["period"] - df["cohort"]).apply(lambda x: x.n)
ret = df.pivot_table(index="cohort", columns="age", values="user_id", aggfunc="nunique")
ret = ret.div(ret[0], axis=0)          # divide each row by its cohort size
```

### A/B test summary

```python
summary = df.groupby("variant").agg(n=("user", "nunique"),
                                    conv=("converted", "mean"))
```

---

## 15. Performance

- Load only needed columns (`usecols`) and set dtypes at read time.
- Use `category` for low-cardinality strings, `int32`/`float32` where range allows.
- Avoid `iterrows`/`apply(axis=1)`; vectorize or use `np.where`.
- Prefer Parquet over CSV.
- Process huge files with `chunksize`, aggregating per chunk.
- `df.memory_usage(deep=True)` shows what's heavy.
- Use `query` / `eval` for large boolean expressions.
- Past a few GB, move to Polars, DuckDB, or Spark/PySpark.

```python
df["city"] = df["city"].astype("category")
df.info(memory_usage="deep")

# chunked aggregation
parts = []
for chunk in pd.read_csv("big.csv", chunksize=500_000):
    parts.append(chunk.groupby("key")["x"].sum())
total = pd.concat(parts).groupby(level=0).sum()
```

Querying a DataFrame with SQL directly (DuckDB):
```python
import duckdb
duckdb.sql("SELECT city, SUM(sales) FROM df GROUP BY city").df()
```

---

## 16. Quick Plotting

```python
df["x"].plot(kind="hist", bins=30)
df.plot(x="date", y="sales", kind="line")
df.groupby("city")["sales"].sum().sort_values().plot(kind="barh")
df.plot.scatter(x="age", y="spend")
df.boxplot(column="spend", by="city")
df.corr(numeric_only=True)                    # correlation matrix
```
For anything presentation-grade, use matplotlib directly, seaborn, or plotly.

---

## 17. Statistics Quick Hits

```python
df["x"].mean(); .median(); .std(); .var(); .skew(); .kurt()
df["x"].quantile([0.25, 0.5, 0.75, 0.95])
df[["a", "b"]].corr(method="spearman")
df["x"].cov(df["y"])
df.groupby("g")["x"].agg(["mean", "std", "count"])
df["x"].value_counts(normalize=True)          # proportions
df.sample(frac=0.1, random_state=42)          # reproducible sample
df.cumsum(); df.cumprod()
```

Report the **median** alongside the mean for skewed data, and always check group sizes (`count`) before comparing group means.

---

## 18. Checklist for a New Dataset

1. `shape`, `info()`, `head()`, `describe()`
2. `isna().sum()`: what's missing and why?
3. `duplicated().sum()`: are rows or keys duplicated?
4. Check dtypes: parse dates, numerics, categories
5. Check ranges and impossible values (negative ages, future dates)
6. Check key uniqueness before any merge
7. Check group sizes before comparing group stats
8. Keep raw data untouched; build cleaned data in a pipeline you can re-run
