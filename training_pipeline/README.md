# CardioScope: training pipeline

Trains the four classifiers behind CardioScope (overall CAD, and stenosis of LAD, LCX and RCA) on the Z-Alizadeh Sani dataset, validates them with nested cross-validation, calibrates them, and exports them to ONNX for the Spring Boot back end.

> Research prototype for decision support and education only. Not a medical device and not a substitute for formal diagnostic imaging.

## Results at a glance

Out-of-fold results of the calibrated RandomForests (nested 5-fold CV on all 303 patients) at the chosen thresholds.

| Target | Threshold | Accuracy | Precision | Recall | F1 | ROC-AUC | PR-AUC | Brier |
|---|---|---|---|---|---|---|---|---|
| CAD | 0.47 | 0.878 | 0.894 | 0.940 | 0.916 | **0.914** | 0.961 | 0.100 |
| LAD | 0.40 | 0.792 | 0.774 | 0.910 | 0.836 | **0.848** | 0.883 | 0.154 |
| LCX | 0.34 | 0.667 | 0.549 | 0.840 | 0.664 | **0.741** | 0.586 | 0.203 |
| RCA | 0.27 | 0.587 | 0.473 | 0.860 | 0.611 | **0.697** | 0.554 | 0.210 |

CAD and LAD are reliable. LCX and RCA are weak (Brier close to the always-predict-the-base-rate reference of 0.239 and 0.235), so the app labels them lower-confidence.

## Data

- Source: Extension of the Z-Alizadeh Sani dataset (UCI Machine Learning Repository, also on Kaggle). 303 patients, 59 columns.
- Targets and positive rates: CAD 71.3%, LAD 58.4%, LCX 39.3%, RCA 37.6%.
- Put the raw file in `data/` before running (see Usage).

## Preprocessing (`preprocess.py`)

| Step | Rule |
|---|---|
| Leakage control | `CAD`, `LAD`, `LCX`, `RCA` and `Cath` are dropped from the inputs before every fit |
| Sex | male = 1, female = 0 (the source spells female "Fmale") |
| Yes/No columns | Y = 1, N = 0 |
| VHD | ordinal: N = 0, mild = 1, moderate = 2, severe = 3 |
| BBB (N / LBBB / RBBB) | one-hot, first category dropped |
| Missing values | filled with the column median |
| Scaling | none for tree models; logistic regression scales inside its pipeline, fitted per training fold |
| Features | 56 encoded training features; the deployed vector is the 55 values listed in `models/features.json` |

`preprocess.py` also writes a stratified 80/20 split (242 train, 61 test) for inspection only. Model comparison and final metrics use cross-validation on all 303 rows, because 61 test patients give very noisy estimates.

## Training method

**Stage 1: baselines.** One model per target, fixed settings, seed 42, 5x5 repeated stratified CV.

| Model | Settings |
|---|---|
| Logistic regression | StandardScaler, balanced class weights, max 5000 iterations |
| RandomForest | 400 trees, min 2 samples per leaf, balanced class weights |
| XGBoost | 300 trees, depth 3, learning rate 0.05, subsample 0.8, column sample 0.8, log-loss |

RandomForest led on CAD, LAD and RCA and XGBoost on LCX, but every gap is inside the fold-to-fold spread (AUC standard deviation 0.04 to 0.07). RandomForest was kept for all four targets because it is stable and works directly with SHAP.

**Stage 2: final model.** One RandomForest per target.

- Randomized hyperparameter search, 8 candidates per fit, objective ROC-AUC.
- Search space: trees {200, 300}, max depth {3, 5, 8, None}, min samples per leaf {1, 2, 3, 5}, max features {sqrt, 0.3, 0.5}, class weight {balanced, balanced_subsample, None}.
- Sigmoid calibration (5-fold) so the output behaves like a risk probability. The 3D viewer colours vessels by this probability, not by the thresholded label.
- Per-target decision threshold: scan 0.20 to 0.80 in steps of 0.01 and keep the best F1 on out-of-fold calibrated probabilities.

Hyperparameters selected on the full data:

| Target | Trees | Min leaf | Max features | Max depth | Class weight |
|---|---|---|---|---|---|
| CAD | 200 | 3 | sqrt | None | balanced_subsample |
| LAD | 300 | 1 | 0.3 | 3 | balanced |
| LCX | 300 | 3 | 0.3 | 8 | balanced_subsample |
| RCA | 200 | 3 | sqrt | None | balanced_subsample |

## Validation

Nested cross-validation, 5 outer folds and 3 inner folds. Each outer fold is held out once; the inner search tunes only on the other four folds, the best settings are refit on them, and that model scores the held-out fold. The five held-out predictions are pooled and every reported metric is computed on them, so tuning never sees the rows that score it. Total run time on the training laptop: about 489 s (CAD 134 s, LAD 132 s, LCX 102 s, RCA 120 s).

## Explainability

- Global: SHAP `TreeExplainer` on the final forests (bar and dot summary plots per target). Typical chest pain leads CAD, LAD and RCA; age leads LCX.
- In the app: permutation-sampling Shapley values against real background patients (`models/background.json`), so a patient's contributions add up to their probability minus the background average.
- SHAP shows what the model relies on, not what causes disease.

## Outputs

| File | Used by |
|---|---|
| `models/CAD.onnx`, `LAD.onnx`, `LCX.onnx`, `RCA.onnx` | ONNX Runtime in the Spring Boot back end |
| `models/features.json` | Ordered input feature list (the back end must match this order exactly) |
| `models/background.json` | Background patients for the Shapley explanations |
| Thresholds (0.47 / 0.40 / 0.34 / 0.27) | `ModelService.java` |

## Usage

Script names below are placeholders; rename them to match your repo.

```bash
python -m venv .venv && source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install scikit-learn xgboost shap pandas numpy skl2onnx onnxruntime matplotlib

python preprocess.py        # clean, encode, drop leakage columns, write the 80/20 inspection split
python train.py             # baselines, nested CV, final tuned and calibrated models
python export_onnx.py       # write models/*.onnx, features.json, background.json
python explain.py           # SHAP summary plots
```

Fixed seed 42 for the baselines. Copy the generated `models/` folder into `src/main/resources/models/` for the back end.

## Limitations

- Internal validation only: one centre, 303 patients. No external or temporal validation.
- Fold-to-fold ROC-AUC standard deviation is 0.04 to 0.07, so differences of 0.01 to 0.02 between models are not meaningful.
- Thresholds were picked on the same out-of-fold probabilities used to report F1, so F1 is slightly optimistic. ROC-AUC and PR-AUC are the primary metrics.
- Final hyperparameters were tuned on all rows and then reused when scoring calibration out-of-fold, a small leak into the calibration numbers. The nested raw-model ROC-AUC is the unbiased figure.
- Inputs describe the patient, not the vessel, which likely explains why LCX and RCA are harder than overall CAD.
- SHAP plots are in-sample (final forests fitted on all 303 rows).

## Data licence and citation

Z-Alizadeh Sani dataset, UCI Machine Learning Repository. Cite the original authors when publishing.
