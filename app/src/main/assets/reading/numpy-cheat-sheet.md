# The NumPy Cheat Sheet: Arrays to Vectorized Thinking

Reference doc. Mental model first, then the operations, then the gotchas and patterns that come up in analysis work and interviews.

```python
import numpy as np
```

---

## 1. Mental Model

- **`ndarray`:** a fixed-size block of memory holding elements of **one dtype**, plus metadata: `shape`, `dtype`, `strides`.
- **Vectorization:** operations run in compiled C over the whole array. A Python loop over elements gives up most of NumPy's speed.
- **Broadcasting:** arrays of different shapes combine by stretching size-1 dimensions, without copying data.
- **Views vs. copies:** many operations return a **view** (same memory, different metadata). Modifying a view modifies the original.
- **NumPy is the layer under pandas, scikit-learn, SciPy, and most ML libraries.** A pandas column is backed by an array.

```python
a = np.arange(1_000_000)
a * 2                       # vectorized: fast
[x * 2 for x in a]          # Python loop over elements: slow
```

---

## 2. Creating Arrays

```python
np.array([1, 2, 3])
np.array([[1, 2], [3, 4]])
np.array([1, 2, 3], dtype=np.float32)

np.zeros((3, 4))
np.ones((2, 3))
np.full((2, 2), 7)
np.empty((2, 2))             # uninitialized memory, contents are garbage
np.eye(3)                    # identity matrix
np.diag([1, 2, 3])

np.arange(0, 10, 2)          # [0 2 4 6 8], stop is exclusive
np.linspace(0, 1, 5)         # 5 points, endpoints INCLUDED
np.logspace(0, 3, 4)         # 1, 10, 100, 1000

np.zeros_like(a); np.ones_like(a); np.full_like(a, 9)

np.meshgrid(x, y)            # coordinate grids from 1-D vectors
```

`arange` with float steps can produce surprising lengths due to rounding. Use `linspace` when you care about the number of points.

---

## 3. Inspecting

```python
a.shape          # (rows, cols, ...)
a.ndim           # number of dimensions
a.size           # total elements
a.dtype
a.itemsize       # bytes per element
a.nbytes         # total bytes
a.strides        # bytes to step along each axis
a.T              # transpose
a.flags          # C_CONTIGUOUS, OWNDATA, ...
```

`a.base is not None` means `a` is a view of another array.

---

## 4. Data Types

```python
np.int8, np.int16, np.int32, np.int64
np.uint8, np.uint16, np.uint32, np.uint64
np.float16, np.float32, np.float64
np.bool_
np.complex128
np.str_, np.datetime64, np.timedelta64

a.astype(np.float32)               # returns a new array
np.can_cast(np.int64, np.int32)
np.iinfo(np.int32).max             # integer limits
np.finfo(np.float64).eps           # float precision
```

**Integers overflow silently.**
```python
np.array([200], dtype=np.uint8) + 100     # wraps around to 44, no error
```

**Type promotion:** mixing ints and floats gives floats. Mixing `int64` and `uint64` gives `float64`. Mixing a Python scalar with an array usually keeps the array's dtype.

`float32` halves memory and is often enough for large arrays; use `float64` when accumulating many values or when precision matters.

---

## 5. Indexing & Slicing

### Basic (returns a VIEW)

```python
a[0]                  # first element / first row
a[-1]
a[1:4]                # start inclusive, stop exclusive
a[::2]                # every second element
a[::-1]               # reversed

m[1, 2]               # row 1, col 2
m[1]                  # row 1
m[:, 0]               # column 0
m[0:2, 1:3]           # sub-block
m[..., 0]             # Ellipsis: all leading dims, last index 0
```

### Boolean indexing (returns a COPY)

```python
a[a > 5]
a[(a > 2) & (a < 8)]          # & | ~ with parentheses, NOT and/or/not
a[a % 2 == 0] = 0             # assign through a mask
np.where(a > 5, a, 0)         # elementwise if/else
```

### Fancy indexing (returns a COPY)

```python
a[[0, 2, 4]]                  # pick by index list
m[[0, 2], [1, 3]]             # pairs: (0,1) and (2,3), NOT a 2x2 block
m[np.ix_([0, 2], [1, 3])]     # the 2x2 block of those rows and columns
a[np.argsort(a)]
```

### newaxis

```python
a[:, np.newaxis]              # shape (n,) -> (n, 1)
a[None, :]                    # shape (n,) -> (1, n)
```

**Slice = view, fancy/boolean = copy.** This is the single most important indexing rule.

```python
b = a[1:4]
b[0] = 99          # also changes a
c = a[[1, 2, 3]]
c[0] = 99          # does NOT change a
```

---

## 6. Broadcasting

Rules, compared from the **last dimension backward**: two dimensions are compatible if they are **equal** or one of them is **1**. Missing leading dimensions are treated as 1.

```python
a = np.ones((3, 4))
b = np.array([1, 2, 3, 4])          # shape (4,)
a + b                                # (3,4) + (4,) -> (3,4), b added to every row

col = np.array([[10], [20], [30]])  # shape (3,1)
a + col                              # (3,4) + (3,1) -> (3,4), col added to every column

x = np.arange(3)[:, None]           # (3,1)
y = np.arange(4)[None, :]           # (1,4)
x + y                                # (3,4) outer sum
```

Incompatible shapes raise `ValueError: operands could not be broadcast together`. Print `.shape` of both operands first.

**Mean-center and standardize columns:**
```python
X = (X - X.mean(axis=0)) / X.std(axis=0)
```

**Pairwise distances without loops:**
```python
# X shape (n, d)
diff = X[:, None, :] - X[None, :, :]       # (n, n, d)
dist = np.sqrt((diff ** 2).sum(axis=-1))   # (n, n)
```
This allocates `n*n*d` values. For large `n`, use the dot-product identity or `scipy.spatial.distance.cdist`.

---

## 7. Universal Functions (ufuncs) & Math

```python
np.add, np.subtract, np.multiply, np.divide, np.power, np.mod
np.sqrt, np.exp, np.log, np.log2, np.log10
np.sin, np.cos, np.tan, np.arctan2
np.abs, np.sign, np.floor, np.ceil, np.round
np.maximum(a, b)            # elementwise max of two arrays
np.minimum(a, b)
np.clip(a, 0, 1)
np.isnan(a); np.isinf(a); np.isfinite(a)
np.isclose(a, b)            # float comparison with tolerance
np.allclose(a, b)           # all elements close
np.logical_and(a, b); np.logical_or(a, b); np.logical_not(a)
```

Ufunc methods:
```python
np.add.reduce(a)              # same as a.sum()
np.add.accumulate(a)          # same as a.cumsum()
np.multiply.outer(a, b)       # outer product
np.add.at(arr, idx, vals)     # unbuffered: repeated indices accumulate correctly
```

`arr[idx] += vals` with repeated indices only applies the last update per index. Use `np.add.at` or `np.bincount` when indices repeat.

---

## 8. Aggregation & the `axis` Argument

```python
a.sum(); a.mean(); a.std(); a.var()
a.min(); a.max()
a.argmin(); a.argmax()        # index of min / max
a.prod()
a.cumsum(); a.cumprod()
a.any(); a.all()
np.median(a)
np.percentile(a, [25, 50, 75])
np.quantile(a, [0.25, 0.75])
np.ptp(a)                      # max - min
np.count_nonzero(a > 5)
```

**`axis` means "the axis that gets collapsed."**

```python
m = np.array([[1, 2, 3],
              [4, 5, 6]])       # shape (2, 3)

m.sum(axis=0)    # collapse rows    -> [5 7 9]       (per column), shape (3,)
m.sum(axis=1)    # collapse columns -> [ 6 15]       (per row),    shape (2,)
m.sum()          # everything -> 21
```

```python
m.sum(axis=1, keepdims=True)    # shape (2,1): keeps the dimension so it broadcasts back
m / m.sum(axis=1, keepdims=True)  # normalize each row to sum to 1
```

`keepdims=True` is the clean way to avoid reshape bugs when broadcasting a reduction result back against the original.

**Sample vs. population std:**
```python
a.std()            # population (ddof=0), NumPy's default
a.std(ddof=1)      # sample std, what pandas uses by default
```

**NaN-aware versions:** `np.nansum`, `np.nanmean`, `np.nanstd`, `np.nanmax`, `np.nanmin`, `np.nanpercentile`. Regular `sum`/`mean` return `nan` if any element is `nan`.

---

## 9. Reshaping

```python
a.reshape(2, 3)
a.reshape(-1, 3)             # -1: infer this dimension
a.ravel()                    # flatten, view if possible
a.flatten()                  # flatten, always a copy
a.T
np.transpose(a, (2, 0, 1))   # permute axes
np.swapaxes(a, 0, 1)
np.moveaxis(a, 0, -1)
np.squeeze(a)                # drop size-1 dimensions
np.expand_dims(a, axis=0)
```

`reshape` returns a view when memory layout allows, otherwise a copy. Don't rely on either behavior; call `.copy()` if you need independence.

### Stacking, splitting, repeating

```python
np.concatenate([a, b], axis=0)
np.vstack([a, b])             # stack rows
np.hstack([a, b])             # stack columns
np.stack([a, b], axis=0)      # new axis, inputs must have identical shape
np.column_stack([a, b])

np.split(a, 3)                # 3 equal parts
np.array_split(a, 3)          # allows unequal parts
np.hsplit(m, 2); np.vsplit(m, 2)

np.repeat(a, 3)               # each element 3 times: [1,1,1,2,2,2]
np.tile(a, 3)                 # the whole array 3 times: [1,2,1,2,1,2]
np.pad(a, (1, 2), constant_values=0)
np.roll(a, 2)                 # circular shift
np.flip(a, axis=0)
```

---

## 10. Sorting, Searching, Sets

```python
np.sort(a)                       # sorted copy
a.sort()                         # in place
np.sort(a, axis=0)
np.argsort(a)                    # indices that would sort a
np.argsort(a)[::-1]              # descending order of indices
np.lexsort((secondary, primary)) # sort by primary, then secondary (last key is primary)

np.where(a > 5)                  # indices where condition is True
np.nonzero(a)
np.argwhere(a > 5)               # (k, ndim) array of coordinates
np.searchsorted(sorted_a, x)     # binary search insert position
np.digitize(a, bins)             # which bin each value falls in

np.unique(a)
np.unique(a, return_counts=True)
np.unique(a, return_inverse=True)
np.unique(m, axis=0)             # unique rows
np.isin(a, [1, 2, 3])            # membership test
np.intersect1d(a, b); np.union1d(a, b); np.setdiff1d(a, b)
```

### Top-k without a full sort

```python
k = 5
idx = np.argpartition(a, -k)[-k:]     # k largest, unordered, O(n)
idx = idx[np.argsort(a[idx])[::-1]]   # order those k descending
```

### Counting

```python
np.bincount(labels)                  # counts per non-negative integer label
np.bincount(labels, weights=w)       # weighted sum per label (a fast groupby-sum)
np.histogram(a, bins=10)
```

---

## 11. Linear Algebra

```python
a @ b                         # matrix multiply (preferred operator)
np.dot(a, b)
a * b                         # ELEMENTWISE, not matrix multiply
np.outer(a, b)
np.inner(a, b)
np.cross(a, b)
np.trace(m)

np.linalg.inv(m)              # inverse
np.linalg.det(m)
np.linalg.solve(A, b)         # solve Ax = b: prefer this over inv(A) @ b
np.linalg.lstsq(A, b, rcond=None)   # least-squares solution
np.linalg.norm(v)             # L2 norm
np.linalg.norm(v, ord=1)      # L1
np.linalg.matrix_rank(m)
np.linalg.eig(m)              # eigenvalues, eigenvectors (general)
np.linalg.eigh(m)             # for symmetric/Hermitian matrices, faster and real
np.linalg.svd(m)
np.linalg.qr(m)
np.linalg.cholesky(m)
```

- `solve` is faster and more numerically stable than computing an explicit inverse.
- Use `eigh` for covariance matrices (they are symmetric).
- `np.cov(X, rowvar=False)` gives the feature covariance matrix when rows are samples.

### einsum

```python
np.einsum("ij,jk->ik", A, B)        # matrix multiply
np.einsum("ii", m)                   # trace
np.einsum("ij->ji", m)               # transpose
np.einsum("ij,ij->i", A, B)          # row-wise dot products
np.einsum("bij,bjk->bik", A, B)      # batched matmul
```

---

## 12. Random Numbers

Use the modern `Generator` API, not the legacy `np.random.seed` global state.

```python
rng = np.random.default_rng(42)       # reproducible

rng.random(5)                          # uniform [0, 1)
rng.integers(0, 10, size=5)            # integers in [0, 10)
rng.normal(0, 1, size=(3, 3))          # mean, std
rng.choice(a, size=3, replace=False)   # sample without replacement
rng.choice(a, size=3, p=[0.5, 0.3, 0.2])   # weighted
rng.permutation(10)                    # shuffled 0..9
rng.shuffle(a)                         # in place
rng.binomial(10, 0.5, size=5)
rng.poisson(3, size=5)
rng.exponential(2.0, size=5)
```

Pass `rng` into functions instead of seeding globally, so experiments stay reproducible and independent.

---

## 13. Statistics Quick Hits

```python
np.mean(a); np.median(a); np.std(a, ddof=1)
np.percentile(a, 95)
np.corrcoef(x, y)             # correlation matrix
np.cov(x, y)
np.histogram(a, bins=20, density=True)
np.average(a, weights=w)      # weighted mean
np.cumsum(a) / np.arange(1, len(a) + 1)    # running mean
```

**Moving average (convolution):**
```python
w = 5
np.convolve(a, np.ones(w) / w, mode="valid")
```

**Moving window view (no copy):**
```python
from numpy.lib.stride_tricks import sliding_window_view
sliding_window_view(a, 5).mean(axis=1)
```

---

## 14. Missing & Special Values

```python
np.nan                       # float only; arrays containing it must be float
np.isnan(a)
a[~np.isnan(a)]              # drop NaNs
np.nan_to_num(a, nan=0.0)    # replace NaN/inf
np.nanmean(a)
np.nan == np.nan             # False, always. Use np.isnan.

masked = np.ma.masked_invalid(a)    # masked arrays ignore invalid entries in computations
masked.mean()
```

Integer arrays cannot hold NaN. Use floats, masked arrays, or pandas nullable types.

---

## 15. Copies, Views & Memory

```python
b = a.copy()                # explicit independent copy
np.shares_memory(a, b)      # do they overlap?
a.base                      # the array this one is a view of, or None
```

| Operation | Result |
|---|---|
| Basic slice `a[1:4]` | view |
| `a.T`, `a.ravel()` (if contiguous), `a.reshape()` (usually) | view |
| Boolean mask `a[mask]` | copy |
| Fancy index `a[[1,2]]` | copy |
| `a.flatten()`, `a.astype()`, `np.concatenate` | copy |

**Aliasing trap:** `b = a` is not a copy at all. Both names point to the same array.

**In-place ops** save memory: `a += 1`, `np.add(a, b, out=a)`. Beware dtype: `int_array += 0.5` raises an error, since the result can't be cast back to int safely.

**Avoid growing arrays in a loop.** `np.append` and `np.concatenate` copy every time (O(n²) total). Collect into a Python list and convert once, or preallocate with `np.empty`.

```python
# slow
out = np.array([])
for x in data:
    out = np.append(out, f(x))

# better
out = np.empty(len(data))
for i, x in enumerate(data):
    out[i] = f(x)

# best, if f is vectorizable
out = f(data)
```

---

## 16. File I/O

```python
np.save("arr.npy", a)              # single array, binary, fast, keeps dtype and shape
a = np.load("arr.npy")
np.savez("arrs.npz", x=a, y=b)     # multiple arrays
np.savez_compressed("arrs.npz", x=a, y=b)
d = np.load("arrs.npz"); d["x"]

np.loadtxt("data.csv", delimiter=",", skiprows=1)
np.genfromtxt("data.csv", delimiter=",", names=True)   # handles missing values
np.savetxt("out.csv", a, delimiter=",")
```

For tabular data with headers and mixed types, use pandas. For large numeric arrays, `.npy` is far faster than text.

---

## 17. Gotchas That Bite Everyone

**`*` is elementwise.** Matrix multiply is `@`.

**`and`/`or` don't work on arrays.** Use `&`, `|`, `~` with parentheses:
```python
a[(a > 2) & (a < 8)]
```

**Views mutate the original.** Slice, then modify, and the source changes. `.copy()` when in doubt.

**`np.array([1, 2, 3.0])` becomes float64.** Mixed input gets promoted to a common dtype.

**Ragged lists don't make 2-D arrays.** `np.array([[1, 2], [3]])` raises an error in current versions (or yields an object array in older ones). Pad or use a list of arrays.

**Shape `(n,)` vs `(n, 1)` vs `(1, n)` are three different things.** A 1-D array is neither a row nor a column vector. Mixing them in broadcasting silently produces an `(n, n)` result:
```python
a = np.arange(3)          # (3,)
b = np.arange(3)[:, None] # (3,1)
(a + b).shape             # (3, 3), probably not what you wanted
```

**Float comparison:** use `np.isclose`/`np.allclose`, never `==`.

**Integer division:** `//` floors, `/` always returns float. Negative numbers floor toward negative infinity.

**`np.sum` of Python `bool`s counts `True` values.** Handy for counting matches: `(a > 5).sum()`.

**Default `std` is population (ddof=0).** Pandas defaults to sample (ddof=1). Results differ between the two for the same data.

**`argmax` returns the first max index** and a flat index for multi-dimensional input. Use `np.unravel_index(a.argmax(), a.shape)` to get coordinates.

**`np.matrix` is deprecated.** Use 2-D `ndarray` with `@`.

---

## 18. Performance

- **Vectorize first.** If you wrote a loop over elements, look for a ufunc, `where`, `cumsum`, or broadcasting equivalent.
- **Mind memory layout.** C-contiguous arrays iterate fast along the last axis. `a.T` is a view with swapped strides, and operations on it can be slower until you `np.ascontiguousarray`.
- **Pick the smallest dtype that's safe** (`float32`, `int32`, `uint8`) for big arrays.
- **Avoid temporaries** in long expressions with `out=` and in-place ops, or `numexpr` for large arrays.
- **Reduce memory with views** like `sliding_window_view` and `broadcast_to` instead of materializing copies.
- **Profile before optimizing:** `%timeit` in IPython/Jupyter.
- **When vectorization isn't expressible**, use Numba (`@njit`) or move to Cython/C.
- **BLAS-backed ops** (`@`, `dot`, `linalg`) are multithreaded and very fast. Prefer them to hand-written loops.

```python
%timeit a * 2
%timeit [x * 2 for x in a]
```

---

## 19. Common Interview / Analysis Patterns

### Normalize / standardize

```python
minmax = (a - a.min()) / (a.max() - a.min())
zscore = (a - a.mean()) / a.std()
rows_sum_to_1 = m / m.sum(axis=1, keepdims=True)
```

### One-hot encoding

```python
labels = np.array([0, 2, 1, 2])
onehot = np.eye(labels.max() + 1)[labels]
```

### Softmax (numerically stable)

```python
def softmax(x, axis=-1):
    z = x - x.max(axis=axis, keepdims=True)    # subtract max to avoid overflow
    e = np.exp(z)
    return e / e.sum(axis=axis, keepdims=True)
```

### Cosine similarity

```python
def cosine(a, b):
    return (a @ b) / (np.linalg.norm(a) * np.linalg.norm(b))

# all pairs, rows of X
Xn = X / np.linalg.norm(X, axis=1, keepdims=True)
sim = Xn @ Xn.T
```

### Group sum without pandas

```python
keys = np.array([0, 1, 0, 2, 1])
vals = np.array([10., 20., 30., 40., 50.])
np.bincount(keys, weights=vals)       # [40. 70. 40.]
```

### Top-k per row

```python
idx = np.argsort(m, axis=1)[:, -3:][:, ::-1]      # indices of 3 largest per row
vals = np.take_along_axis(m, idx, axis=1)
```

### Rolling window features

```python
from numpy.lib.stride_tricks import sliding_window_view
win = sliding_window_view(a, 7)
rolling_mean, rolling_std = win.mean(axis=1), win.std(axis=1)
```

### Find missing number / duplicates

```python
n = 10
missing = n * (n + 1) // 2 - arr.sum()          # arr holds 1..n except one

vals, counts = np.unique(arr, return_counts=True)
dups = vals[counts > 1]
```

### Matrix rotation, diagonal, spiral building blocks

```python
np.rot90(m)
np.diag(m)
np.triu(m); np.tril(m)
np.fliplr(m); np.flipud(m)
```

### Image-like arrays

```python
img.shape                       # (H, W, C)
gray = img @ np.array([0.299, 0.587, 0.114])
img.transpose(2, 0, 1)           # HWC -> CHW
img.astype(np.float32) / 255.0   # scale to [0,1]
```

### Simulate and estimate (Monte Carlo)

```python
rng = np.random.default_rng(0)
pts = rng.random((1_000_000, 2))
pi_est = 4 * ((pts ** 2).sum(axis=1) <= 1).mean()
```

### Linear regression with the normal equation / lstsq

```python
X1 = np.column_stack([np.ones(len(X)), X])        # add intercept column
beta, *_ = np.linalg.lstsq(X1, y, rcond=None)
pred = X1 @ beta
```

---

## 20. Practice Interview Questions (10 Easy, 10 Medium, 10 Hard)

Every solution below was run and checked. Try each one cold before reading the answer.

### Easy

**E1. Reverse a 1-D array.**
```python
a = np.arange(10)
a[::-1]                     # a view, no copy made
```

**E2. Build a 5x5 matrix with 1, 2, 3, 4 on the diagonal just below the main diagonal.**
```python
np.diag(1 + np.arange(4), k=-1)
# [[0 0 0 0 0]
#  [1 0 0 0 0]
#  [0 2 0 0 0]
#  [0 0 3 0 0]
#  [0 0 0 4 0]]
```
`k` shifts the diagonal: positive above, negative below.

**E3. Replace every odd number with -1 without modifying the original.**
```python
np.where(a % 2 == 1, -1, a)         # returns a new array
```

**E4. Count how many elements are greater than the mean.**
```python
(a > a.mean()).sum()                 # True counts as 1
```

**E5. Reshape 12 elements into 3x4, then into 2 rows with the column count inferred.**
```python
a = np.arange(12)
a.reshape(3, 4)
a.reshape(2, -1)                     # -1 infers 6
```

**E6. Find the column index of the maximum in each row.**
```python
m.argmax(axis=1)
```
`axis=1` collapses the columns, leaving one answer per row.

**E7. Min-max scale an array to the range [0, 1].**
```python
(a - a.min()) / (a.max() - a.min())
```
Edge case to mention: all-equal input divides by zero.

**E8. Swap the first two columns of a 2-D array.**
```python
m[:, [0, 1]] = m[:, [1, 0]]
```
The right side is fancy indexing, so it is a copy. That is why the swap works without a temp variable.

**E9. Find the values common to two arrays.**
```python
np.intersect1d(a, b)                 # sorted, unique
```

**E10. Remove NaN values from an array.**
```python
a[~np.isnan(a)]
```

### Medium

**M1. Find the most frequent value in an array of non-negative integers.**
```python
np.bincount(a).argmax()
```
For negative or non-integer values: `vals, c = np.unique(a, return_counts=True); vals[c.argmax()]`. Ties return the smallest value.

**M2. Find the second-largest distinct value.**
```python
np.unique(a)[-2]                     # [5,5,3,9,9,1] -> 5
```
`np.partition(a, -2)[-2]` is O(n) but returns a duplicate of the max when the max repeats, so it answers a different question. Clarify "distinct" with the interviewer.

**M3. Subtract each row's mean from that row.**
```python
m - m.mean(axis=1, keepdims=True)
```
`keepdims=True` keeps shape `(n, 1)` so it broadcasts across columns. Without it you get `(n,)`, which broadcasts against the wrong axis.

**M4. Select only the rows where every value is positive.**
```python
m[(m > 0).all(axis=1)]
```

**M5. Compute all pairwise Euclidean distances between rows of `A` (n,d) and `B` (m,d), no loops.**
```python
np.sqrt(((A[:, None, :] - B[None, :, :]) ** 2).sum(axis=-1))    # shape (n, m)
```
Broadcasting `(n,1,d)` against `(1,m,d)` gives `(n,m,d)`. Memory is O(n·m·d), which is the follow-up trap. See H1 for the fix.

**M6. Compute a moving average of window `k` in O(n) using `cumsum`.**
```python
c = np.cumsum(np.insert(a, 0, 0))
(c[k:] - c[:-k]) / k
```
Each window sum is a difference of two prefix sums. Matches `np.convolve(a, np.ones(k)/k, mode="valid")`.

**M7. Return the rank (0 = smallest) of each element.**
```python
order = a.argsort()
ranks = np.empty_like(order)
ranks[order] = np.arange(len(a))     # [30, 10, 20] -> [2, 0, 1]
```
Equivalent shortcut: `a.argsort().argsort()`. Ties get arbitrary distinct ranks; use `scipy.stats.rankdata` for tie handling.

**M8. Run-length encode an array.**
```python
a = np.array([1, 1, 2, 2, 2, 3])
starts = np.r_[0, np.flatnonzero(np.diff(a) != 0) + 1]
lengths = np.diff(np.r_[starts, len(a)])
values = a[starts]                   # values [1 2 3], lengths [2 3 1]
```
`diff != 0` marks every position where the value changes.

**M9. Replace each NaN with its column's mean.**
```python
col_mean = np.nanmean(X, axis=0)
np.where(np.isnan(X), col_mean, X)   # (d,) broadcasts across rows
```

**M10. Sort the rows of a 2-D array by its second column, descending.**
```python
m[np.argsort(-m[:, 1])]
```
Negating works for numeric dtypes. For stable ordering on ties, use `np.argsort(m[:, 1], kind="stable")[::-1]` with care, since reversing flips tie order.

### Hard

**H1. Pairwise distances without the `(n, m, d)` blow-up.**
```python
d2 = (A**2).sum(1)[:, None] + (B**2).sum(1)[None, :] - 2 * A @ B.T
dist = np.sqrt(np.maximum(d2, 0))    # clip tiny negatives from float error
```
Uses `|a-b|² = |a|² + |b|² - 2a·b`. Memory is O(n·m), and the work becomes one BLAS matmul. Mention the precision tradeoff: it can lose accuracy for points very close together.

**H2. 2-D convolution (valid mode) without a Python loop over pixels.**
```python
from numpy.lib.stride_tricks import sliding_window_view

win = sliding_window_view(img, ker.shape)           # (H-kh+1, W-kw+1, kh, kw), no copy
out = np.einsum("ijkl,kl->ij", win, ker)            # cross-correlation
conv = np.einsum("ijkl,kl->ij", win, ker[::-1, ::-1])   # true convolution flips the kernel
```
Most "convolutions" in image libraries are really cross-correlation. Knowing the kernel flip is the detail interviewers listen for.

**H3. One step of Conway's Game of Life on a grid.**
```python
def life(g):
    n = sum(np.roll(np.roll(g, i, 0), j, 1)
            for i in (-1, 0, 1) for j in (-1, 0, 1) if (i, j) != (0, 0))
    return ((n == 3) | ((g == 1) & (n == 2))).astype(int)
```
`roll` counts the 8 neighbors by shifting the whole grid. It wraps at the edges (a torus). For fixed borders, pad with zeros and slice instead.

**H4. Rolling standard deviation in O(n) using prefix sums.**
```python
c1 = np.cumsum(np.insert(a, 0, 0))
c2 = np.cumsum(np.insert(a**2, 0, 0))
mean = (c1[w:] - c1[:-w]) / w
var = (c2[w:] - c2[:-w]) / w - mean**2
std = np.sqrt(np.maximum(var, 0))
```
Uses `Var = E[x²] - E[x]²`. Caveat: catastrophic cancellation when the mean is large relative to the spread. The safe-but-slower alternative is `sliding_window_view(a, w).std(axis=1)`.

**H5. Compute a correlation matrix without `np.corrcoef`.**
```python
Xc = X - X.mean(axis=0)
cov = Xc.T @ Xc / (len(X) - 1)
sd = np.sqrt(np.diag(cov))
corr = cov / np.outer(sd, sd)
```

**H6. Bootstrap a 95% confidence interval for the mean, fully vectorized.**
```python
rng = np.random.default_rng(0)
means = rng.choice(a, size=(2000, a.size), replace=True).mean(axis=1)
lo, hi = np.percentile(means, [2.5, 97.5])
```
One `(B, n)` resample matrix, one reduction. Memory is `B·n`; chunk the resamples if that is too large.

**H7. Row-wise mode of a 2-D array of small non-negative integers.**
```python
n, k = m.shape[0], m.max() + 1
counts = np.zeros((n, k), dtype=int)
np.add.at(counts, (np.arange(n)[:, None], m), 1)    # per-row histogram
counts.argmax(axis=1)
```
`counts[rows, m] += 1` would drop repeated indices. `np.add.at` accumulates them correctly. This is the "why does `+=` with duplicate indices fail" question in disguise.

**H8. Length of the longest run of `True` in a boolean array.**
```python
p = np.r_[False, b, False].astype(int)
d = np.diff(p)
starts = np.flatnonzero(d == 1)
ends = np.flatnonzero(d == -1)
(ends - starts).max(initial=0)
```
Padding with `False` on both sides guarantees every run has a start and an end. `initial=0` handles an all-`False` input.

**H9. Merge overlapping intervals, vectorized.**
```python
iv = iv[np.argsort(iv[:, 0])]                        # sort by start
st, en = iv[:, 0], iv[:, 1]
cm = np.maximum.accumulate(en)                       # furthest end seen so far
brk = st[1:] > cm[:-1]                               # gap before this interval
first = np.r_[0, np.flatnonzero(brk) + 1]            # first index of each merged group
merged = np.column_stack([st[first], np.maximum.reduceat(en, first)])
# [[8,10],[1,3],[2,6],[15,18],[17,20]] -> [[1,6],[8,10],[15,20]]
```
The key insight is the running maximum of ends: a new group starts only when the next start exceeds everything seen so far.

**H10. For each query value, find the nearest element in a sorted array.**
```python
i = np.searchsorted(arr, q)
left = np.clip(i - 1, 0, len(arr) - 1)
right = np.clip(i, 0, len(arr) - 1)
near = np.where(np.abs(arr[left] - q) <= np.abs(arr[right] - q), left, right)
arr[near]                                            # O(m log n) total
```
`searchsorted` gives the insertion point, so the nearest value is either its left or right neighbor. Clipping handles queries below the minimum or above the maximum.

---

## 21. NumPy and Pandas

```python
df.to_numpy()                   # DataFrame -> array
df["a"].to_numpy()
pd.DataFrame(arr, columns=["a", "b"])
pd.Series(arr)

np.where(df["a"] > 0, "pos", "neg")       # fast conditional column
np.select([c1, c2], [v1, v2], default=v3) # multi-branch CASE WHEN
np.log1p(df["x"])                          # ufuncs work directly on Series/DataFrames
```

Pandas adds labels and alignment on top of arrays. Drop to NumPy for heavy numeric work, then wrap the result back in pandas.

---

## 22. Quick Reference

```python
# create
np.array, zeros, ones, full, eye, arange, linspace, random.default_rng
# shape
reshape, ravel, T, transpose, squeeze, expand_dims, newaxis, stack, concatenate
# index
a[i], a[i:j], a[mask], a[[i, j]], np.where, np.argwhere, take_along_axis
# math
+ - * / ** @ , np.sqrt/exp/log, clip, abs, maximum/minimum
# reduce (axis= collapses that axis)
sum, mean, std, min, max, argmin, argmax, cumsum, any, all, percentile
# sort/search
sort, argsort, argpartition, unique, isin, searchsorted, bincount, digitize
# linalg
@, solve, inv, eigh, svd, norm, lstsq, einsum
# safety
isclose, nan* functions, copy(), dtype checks
```

**Debugging checklist when results look wrong:**
1. Print `shape` and `dtype` of every operand.
2. Check for accidental broadcasting: `(n,)` vs `(n,1)`.
3. Check for views mutating the original.
4. Check integer overflow and silent dtype promotion.
5. Check `axis` against what you meant to collapse.
6. Check NaNs with `np.isnan(a).any()`.
