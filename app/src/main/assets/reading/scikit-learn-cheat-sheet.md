# The scikit-learn Cheat Sheet: Data to Defensible Model

Reference doc. Focus is the workflow and the mistakes that invalidate results (leakage, wrong metric, wrong CV), not an algorithm catalogue. Snippets were run against scikit-learn 1.8.

```python
import numpy as np, pandas as pd
from sklearn.model_selection import train_test_split, cross_val_score, GridSearchCV
from sklearn.pipeline import Pipeline, make_pipeline
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.impute import SimpleImputer
```

---

## 1. Mental Model

Every object follows one of three interfaces:

| Role | Methods | Examples |
|---|---|---|
| **Estimator** | `fit(X, y)` | everything |
| **Transformer** | `fit`, `transform`, `fit_transform` | `StandardScaler`, `PCA`, `OneHotEncoder` |
| **Predictor** | `fit`, `predict`, `score` (+ `predict_proba`) | `LogisticRegression`, `RandomForestClassifier` |

- `X` is `(n_samples, n_features)`; `y` is `(n_samples,)`.
- `fit` **learns state** and stores it in attributes ending in `_` (`coef_`, `mean_`, `classes_`). Unfitted objects don't have them.
- Constructor arguments are **hyperparameters**; they are not learned. `get_params()` / `set_params()` / `clone()` work on them.
- **The one rule that matters:** anything that learns from data (scalers, imputers, encoders, feature selectors) must be fit on **training data only**. Pipelines enforce this for you.

```python
scaler = StandardScaler().fit(X_train)       # learn mean/std from train
X_train_s = scaler.transform(X_train)
X_test_s  = scaler.transform(X_test)         # apply train statistics to test
```

---

## 2. The Standard Workflow

```
1. Define the target and the metric that matches the business cost
2. Split off a test set (and don't touch it)
3. Build a dumb baseline
4. Build a Pipeline: preprocessing + model
5. Cross-validate on the training set
6. Tune hyperparameters (inside CV)
7. Evaluate ONCE on the test set
8. Inspect errors and feature importance
9. Save the whole pipeline
```

### Split

```python
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, stratify=y, random_state=42)    # stratify for classification
```

- `stratify=y` keeps class proportions the same in both splits. Use it for classification, especially with imbalance.
- Always set `random_state` so results are reproducible.
- **Time-ordered data: do not shuffle.** Split by time (see section 8).
- If rows share an entity (same user, patient, device), split by **group**, not by row (see section 8).

### Baseline first

```python
from sklearn.dummy import DummyClassifier, DummyRegressor
DummyClassifier(strategy="most_frequent").fit(X_train, y_train).score(X_test, y_test)
DummyRegressor(strategy="mean")
```

On an 85/15 dataset the "always predict the majority class" baseline already scores 0.85 accuracy. A model that doesn't clearly beat it has learned nothing.

---

## 3. Preprocessing

```python
from sklearn.preprocessing import (StandardScaler, MinMaxScaler, RobustScaler,
                                   OneHotEncoder, OrdinalEncoder, PolynomialFeatures,
                                   FunctionTransformer, LabelEncoder)
from sklearn.impute import SimpleImputer
```

| Tool | Use |
|---|---|
| `StandardScaler` | zero mean, unit variance. Default for linear models, SVM, KNN, PCA |
| `MinMaxScaler` | scale to [0, 1]. Sensitive to outliers |
| `RobustScaler` | median/IQR scaling. Use when outliers exist |
| `OneHotEncoder(handle_unknown="ignore")` | nominal categories |
| `OrdinalEncoder` | ordered categories (give `categories=` explicitly) |
| `SimpleImputer(strategy=...)` | `mean`, `median`, `most_frequent`, `constant` |
| `FunctionTransformer(np.log1p)` | wrap any function as a transformer |
| `PolynomialFeatures` | interaction and power terms |

**Which models need scaling?**

| Needs scaling | Doesn't care |
|---|---|
| Linear/logistic regression (esp. regularized), SVM, KNN, K-means, PCA, neural nets | Decision trees, random forests, gradient boosting |

```python
OneHotEncoder(handle_unknown="ignore", sparse_output=False)   # unseen categories at predict time become all zeros
```
Without `handle_unknown="ignore"`, a category that appears only in test data raises an error in production.

`LabelEncoder` is for the **target** only. Use `OrdinalEncoder`/`OneHotEncoder` for features.

---

## 4. Pipelines & ColumnTransformer

Real data mixes numeric and categorical columns. This is the template to copy:

```python
num_cols = ["age", "income"]
cat_cols = ["city"]

preprocess = ColumnTransformer([
    ("num", Pipeline([
        ("impute", SimpleImputer(strategy="median")),
        ("scale",  StandardScaler()),
    ]), num_cols),
    ("cat", OneHotEncoder(handle_unknown="ignore"), cat_cols),
], remainder="drop")                       # "passthrough" keeps untouched columns

model = Pipeline([
    ("pre", preprocess),
    ("clf", LogisticRegression(max_iter=1000)),
])

model.fit(X_train, y_train)
model.predict(X_test)
model.score(X_test, y_test)
```

```python
model.named_steps["pre"].get_feature_names_out()     # names after transformation
model.set_output(transform="pandas")                  # transformers return DataFrames
make_pipeline(StandardScaler(), LogisticRegression()) # auto-named shortcut
```

Why pipelines matter:
- **They prevent leakage.** In cross-validation, the whole pipeline is re-fit on each training fold, so the scaler never sees the validation fold.
- One object to tune, save, and deploy.
- Hyperparameters are addressed as `stepname__param` (double underscore): `clf__C`, `pre__num__impute__strategy`.

### Custom transformer

```python
from sklearn.base import BaseEstimator, TransformerMixin

class LogClip(BaseEstimator, TransformerMixin):
    def __init__(self, lower=0.0):          # only store params, no logic here
        self.lower = lower
    def fit(self, X, y=None):
        return self                          # always return self
    def transform(self, X):
        return np.log1p(np.clip(X, self.lower, None))
```

---

## 5. Models: Picking a Sensible Default

```python
from sklearn.linear_model import LinearRegression, Ridge, Lasso, ElasticNet, LogisticRegression
from sklearn.tree import DecisionTreeClassifier
from sklearn.ensemble import (RandomForestClassifier, GradientBoostingClassifier,
                              HistGradientBoostingClassifier)
from sklearn.neighbors import KNeighborsClassifier
from sklearn.svm import SVC
```

| Situation | Start with |
|---|---|
| Need interpretability / quick baseline | Linear or logistic regression |
| Tabular data, want strong accuracy with little tuning | `HistGradientBoosting*` (fast, handles NaN natively) |
| Robust, low-tuning, good default | `RandomForest*` |
| Many features, want automatic sparsity | `Lasso` / L1 logistic |
| Correlated features, shrink coefficients | `Ridge` |
| Small data, non-linear boundary | `SVC` (scaled), `KNN` (scaled) |

### Regularization direction (easy to get backwards)

- `Ridge(alpha=...)`, `Lasso(alpha=...)`: **larger alpha = stronger** regularization.
- `LogisticRegression(C=...)`, `SVC(C=...)`: **larger C = weaker** regularization (C is the inverse).
- `LogisticRegression` is **L2-regularized by default**. It is not "plain" logistic regression unless you set `C` very large.

### Bias-variance, practically

| Symptom | Diagnosis | Try |
|---|---|---|
| Train score low, validation score low | **Underfitting** (high bias) | richer model, more features, less regularization |
| Train score high, validation score much lower | **Overfitting** (high variance) | more data, simpler model, more regularization, fewer features |

```python
from sklearn.model_selection import learning_curve, validation_curve
train_sizes, train_scores, val_scores = learning_curve(model, X, y, cv=5,
                                                        train_sizes=[0.3, 0.6, 1.0])
```
A learning curve where validation still improves as data grows means more data would help. Flat curves mean change the model or features.

---

## 6. Metrics: Pick the Right One Before Training

### Classification

| Metric | Answers | Watch out |
|---|---|---|
| `accuracy_score` | fraction correct | misleading on imbalanced data |
| `precision_score` | of predicted positives, how many were real? | cost of false alarms |
| `recall_score` | of real positives, how many did we find? | cost of misses |
| `f1_score` | harmonic mean of precision and recall | ignores true negatives |
| `roc_auc_score` | ranking quality across all thresholds | optimistic on heavy imbalance |
| `average_precision_score` | area under the precision-recall curve | **preferred for rare positives** |
| `log_loss` | quality of predicted probabilities | punishes overconfidence |

```python
from sklearn.metrics import (classification_report, confusion_matrix, roc_auc_score,
                             average_precision_score, precision_recall_curve,
                             ConfusionMatrixDisplay, RocCurveDisplay, PrecisionRecallDisplay)

proba = model.predict_proba(X_test)[:, 1]           # probability of the positive class
print(classification_report(y_test, model.predict(X_test)))
confusion_matrix(y_test, model.predict(X_test))     # rows = true, cols = predicted: [[TN FP],[FN TP]]
roc_auc_score(y_test, proba)                        # pass PROBABILITIES, not hard labels
average_precision_score(y_test, proba)

ConfusionMatrixDisplay.from_estimator(model, X_test, y_test)
RocCurveDisplay.from_estimator(model, X_test, y_test)
```

### Regression

```python
from sklearn.metrics import mean_absolute_error, mean_squared_error, root_mean_squared_error, r2_score
mean_absolute_error(y, pred)       # same units as y, robust to outliers
root_mean_squared_error(y, pred)   # same units as y, punishes big errors (scikit-learn 1.4+)
r2_score(y, pred)                  # share of variance explained; can be negative
```
`R²` can be negative on test data: that means the model is worse than predicting the mean.

### Thresholds are a decision, not a constant

`predict()` uses 0.5. That is rarely the right operating point.

```python
prec, rec, thr = precision_recall_curve(y_test, proba)
f1 = 2 * prec * rec / (prec + rec + 1e-12)
best_thr = thr[np.argmax(f1[:-1])]                  # threshold maximizing F1 (arrays are len(thr)+1)
y_pred = (proba >= best_thr).astype(int)
```
Choose the threshold from business cost (what does a false positive cost vs. a false negative?) and set it on **validation** data, never on test data.

---

## 7. Imbalanced Classes

1. **Use the right metric:** average precision, recall at a fixed precision, PR curve. Not accuracy.
2. **Stratify** splits and CV folds.
3. **`class_weight="balanced"`** on most classifiers: cheap, often enough.
4. **Tune the threshold** (section 6).
5. Resampling (SMOTE etc., from `imbalanced-learn`) **only inside the training fold**, via an `imblearn` pipeline. Resampling before the split leaks duplicates into validation.

```python
LogisticRegression(class_weight="balanced", max_iter=1000)
RandomForestClassifier(class_weight="balanced_subsample")
```
Note that `class_weight` shifts predicted probabilities, so they are no longer calibrated.

---

## 8. Cross-Validation

```python
from sklearn.model_selection import (cross_val_score, cross_validate, StratifiedKFold,
                                     KFold, GroupKFold, TimeSeriesSplit)

cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=0)
scores = cross_val_score(model, X_train, y_train, cv=cv, scoring="average_precision")
print(scores.mean(), scores.std())

res = cross_validate(model, X_train, y_train, cv=cv,
                     scoring=["accuracy", "f1"], return_train_score=True)
res["test_f1"], res["train_f1"]        # compare train vs validation to spot overfitting
```

**Pick the splitter that matches how the model will be used:**

| Data | Splitter |
|---|---|
| Independent rows, classification | `StratifiedKFold` |
| Independent rows, regression | `KFold(shuffle=True)` |
| Multiple rows per entity (user, patient) | `GroupKFold` (pass `groups=`) |
| Time-ordered | `TimeSeriesSplit` (train always precedes validation) |

```python
cross_val_score(model, X, y, cv=GroupKFold(5), groups=user_ids)
for tr, te in TimeSeriesSplit(5).split(X): ...        # expanding window, no future leakage
```

Random KFold on grouped or time-ordered data produces **optimistic scores** that collapse in production, because the model has effectively seen the same user (or the future) during training.

---

## 9. Hyperparameter Tuning

```python
from sklearn.model_selection import GridSearchCV, RandomizedSearchCV

param_grid = {
    "clf__C": [0.01, 0.1, 1, 10],
    "pre__num__impute__strategy": ["mean", "median"],
}
search = GridSearchCV(model, param_grid, cv=cv, scoring="average_precision", n_jobs=-1)
search.fit(X_train, y_train)

search.best_params_
search.best_score_
search.best_estimator_                  # already refit on all of X_train
search.cv_results_                      # full table: pd.DataFrame(search.cv_results_)
search.score(X_test, y_test)            # final, one-time test evaluation
```

- `RandomizedSearchCV(model, distributions, n_iter=30)` beats grid search when there are many hyperparameters.
- `best_score_` is **optimistically biased** (you picked the max over many tries). The untouched test set is the honest number.
- For an honest estimate of the whole tuning procedure, use **nested CV**: a search object inside `cross_val_score`.
- Tune the **pipeline**, not just the final model, so preprocessing choices are searched without leakage.

---

## 10. Leakage: How Good Scores Lie

Leakage is information from outside the training data (or from the future) influencing the model. It produces great validation scores and bad production results.

| Leak | Example | Fix |
|---|---|---|
| **Preprocessing before split** | `StandardScaler().fit(X)` on all data, then split | Fit inside a Pipeline |
| **Target leakage** | A feature computed after the outcome (e.g. "refund issued" predicting churn) | Audit each feature: *would I know this at prediction time?* |
| **Duplicate rows across splits** | Same record in train and test | Deduplicate before splitting |
| **Group leakage** | Same patient/user in both train and validation | `GroupKFold` |
| **Time leakage** | Random split of time series | Time-ordered split |
| **Target encoding on full data** | Mean-of-target encoding computed before CV | Compute within each training fold |
| **Tuning on test** | Choosing a threshold or model by test score | Use validation data; touch test once |

Warning sign: a validation score that looks too good (AUC 0.99 on a hard problem). Suspect leakage before celebrating.

---

## 11. Interpreting Models

```python
# linear models: standardized coefficients are comparable
pd.Series(model.named_steps["clf"].coef_[0],
          index=model.named_steps["pre"].get_feature_names_out()).sort_values()

# trees: impurity-based importance (fast, but biased toward high-cardinality features)
rf.feature_importances_

# model-agnostic, computed on held-out data: preferred
from sklearn.inspection import permutation_importance
pi = permutation_importance(model, X_test, y_test, n_repeats=10, random_state=0,
                            scoring="average_precision")
pd.Series(pi.importances_mean, index=X_test.columns).sort_values(ascending=False)
```

- **Permutation importance** shuffles one feature and measures the score drop, so it reflects what the model actually relies on. Correlated features share (and dilute) importance.
- **Importance is not causation.** A feature can matter to the model without driving the outcome.
- Always inspect errors directly: sort misclassified rows by confidence and look at them.

---

## 12. Unsupervised: Clustering & Dimensionality Reduction

```python
from sklearn.cluster import KMeans, DBSCAN
from sklearn.decomposition import PCA
from sklearn.metrics import silhouette_score

Xs = StandardScaler().fit_transform(X)                 # scale first: distances drive both methods

km = KMeans(n_clusters=4, n_init="auto", random_state=0).fit(Xs)
km.labels_, km.cluster_centers_, km.inertia_
silhouette_score(Xs, km.labels_)                       # -1..1, higher = better-separated clusters

pca = PCA(n_components=0.95).fit(Xs)                   # keep 95% of variance
pca.n_components_
pca.explained_variance_ratio_
Z = pca.transform(Xs)
```

- **Choosing k:** plot inertia vs. k (elbow) and silhouette vs. k; neither is decisive, so also ask whether the clusters mean anything to the business.
- K-means assumes roughly spherical, similar-size clusters. `DBSCAN` finds arbitrary shapes and labels noise as `-1` but needs `eps` tuned.
- PCA components are linear combinations; they are harder to interpret than raw features. Use it for compression, de-correlation, and visualization.
- t-SNE/UMAP are for **visualization only**: distances between clusters in the plot are not meaningful.

---

## 13. Feature Selection

```python
from sklearn.feature_selection import SelectKBest, f_classif, RFE, SelectFromModel

SelectKBest(f_classif, k=10)                           # univariate filter
SelectFromModel(Lasso(alpha=0.01))                     # keep features with nonzero coefficients
RFE(LogisticRegression(max_iter=1000), n_features_to_select=10)
```
Selection is a learned step. Put it **inside the pipeline** so it only sees training folds; selecting features on the full dataset first is a classic leak.

---

## 14. Saving & Loading

```python
import joblib
joblib.dump(model, "model.joblib")                     # save the WHOLE pipeline, not just the classifier
model = joblib.load("model.joblib")
```
- Save the full pipeline so production applies identical preprocessing.
- Pin the scikit-learn version: a pickle/joblib file may not load (or may behave differently) across versions.
- Only load model files you trust; unpickling can execute arbitrary code.

---

## 15. Gotchas

- **`predict_proba` columns follow `model.classes_`.** Column 1 is the positive class only if `classes_` is `[0, 1]`. Check it.
- **`roc_auc_score(y, y_pred)` with hard labels** gives a misleadingly low number. Pass probabilities.
- **`fit_transform` on test data** is a leak. Use `transform`.
- **Metric argument order is `(y_true, y_pred)`.** Swapping silently changes precision and recall.
- **`cross_val_score` returns scores, not models.** Fit the final model separately on all training data.
- **Scorer sign:** loss-style scorers are negated (`"neg_mean_squared_error"`) so that higher is always better.
- **`shuffle=True` is required** in `KFold`/`StratifiedKFold` if the data is ordered and you pass `random_state`.
- **Random forest/boosting scores vary** with `random_state`; compare models with the **same CV splits**.
- **High accuracy ≠ good model** on imbalanced data. Always compare against the dummy baseline.
- **`OneHotEncoder` on high-cardinality columns** explodes feature count. Consider target-aware or hashing approaches (inside CV) or tree models with native categorical support.
- **Float vs. int labels:** `y` as floats can make `classification` estimators complain. Cast with `astype(int)`.
- **Don't evaluate on training data** and call it performance.

---

## 16. Interview Questions

**What is data leakage and how do you prevent it?**
Information unavailable at prediction time influencing training. Prevent with split-first, Pipelines so preprocessing is fit per training fold, group/time-aware CV, and auditing features for post-outcome information.

**Why use a Pipeline?**
Prevents leakage in CV, makes tuning cover preprocessing, and gives one deployable artifact.

**Accuracy is 95%. Is the model good?**
Not without knowing the class balance. At 95/5, always predicting the majority class gets 95%. Compare to a baseline and look at precision, recall, and average precision.

**Precision vs. recall: which do you optimize?**
Depends on the cost of each error. Fraud screening with human review favors recall; automatic account bans favor precision. Pick a threshold from those costs.

**ROC-AUC vs. PR-AUC?**
ROC-AUC is insensitive to class balance and can look good when positives are rare. PR-AUC reflects performance on the minority class, so it's preferred for imbalanced problems.

**How do you detect overfitting?**
Large gap between training and validation scores; learning curves; validation score dropping as complexity rises. Fix with more data, regularization, simpler models, early stopping.

**Bias vs. variance?**
Bias: error from overly simple assumptions (underfitting). Variance: sensitivity to the training sample (overfitting). Increasing complexity trades the first for the second.

**Why scale features?**
Distance-based and gradient/regularization-based methods are sensitive to feature scale. Trees are not.

**Ridge vs. Lasso?**
Both penalize coefficient size. L2 (Ridge) shrinks all coefficients smoothly and handles correlated features; L1 (Lasso) drives some exactly to zero, so it also selects features.

**Random forest vs. gradient boosting?**
Forest: many deep trees trained independently on bootstrap samples and averaged (reduces variance). Boosting: shallow trees trained sequentially, each correcting the previous errors (reduces bias); usually more accurate, more sensitive to tuning.

**How do you cross-validate time series?**
Forward-chaining splits (`TimeSeriesSplit`): train on the past, validate on the future. Never shuffle.

**How do you handle missing values?**
First ask why they are missing. Then impute inside the pipeline (median/mode/constant plus a missing-indicator), or use models that handle NaN natively (`HistGradientBoosting`).

**Your CV score is 0.95 but production is 0.70. Why?**
Likely leakage, distribution shift between training and production, group/time structure ignored in CV, or a threshold/metric mismatch. Diagnose by comparing feature distributions and re-running CV with proper grouping.

**How would you explain a prediction to a stakeholder?**
Use a simple model or permutation importance for global drivers, and examine individual examples; be clear that importance is about the model, not causation.

---

## 17. Quick Reference

```python
# split
train_test_split(X, y, test_size=.2, stratify=y, random_state=42)

# pipeline
ColumnTransformer([("num", num_pipe, num_cols), ("cat", OneHotEncoder(handle_unknown="ignore"), cat_cols)])
Pipeline([("pre", pre), ("clf", clf)])

# evaluate
cross_val_score(pipe, X, y, cv=StratifiedKFold(5, shuffle=True, random_state=0), scoring="average_precision")
classification_report, confusion_matrix, roc_auc_score, average_precision_score
root_mean_squared_error, mean_absolute_error, r2_score

# tune
GridSearchCV(pipe, {"clf__C": [...]}, cv=cv, scoring=...)

# inspect
permutation_importance, get_feature_names_out, learning_curve

# persist
joblib.dump(pipe, "model.joblib")
```

**Checklist before trusting a result:**
1. Is there a dummy baseline, and does the model clearly beat it?
2. Was every learned transform fit on training data only (Pipeline)?
3. Does the CV scheme match the data (groups, time, imbalance)?
4. Does the metric match the real cost of errors?
5. Was the test set used exactly once?
6. Does any feature leak the outcome?
7. Did I look at actual errors, not just the aggregate score?
