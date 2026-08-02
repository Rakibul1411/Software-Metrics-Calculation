# User guide

## Dashboard

After signing in, the dashboard shows total datasets, manual datasets, labeled
benchmarks, comparison runs, recent activity, and direct links to Analyze and
Predictions.

## Analyze

Choose **Source code archive** for a PROMISE release or **GitHub repository** for
PROMISE/AEEEM. Supply the release version. Project name is inferred when blank.

For an AEEEM benchmark, select its profile. The UI fills the canonical name,
version, and recommended repository. Analysis can take longer because full Git
history is mined.

Successful analysis creates:

- a metric CSV in backend storage;
- one `MANUAL`, unlabeled row in `metric_datasets`.

It does not create bug labels.

## Metric storage

Use family/origin/search filters to find data. Each card shows project/version,
row and feature counts, ownership, origin, and label status.

Preview and download are available without exposing internal paths. User-owned
data can be deleted only before it is referenced by a saved comparison.
System predefined datasets cannot be deleted.

## Predictions

Select three compatible datasets:

- **Source**: any labeled dataset of the same family.
- **Manual target**: source-code calculated dataset for the project/version.
- **Predefined target**: matching labeled benchmark.

The predefined dropdown narrows automatically after choosing a manual target.

Choose K from 1 to 5 and whether to apply dataset alignment, then adjust the
decision threshold if needed. Log1p runs automatically; shallow CORAL runs only
when the alignment checkbox is checked.

Running again with another alignment or threshold setting is safe: the old
result and original target stay unchanged.

## Ranked results

Open any run to view:

- class identifier;
- defect probability/score;
- predicted label;
- actual label when present;
- risk rank and band.

Switch between manual and predefined predictions, filter predicted buggy rows,
change the row limit, or download the complete labeled CSV for a manual target.

## Reports

Reports combine model settings, dataset identities, evaluation, comparison,
warnings, and top-risk manual classes. Prediction and metric-comparison reports
are generated as PDFs and downloaded through authenticated backend routes.

For a dual-target execution, the two saved target runs share one comparison
group ID and appear together in the grouped report view. The manual run also
provides a labeled prediction CSV; the predefined run provides evaluation
against its actual labels.

## Common problems

### No matching predefined target

Manual and predefined project name, version, and family must match. For AEEEM,
use the benchmark profile instead of typing those values manually.

### AEEEM archive rejected

This is expected. Use GitHub because history features cannot be reconstructed
from a source-only ZIP.

### Source rejected

The source must contain a recognized label column and valid PROMISE/AEEEM
features. Open Metric storage quality details or upload a labeled benchmark.

### ML service unavailable

Start FastAPI on port 8000 and verify `ML_SERVICE_TOKEN` matches Spring Boot.

### Database connection failure

Check `DEFECTLAB_DB_URL`, user/password, Neon SSL settings, and IP/network
permissions. Do not paste production secrets into source files.
