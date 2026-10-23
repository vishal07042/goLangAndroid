# The Matplotlib Cheat Sheet: Figures to Finished Charts

Reference doc. Use the **object-oriented interface**, learn the anatomy once, and most of matplotlib stops being mysterious. Snippets were run against matplotlib 3.10.

```python
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.dates as mdates
```

---

## 1. Mental Model

```
Figure                      the whole canvas (the window / the PNG)
 └── Axes                   one plot area: data, ticks, labels, title   <- you work here
      ├── Axis (x, y)       the tick/scale machinery of one dimension
      └── Artists           everything drawn: lines, text, patches, images
```

- **`Figure`**: the container. Size, DPI, saving, overall title.
- **`Axes`**: a single plot. It is not "axis" (singular); that is a separate thing. Almost every method you call lives on an `Axes`.
- Everything visible is an **Artist**.

### Two interfaces (pick one)

```python
# pyplot / state-machine: implicit "current" figure and axes. Fine for a quick look.
plt.plot(x, y)
plt.title("Quick")
plt.show()

# object-oriented: explicit objects. Use this for anything real.
fig, ax = plt.subplots()
ax.plot(x, y)
ax.set_title("Explicit")
```

The state-machine style breaks down the moment you have two axes, loops, or functions that draw. The OO style always tells you exactly what you're modifying. **Default to `fig, ax = plt.subplots()`.**

Method names differ slightly: `plt.title(...)` becomes `ax.set_title(...)`, `plt.xlabel` becomes `ax.set_xlabel`. Or set several at once: `ax.set(title="...", xlabel="...", ylabel="...", xlim=(0, 10))`.

---

## 2. The Basic Template

```python
x = np.linspace(0, 10, 100)

fig, ax = plt.subplots(figsize=(8, 4), dpi=100, layout="constrained")
ax.plot(x, np.sin(x), label="sin", color="C0", linewidth=2, linestyle="--")
ax.plot(x, np.cos(x), label="cos")
ax.set(xlabel="x", ylabel="y", title="Waves", xlim=(0, 10))
ax.legend(loc="upper right", frameon=False)
ax.grid(alpha=0.3)
ax.spines[["top", "right"]].set_visible(False)      # cleaner look
fig.savefig("out.png", dpi=200, bbox_inches="tight")
plt.show()
```

- `layout="constrained"` auto-adjusts spacing so labels don't overlap (preferred over `plt.tight_layout()`).
- `figsize` is in inches; `dpi` sets resolution. Pixels = inches x dpi.
- `savefig` **before** `plt.show()`: in some backends `show()` clears the figure.
- `bbox_inches="tight"` trims whitespace and avoids clipped labels in the saved file.

---

## 3. Chart Types

```python
ax.plot(x, y)                                  # line
ax.scatter(x, y, s=40, c=values, cmap="viridis", alpha=0.7)   # points; s=size, c=color values
ax.bar(categories, heights)                    # vertical bars
ax.barh(categories, widths)                    # horizontal bars (better for long labels)
ax.hist(data, bins=30, density=True)           # histogram
ax.boxplot([g1, g2, g3], tick_labels=["a", "b", "c"])
ax.violinplot([g1, g2, g3])
ax.errorbar(x, y, yerr=err, fmt="o-", capsize=3)
ax.fill_between(x, lower, upper, alpha=0.2)    # confidence band / shaded area
ax.stackplot(x, y1, y2, y3, labels=[...])
ax.pie(sizes, labels=labels, autopct="%1.0f%%")
ax.step(x, y, where="post")
ax.hexbin(x, y, gridsize=30)                   # density for many points
ax.contourf(X, Y, Z)
im = ax.imshow(matrix, cmap="viridis")         # heatmap / image
```

### Bar labels (matplotlib 3.4+)

```python
bars = ax.bar(["a", "b", "c"], [3, 7, 5])
ax.bar_label(bars, fmt="%d")
```

### Grouped bars

```python
w = 0.35
idx = np.arange(len(labels))
ax.bar(idx - w/2, a_vals, w, label="A")
ax.bar(idx + w/2, b_vals, w, label="B")
ax.set_xticks(idx, labels=labels)
```

### Heatmap with values and colorbar

```python
fig, ax = plt.subplots()
im = ax.imshow(M, cmap="viridis", vmin=0, vmax=1)
fig.colorbar(im, ax=ax, label="value")
for i in range(M.shape[0]):
    for j in range(M.shape[1]):
        ax.text(j, i, f"{M[i, j]:.2f}", ha="center", va="center", color="white")
ax.set_xticks(range(M.shape[1]), labels=col_names)
ax.set_yticks(range(M.shape[0]), labels=row_names)
```
(`seaborn.heatmap` does this in one call; matplotlib shows what it does under the hood.)

---

## 4. Choosing the Right Chart

Start with the **question**, not the chart.

| Question | Chart |
|---|---|
| How is one variable distributed? | histogram, KDE, box/violin |
| How do two numeric variables relate? | scatter (hexbin if huge), add a trend line |
| How does something change over time? | line |
| Compare values across categories | bar (sorted); horizontal if labels are long |
| Part of a whole | stacked bar, or a bar of shares. Pie only for 2 to 4 slices |
| Compare distributions across groups | box/violin, overlaid histograms |
| Correlation across many variables | heatmap of the correlation matrix |
| Uncertainty / range | error bars, confidence band |
| Ranking | sorted horizontal bar |

Common mistakes: truncating a bar chart's y-axis (bars encode value by length, so start at zero), 3-D effects, rainbow colormaps, too many categories in a pie, dual-axis charts that suggest a relationship that isn't there.

---

## 5. Labels, Ticks, Limits, Scales

```python
ax.set_title("Title", fontsize=14, loc="left")
ax.set_xlabel("X"); ax.set_ylabel("Y")
ax.set_xlim(0, 10); ax.set_ylim(0, None)           # None keeps that bound automatic
ax.invert_yaxis()

ax.set_xticks([0, 5, 10])
ax.set_xticks(range(3), labels=["a", "b", "c"])
ax.tick_params(axis="x", rotation=45, labelsize=9)
ax.xaxis.set_major_formatter("{x:.0f}%")           # format string for tick labels

ax.set_xscale("log"); ax.set_yscale("log")         # also "symlog"
ax.axhline(0, color="k", lw=0.5)                   # horizontal reference line
ax.axvline(5, color="gray", ls=":")
ax.axhspan(2, 4, alpha=0.1)                        # shaded horizontal band
ax.axvspan(3, 5, alpha=0.1)
ax.set_aspect("equal")                             # equal unit lengths on both axes

ax.legend(loc="best", ncol=2, frameon=False, title="Series")
ax.legend(bbox_to_anchor=(1.02, 1), loc="upper left")   # legend outside the plot
```

Use a **log scale** when data spans orders of magnitude or you care about ratios. Label it clearly, since readers often misread log axes.

### Annotation

```python
ax.annotate("peak", xy=(px, py), xytext=(px + 1, py + 0.5),
            arrowprops=dict(arrowstyle="->"))
ax.text(x, y, "label", ha="center", va="bottom", fontsize=9)
```

An annotation that states the takeaway ("Revenue dropped after the price change") is worth more than a perfectly styled axis.

---

## 6. Subplots & Layout

```python
fig, axs = plt.subplots(2, 3, figsize=(12, 6), layout="constrained")
axs[0, 0].plot(x, y)                    # 2-D array of Axes
for ax in axs.flat:                      # iterate over all
    ax.grid(alpha=0.3)

fig, axs = plt.subplots(1, 3, sharey=True)         # shared y-axis, comparable scales
fig, axs = plt.subplots(2, 1, sharex=True)

# irregular layouts
fig, axd = plt.subplot_mosaic([["big", "big"],
                               ["left", "right"]], layout="constrained")
axd["big"].plot(x, y)
axd["left"].hist(data)

fig.suptitle("Figure title")
fig.supxlabel("shared x label"); fig.supylabel("shared y label")
fig.delaxes(axs[1, 2])                              # remove an unused panel
```

**Small multiples** (the same chart repeated per group, on shared axes) are usually clearer than one chart with many overlapping lines:

```python
groups = df["city"].unique()
fig, axs = plt.subplots(1, len(groups), sharey=True, figsize=(4 * len(groups), 3))
for ax, g in zip(axs, groups):
    d = df[df["city"] == g]
    ax.plot(d["date"], d["sales"]); ax.set_title(g)
```

### Two y-axes

```python
fig, ax = plt.subplots()
ax2 = ax.twinx()
ax.plot(x, revenue, "C0");  ax.set_ylabel("Revenue", color="C0")
ax2.plot(x, users, "C1");   ax2.set_ylabel("Users", color="C1")
```
Dual axes can create fake correlations by choosing scales. Prefer two stacked panels with a shared x-axis unless there is a good reason.

---

## 7. Color

```python
ax.plot(x, y, color="C0")              # default cycle colors: C0, C1, C2...
ax.plot(x, y, color="#1f77b4")
ax.plot(x, y, color="tab:orange")
ax.scatter(x, y, c=values, cmap="viridis")
fig.colorbar(sc, ax=ax, label="value")
```

| Data type | Colormap family | Examples |
|---|---|---|
| Sequential (low to high) | single hue / perceptually uniform | `viridis`, `plasma`, `Blues` |
| Diverging (meaningful midpoint) | two hues meeting at neutral | `RdBu`, `coolwarm`, `PuOr` (set `vmin`/`vmax` symmetric) |
| Categorical | distinct colors | `tab10`, `Set2` |

- Avoid `jet`/rainbow: not perceptually uniform, misleading, unfriendly to color-blind viewers.
- `viridis` is perceptually uniform and readable in grayscale.
- Don't encode information in red vs. green alone.
- For a diverging map centered on zero: `vmin=-m, vmax=m` with `m = np.abs(data).max()`.

---

## 8. Dates & Time Series

```python
fig, ax = plt.subplots()
ax.plot(df["date"], df["value"])                    # datetime64 columns work directly

ax.xaxis.set_major_locator(mdates.MonthLocator())
ax.xaxis.set_major_formatter(mdates.DateFormatter("%b %Y"))
fig.autofmt_xdate()                                  # rotate and align date labels
```

```python
# highlight a period
ax.axvspan(pd.Timestamp("2025-03-01"), pd.Timestamp("2025-03-15"), alpha=0.15)
```
Sort by date before plotting a line. An unsorted date column draws lines zig-zagging back and forth.

---

## 9. Styling Globally

```python
plt.style.use("seaborn-v0_8-whitegrid")            # built-in styles: print(plt.style.available)
plt.rcParams.update({
    "figure.figsize": (8, 4),
    "font.size": 11,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.grid": True,
    "grid.alpha": 0.3,
})

with plt.rc_context({"lines.linewidth": 3}):       # temporary override
    fig, ax = plt.subplots(); ax.plot(x, y)
```

Set the style once at the top of a notebook or script so every chart is consistent, rather than formatting each chart by hand.

---

## 10. Pandas Integration

```python
df.plot(x="date", y="sales", ax=ax)                 # pass an existing Axes
df["x"].plot(kind="hist", bins=30, ax=ax)
df.groupby("city")["sales"].sum().sort_values().plot(kind="barh", ax=ax)
df.plot.scatter(x="age", y="spend", c="income", cmap="viridis", ax=ax)
df.boxplot(column="spend", by="city", ax=ax)
```

Pandas plotting is a thin wrapper that returns an `Axes`; keep customizing with the OO methods afterward. For statistical charts (distributions by group, regression fits, pair plots) seaborn is faster to write and builds on matplotlib, so everything here still applies.

---

## 11. Saving & Output

```python
fig.savefig("chart.png", dpi=200, bbox_inches="tight")
fig.savefig("chart.svg")                            # vector, scales without blur
fig.savefig("chart.pdf")                            # vector, good for print/papers
fig.savefig("chart.png", transparent=True)
```

- Raster (PNG) for the web; vector (SVG/PDF) for papers and slides where quality at any size matters.
- `dpi=200` to `300` for print.
- Jupyter: `%matplotlib inline` (static), `%matplotlib widget` (interactive, needs `ipympl`).
- Headless servers/CI: `matplotlib.use("Agg")` before importing pyplot.

---

## 12. Animation (brief)

```python
from matplotlib.animation import FuncAnimation

fig, ax = plt.subplots()
(line,) = ax.plot([], [])
ax.set(xlim=(0, 10), ylim=(-1.2, 1.2))

def update(frame):
    line.set_data(x[:frame], np.sin(x[:frame]))
    return (line,)

ani = FuncAnimation(fig, update, frames=len(x), interval=30, blit=True)
ani.save("wave.gif", writer="pillow")               # keep a reference to `ani`, or it gets garbage-collected
```

Update the **data** of existing artists (`set_data`) instead of redrawing the axes each frame.

---

## 13. Gotchas

- **Memory leak in loops:** every `plt.subplots()` stays alive until closed. Call `plt.close(fig)` after saving when generating many charts.
- **`plt.show()` blocks** in scripts until the window closes. In notebooks it isn't needed for inline backends.
- **Overlapping labels:** use `layout="constrained"` (or `fig.tight_layout()`); rotate tick labels for long categories.
- **Legend is empty:** you must pass `label=` to the plot call (or pass labels to `legend`).
- **Mixing interfaces:** calling `plt.title()` after creating several axes changes only the *current* one, which may not be the one you meant. Use `ax.` methods.
- **`ax` is an array when you make multiple subplots:** `plt.subplots(1, 3)` returns an array of axes; `plt.subplots()` returns a single Axes. `squeeze=False` always returns a 2-D array.
- **Bars from a categorical axis:** pass strings to `ax.bar` and matplotlib orders them in the order given, not alphabetically. Sort your data deliberately.
- **Default histogram bins (10)** can hide structure. Try `bins="auto"` or several values.
- **Alpha on overlapping points** (`alpha=0.3`) reveals density that solid markers hide.
- **Saving looks different from the screen:** DPI and figure size differ. Set `figsize` and `dpi` explicitly and check the saved file.
- **Fonts/glyphs missing:** unusual Unicode characters may not exist in the default font.
- **Don't truncate bar axes:** bars with a non-zero baseline exaggerate differences.

---

## 14. Patterns You'll Reuse

### Distribution comparison (overlaid, normalized)

```python
fig, ax = plt.subplots()
for name, grp in df.groupby("segment"):
    ax.hist(grp["value"], bins=30, density=True, alpha=0.5, label=name)
ax.legend()
```
`density=True` normalizes areas so groups of different sizes are comparable.

### Trend with confidence band

```python
ax.plot(x, mean, label="mean")
ax.fill_between(x, mean - 1.96 * se, mean + 1.96 * se, alpha=0.2)
```

### Scatter with trend line

```python
ax.scatter(x, y, alpha=0.4)
m, b = np.polyfit(x, y, 1)
xs = np.linspace(x.min(), x.max(), 100)
ax.plot(xs, m * xs + b, color="C3")
```

### Sorted horizontal bar with value labels

```python
s = df.groupby("city")["sales"].sum().sort_values()
bars = ax.barh(s.index, s.values)
ax.bar_label(bars, fmt="%.0f", padding=3)
```

### Highlight one bar/line among many

```python
colors = ["C3" if c == "target" else "lightgray" for c in categories]
ax.bar(categories, values, color=colors)
```
Muting everything except the one thing you want people to see is one of the cheapest, most effective design moves.

### Correlation matrix

```python
corr = df.corr(numeric_only=True)
fig, ax = plt.subplots()
im = ax.imshow(corr, cmap="RdBu", vmin=-1, vmax=1)
ax.set_xticks(range(len(corr)), labels=corr.columns, rotation=45, ha="right")
ax.set_yticks(range(len(corr)), labels=corr.columns)
fig.colorbar(im, ax=ax, label="correlation")
```

### Model diagnostics (ties to scikit-learn)

```python
from sklearn.metrics import PrecisionRecallDisplay, RocCurveDisplay
fig, axs = plt.subplots(1, 2, figsize=(10, 4))
RocCurveDisplay.from_estimator(model, X_test, y_test, ax=axs[0])
PrecisionRecallDisplay.from_estimator(model, X_test, y_test, ax=axs[1])
```
Residuals vs. predicted, calibration curves, and learning curves are all plain matplotlib on top of arrays.

---

## 15. Chart Quality Checklist

1. **Title states the takeaway**, not just the topic.
2. Axes labeled, with **units**.
3. Bars start at zero; log axes are labeled as log.
4. Sorted categories (usually by value).
5. Legend only when needed; direct labels beat legends.
6. Color carries meaning (highlight vs. mute), and works for color-blind viewers.
7. Remove chart junk: heavy gridlines, top/right spines, 3-D, shadows.
8. Fonts large enough to read at the size it will be shown.
9. Shared axes on small multiples so panels are comparable.
10. Source and date noted when it's a report.

---

## 16. Quick Reference

```python
fig, ax = plt.subplots(figsize=(8, 4), layout="constrained")

ax.plot / scatter / bar / barh / hist / boxplot / imshow / fill_between / errorbar
ax.set(title=..., xlabel=..., ylabel=..., xlim=..., ylim=...)
ax.legend(); ax.grid(alpha=.3); ax.set_xscale("log")
ax.axhline / axvline / axhspan / axvspan / annotate / text
ax.tick_params(axis="x", rotation=45)
fig.colorbar(im, ax=ax)

fig, axs = plt.subplots(2, 2, sharex=True)       # grid of axes
fig, axd = plt.subplot_mosaic([["a", "a"], ["b", "c"]])

fig.savefig("out.png", dpi=200, bbox_inches="tight")
plt.close(fig)
```

**Debugging checklist when a chart looks wrong:**
1. Am I drawing on the Axes I think I am (`ax.` vs `plt.`)?
2. Is the data sorted, and are dtypes right (dates as datetimes, numbers as numbers)?
3. Are limits, scale (linear/log), and aspect what I intended?
4. Is anything hidden behind something else (`zorder`, `alpha`)?
5. Did I close old figures, and am I looking at the saved file or the screen?
