# The Statistics Cheat Sheet: For Actual Beginners

A reference doc that assumes you're starting from zero — every term gets explained in plain language before it gets a formula, and every formula gets a worked example. Read it top to bottom once, then keep it open as a lookup.

---

## 1. What Statistics Actually Is (Before Any Math)

Statistics is the discipline of **making sense of data** — collecting it, summarizing it, and drawing reasonable conclusions from it, especially when you can't look at every single data point in existence and have to work from a smaller sample instead.

Two big halves worth knowing by name from the start, because everything else in this document falls into one or the other:

- **Descriptive statistics** — summarizing and describing the data you actually have in front of you. "The average test score in this class was 78." No claims about anything beyond this specific dataset.
- **Inferential statistics** — using a smaller sample of data to make a reasonable guess about a larger group you didn't fully measure. "Based on surveying 1,000 voters, we estimate 52% of the country supports this policy." This is where probability, confidence, and uncertainty all enter the picture.

Almost every beginner statistics course, and this cheat sheet, starts with descriptive statistics because you need to be able to summarize data clearly before you can responsibly make guesses based on it.

---

## 2. Population vs. Sample — The Distinction Everything Else Depends On

- **Population** — every single member of the group you actually care about. "All registered voters in the country." Usually too large, expensive, or flat-out impossible to fully measure.
- **Sample** — a smaller subset of the population that you actually collect data from. "1,000 voters you called and surveyed."

Almost all real-world statistics is about using a **sample** to make honest, appropriately uncertain statements about a **population** you couldn't fully measure. This single distinction is why statistics has to deal with uncertainty and probability at all — if you could measure everyone, you wouldn't need to guess, you'd just know.

**Notation you'll see constantly, worth memorizing early:**

| Concept | Population symbol | Sample symbol |
|---|---|---|
| Mean (average) | μ (mu) | x̄ (x-bar) |
| Standard deviation | σ (sigma) | s |
| Size | N | n |

If you ever see a formula with μ or σ, it's talking about the whole population. If you see x̄ or s, it's talking about a sample. This one distinction unlocks a surprising amount of otherwise-confusing textbook notation.

---

## 3. Types of Data — Know What You're Looking At First

Before you can pick the right way to summarize or analyze data, you need to know what kind of data it is.

- **Categorical (qualitative)** — labels, categories, no inherent numeric meaning. Eye color, country of birth, yes/no survey answers.
  - **Nominal** — categories with no natural order. "Red, Blue, Green."
  - **Ordinal** — categories WITH a natural order, but the gaps between them aren't necessarily equal. "Small, Medium, Large" or a 1-5 satisfaction rating.
- **Numerical (quantitative)** — actual numbers you can do math on.
  - **Discrete** — countable, whole-number values. Number of kids in a family, number of cars sold.
  - **Continuous** — can take any value within a range, including fractions/decimals. Height, weight, temperature, time.

Why this matters practically: you can calculate a meaningful average for continuous data (average height). Calculating an "average" of nominal categorical data (average eye color) is nonsense — there's no number to average. Knowing your data type up front is what stops you from applying the wrong tool to the wrong kind of data later in this document.

---

## 4. Measures of Central Tendency — "What's a Typical Value Here?"

These are the three ways to answer "what's a typical, central value in this dataset" — and beginners often assume they're interchangeable. They're not, and the difference matters.

### Mean (the average)

Add up every value, divide by how many values there are.

```
Data: 4, 8, 6, 5, 3, 100
Mean = (4 + 8 + 6 + 5 + 3 + 100) / 6 = 126 / 6 = 21
```

### Median (the middle value)

Sort the data, find the value exactly in the middle. If there's an even number of values, average the two middle ones.

```
Data: 4, 8, 6, 5, 3, 100
Sorted: 3, 4, 5, 6, 8, 100
Middle two values: 5 and 6
Median = (5 + 6) / 2 = 5.5
```

### Mode (the most frequent value)

Whichever value shows up most often. A dataset can have one mode, several (multi-modal), or none at all if nothing repeats.

```
Data: 4, 8, 6, 5, 3, 100
No value repeats — no mode.

Data: 4, 4, 6, 5, 3, 4
Mode = 4 (appears three times)
```

### Why the mean lied to you in that example above

Look back at the mean (21) versus the median (5.5) for the exact same dataset. The mean got dragged way up by one extreme value — **100** — sitting far from the rest of the data. The median barely noticed it was there. This is the single most important practical lesson in this entire section:

**The mean is sensitive to outliers. The median is not.** This is exactly why you'll hear "median household income" in the news instead of "average household income" — a small number of billionaires would drag the *mean* income up to a number that badly misrepresents what a typical household actually earns, while the median stays anchored to what's actually typical. When your data has extreme values or is skewed, reach for the median. When your data is fairly evenly spread with no wild outliers, the mean is a fine, more information-rich choice.

---

## 5. Measures of Spread — "How Spread Out Is the Data?"

Knowing the typical value isn't enough — two datasets can have the identical mean and look completely different.

```
Dataset A: 20, 21, 19, 20, 20   (mean = 20, everything clustered tight)
Dataset B: 0, 40, 5, 35, 20      (mean = 20, wildly spread out)
```

Both have a mean of 20. They are not remotely similar datasets. **Measures of spread** capture that difference.

### Range — simplest possible measure, just the gap between extremes

```
Range = max - min
Dataset B: 40 - 0 = 40
```

Simple, but entirely determined by just two values — one weird outlier and your range is misleading, same fragility as the mean.

### Variance — the average squared distance from the mean

```
Steps:
1. Find the mean.
2. Subtract the mean from each value (this is each value's "deviation").
3. Square each deviation (makes everything positive, and punishes bigger gaps more).
4. Average those squared deviations.

Data: 2, 4, 6, 8
Mean = 5
Deviations: 2-5=-3, 4-5=-1, 6-5=1, 8-5=3
Squared: 9, 1, 1, 9
Average of squared deviations = (9+1+1+9)/4 = 20/4 = 5
Variance = 5
```

### Standard Deviation — the square root of variance, and the one you'll actually use

```
Standard deviation = √variance = √5 ≈ 2.24
```

**Why bother with standard deviation when you already have variance?** Because variance is in *squared* units — if your data is in dollars, variance is in "dollars squared," which means nothing intuitively. Taking the square root brings it back into the original units (dollars), so a standard deviation of 2.24 actually means something concrete: "on average, values sit about 2.24 units away from the mean." This is why standard deviation, not variance, is the number that gets reported and interpreted in practice — variance is mostly a stepping stone to get there.

**Rule of thumb for reading a standard deviation:** a small standard deviation relative to the mean means the data is tightly clustered and predictable. A large one means the data is spread out and variable. Same mean, wildly different standard deviation, is exactly the Dataset A vs. Dataset B situation above.

---

## 6. Visualizing Data — Before You Trust Any Number, Look At the Shape

### Histogram — shows the distribution/shape of numerical data

Bins numerical data into ranges and shows how many values fall into each range as bars. This is usually the very first thing you should do with a new numerical dataset — before computing a single statistic, look at the shape.

### The Normal Distribution ("bell curve") — the shape you'll see referenced constantly

A **symmetric**, bell-shaped distribution where values cluster around the mean and taper off evenly in both directions. An enormous number of real-world phenomena approximate this shape — heights, test scores, measurement errors — which is exactly why it gets so much attention in intro statistics.

**The 68-95-99.7 rule (empirical rule)** — for data that's normally distributed:
- About **68%** of values fall within **1 standard deviation** of the mean.
- About **95%** of values fall within **2 standard deviations** of the mean.
- About **99.7%** of values fall within **3 standard deviations** of the mean.

```
Example: adult heights, mean = 170cm, standard deviation = 10cm
~68% of people are between 160cm and 180cm
~95% of people are between 150cm and 190cm
~99.7% of people are between 140cm and 200cm
```

### Skewness — when the bell curve isn't symmetric

- **Right-skewed (positive skew)** — a long tail stretching to the right; most values are low/moderate, with a few very high outliers dragging the tail out. Household income is the classic real-world example — most people cluster at moderate incomes, a small number of very high earners create a long right tail. In right-skewed data, **mean > median**, because that long tail of high values pulls the mean upward.
- **Left-skewed (negative skew)** — the mirror image, a long tail stretching left. Age at death in a population with generally long lifespans is a classic example — most people live to old age, with a smaller number of early deaths creating a left tail. Here, **mean < median**.

This is the direct, practical payoff of section 4's mean-vs-median discussion: checking whether mean and median are close together or far apart is actually a quick, informal way to check whether your data is skewed, without even drawing the histogram.

### Boxplot — a compact summary of spread and outliers in one small picture

Shows the median, the middle 50% of the data (the "box," bounded by the first and third quartiles — more on quartiles below), and individual points marked separately as likely outliers if they sit far outside that range. Extremely useful for quickly comparing the spread of several groups side by side.

---

## 7. Quartiles & Percentiles — Splitting Data Into Chunks

A **percentile** tells you the value below which a given percentage of the data falls. "You scored in the 90th percentile" means 90% of other test-takers scored at or below you.

**Quartiles** are just the specific percentiles that split data into four equal chunks:

- **Q1 (25th percentile)** — 25% of the data falls below this value.
- **Q2 (50th percentile)** — this IS the median, just under another name.
- **Q3 (75th percentile)** — 75% of the data falls below this value.

**Interquartile Range (IQR)** — the range covered by the middle 50% of the data:

```
IQR = Q3 - Q1
```

The IQR is genuinely useful specifically because, like the median, it's **resistant to outliers** — a single wild extreme value at the very top or bottom of your dataset barely moves Q1 or Q3 at all, unlike the full range (section 5), which one outlier can blow wide open. This is also the standard, principled way outliers get flagged in a boxplot: any point sitting more than 1.5×IQR beyond Q1 or Q3 gets marked as a likely outlier, rather than someone just eyeballing "that number looks kind of big."

---

## 8. Probability Basics — The Language Everything After This Uses

**Probability** is a number between 0 and 1 (or 0% to 100%) describing how likely something is. 0 means impossible, 1 means certain.

```
P(event) = number of favorable outcomes / total number of possible outcomes

Rolling a fair six-sided die, probability of rolling a 4:
P(rolling a 4) = 1/6 ≈ 0.167 (about 16.7%)
```

### A few core rules, kept plain

- **Complement rule:** the probability something does NOT happen = 1 minus the probability it does.
  ```
  P(not rolling a 4) = 1 - 1/6 = 5/6
  ```
- **Independent events** — one event's outcome has zero effect on the other's. Flipping a coin twice: the first flip has no influence on the second. For independent events, multiply their individual probabilities to get the probability of both happening:
  ```
  P(heads, then heads again) = 1/2 × 1/2 = 1/4
  ```
- **Conditional probability** — the probability of something happening, GIVEN that something else has already happened. Written `P(A | B)`, read as "probability of A, given B." "What's the probability it rains, given that it's already cloudy" is a fundamentally different, usually higher, number than "what's the probability it rains" with no information at all. This idea — that new information should update your probability estimate — is the seed that eventually grows into Bayesian statistics, a whole separate branch beyond this beginner sheet's scope, but worth knowing the seed exists.

---

## 9. The Normal Distribution & Z-Scores — Comparing Apples to Oranges

A **z-score** tells you how many standard deviations a specific value sits away from the mean — and it's the tool that lets you meaningfully compare values from two totally different datasets or scales.

```
z = (x - mean) / standard deviation
```

```
Example: You scored 85 on a test with mean 70, standard deviation 10.
z = (85 - 70) / 10 = 1.5   → you scored 1.5 standard deviations above average

Your friend scored 90 on a DIFFERENT test with mean 80, standard deviation 15.
z = (90 - 80) / 15 = 0.67   → they scored 0.67 standard deviations above average
```

Even though your friend's raw score (90) is higher than yours (85), your **z-score** (1.5) is higher than theirs (0.67) — meaning you actually did better *relative to your respective tests*, despite the lower raw number. This is the entire point of a z-score: it strips away the original units and scale, so values from completely different distributions become directly, honestly comparable.

---

## 10. Correlation — Do Two Things Move Together?

**Correlation** measures the strength and direction of a relationship between two numerical variables, using a number called the **correlation coefficient (r)**, which always falls between -1 and +1.

- **r close to +1** — strong positive relationship: as one variable goes up, the other tends to go up too. (Height and shoe size.)
- **r close to -1** — strong negative relationship: as one goes up, the other tends to go down. (Hours spent on social media and hours spent sleeping, in a lot of studies people love to cite.)
- **r close to 0** — little to no linear relationship between the two variables.

### The sentence you should tattoo on your brain before doing anything else with correlation

**Correlation does not imply causation.** Two variables can be strongly correlated without either one causing the other — ice cream sales and drowning deaths both rise in summer, correlated with each other, but ice cream doesn't cause drownings; a third factor (hot weather, more people swimming) drives both. Every single time you see a correlation reported anywhere — a headline, a study, a chart someone posted — the correct next question is always "what third factor might explain both of these at once," not "great, so A causes B."

---

## 11. Sampling — Why Your Sample Actually Needs to Be Random

Recall from section 2: you almost never measure an entire population, you measure a sample and try to generalize. That generalization is only trustworthy if the sample was collected properly.

- **Random sampling** — every member of the population has an equal, known chance of being selected. This is what makes generalizing from sample to population statistically defensible in the first place.
- **Sampling bias** — the sample systematically fails to represent the population, usually because of *how* it was collected, not how large it is. The infamous historical example: a 1936 U.S. presidential poll surveyed over two million people (a genuinely enormous sample) via telephone directories and car registrations — and got the result completely wrong, because in 1936 phone and car ownership skewed heavily toward wealthier people, who voted differently from the population as a whole. A huge biased sample is still a biased sample — **size does not fix bias**, only proper random selection does.
- **Sample size and confidence** — all else being equal, a larger *properly random* sample gives you more confidence that your sample statistic (like x̄) is close to the true population value (μ). This is the intuition behind why polls report margins of error, and why that margin shrinks as sample size grows.

---

## 12. Hypothesis Testing — The Logic, in Plain English First

This is usually where beginner statistics starts to feel like a foreign language, so here's the actual plain-English logic before any jargon:

You start by assuming nothing interesting is happening (this assumption is called the **null hypothesis**). Then you look at your data and ask: "if nothing interesting really were happening, how surprising would it be to see data like this just by random chance?" If the answer is "extremely surprising, this would almost never happen by chance alone," you reject the assumption that nothing's happening — you conclude something real is probably going on.

- **Null hypothesis (H₀)** — the boring default assumption: no effect, no difference, no relationship. "This new drug has no effect on blood pressure."
- **Alternative hypothesis (H₁ or Hₐ)** — what you actually suspect might be true instead. "This new drug DOES affect blood pressure."
- **p-value** — the probability of seeing data at least this extreme, IF the null hypothesis were actually true. A small p-value means "this would have been a really unlikely coincidence if nothing were really going on" — which is evidence against the null hypothesis.
- **Significance level (α, alpha)** — a threshold you decide on *before* looking at the results, commonly 0.05. If your p-value comes in below this threshold, you call the result **"statistically significant"** and reject the null hypothesis.

```
Example, in plain terms:
H₀: This coin is fair (50/50).
You flip it 100 times and get 80 heads.
p-value calculation asks: "if this coin really were fair, how likely is it we'd see
80+ heads out of 100 just by random chance?"
That turns out to be an extremely small probability.
Since it's below your significance threshold (say, 0.05), you reject H₀ —
you conclude the coin probably isn't fair.
```

**The most common beginner misreading of a p-value, worth calling out explicitly:** a p-value of 0.03 does **not** mean "there's a 3% chance the null hypothesis is true," and it does not mean "there's a 97% chance your finding is correct." It specifically means "if the null hypothesis WERE true, there'd only be a 3% chance of seeing data this extreme by random chance alone." Subtle difference, constantly misstated even in published research, genuinely worth getting right.

---

## 13. Confidence Intervals — Owning Up to Your Uncertainty, With a Number

Instead of just giving one single-number estimate from a sample, a **confidence interval** gives a *range* of plausible values for the true population parameter, along with a stated confidence level.

```
"We are 95% confident the true population mean falls between 68 and 74."
```

This does **not** mean "there's a 95% probability the true mean is in this specific range" (the true mean is a fixed number, it either is or isn't in there, no probability about it once it's already computed). It means: if you repeated this same sampling process many, many times and built a confidence interval each time, about 95% of those intervals would contain the true population value. Wider intervals mean more uncertainty (often from a smaller sample); narrower intervals mean more precision (often from a larger sample) — the width of the interval is itself an honest, quantified admission of how much your sample size and variability actually let you claim to know.

---

## 14. Common Beginner Mistakes, Collected in One Place

- **Confusing correlation with causation** (section 10) — the single most common statistical error in casual conversation and, uncomfortably often, in published headlines too.
- **Using the mean on skewed data** (section 4) — reach for the median when outliers or skew are present; the mean will quietly mislead you.
- **Trusting a big sample without checking if it was randomly collected** (section 11) — the 1936 poll disaster is the textbook cautionary tale for exactly this reason.
- **Misreading a p-value as "probability the hypothesis is true"** (section 12) — it isn't that, it's a statement about how surprising your data would be under the null hypothesis.
- **Assuming "statistically significant" means "practically important"** — with a large enough sample, even a tiny, real-world-meaningless difference can become "statistically significant." Significance tells you the effect probably isn't pure random noise; it says nothing on its own about whether the effect is actually large enough to matter in practice.
- **Ignoring the shape of the data before computing anything** (section 6) — always look at a histogram before you trust a mean, a standard deviation, or a correlation coefficient. Numbers can look identical while the underlying shapes are completely different (this is the entire point of "Anscombe's Quartet," a famous set of four datasets with nearly identical means, variances, and correlations, that look wildly different once actually plotted).

---

## 15. Where to Go Next, Once This Feels Comfortable

This cheat sheet deliberately stops at the beginner layer. Once sections 1–14 feel solid and not just memorized, the natural next topics are: different probability distributions beyond the normal one (binomial, Poisson), t-tests and chi-square tests as specific, named hypothesis tests for specific situations, linear regression (quantifying and predicting from a correlation, not just describing it), and eventually Bayesian statistics, which flips the whole hypothesis-testing logic in section 12 around and reasons about probability rather differently.

---

*Pairs with the rest of this reference series — same "explain the actual problem before the syntax/formula" approach as the Go, Rust, Python, Docker, and Kubernetes cheat sheets.*
