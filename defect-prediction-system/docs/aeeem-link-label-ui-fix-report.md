# AEEEM link, label, and prediction-warning fix

## Why only JDT worked

The previous implementation required one hard-coded release ref for every
AEEEM profile. JDT's current GitHub mirror still exposes `R3_4`, so it worked.
The current PDE and Lucene mirrors do not expose the configured legacy refs,
even though their default branches contain the required release-date history.
The previous resolver stopped instead of using that history.

Equinox had a separate input problem. `eclipse-equinox/equinox` is not the
historical framework repository and its 2008 tree does not contain
`bundles/org.eclipse.osgi`. The correct repository is
`eclipse-equinox/equinox.framework`.

Mylyn exposes `R_3_1_0`, but the GitHub mirror begins on 2005-06-17 while the
paper window begins on 2005-01-17. The old implementation treated this migrated
history gap as fatal.

## Updated verified links

| Profile | Repository | Resolution observed |
|---|---|---|
| JDT | `https://github.com/eclipse-jdt/eclipse.jdt.core` | exact `R3_4` |
| PDE | `https://github.com/eclipse-pde/eclipse.pde` | 2008-09-11 date fallback |
| EQ | `https://github.com/eclipse-equinox/equinox.framework` | exact `R3_4` |
| ML | `https://github.com/eclipse-mylyn/org.eclipse.mylyn` | exact `R_3_1_0`; history begins 2005-06-17 |
| LC | `https://github.com/apache/lucene` | 2008-10-08 date fallback |

The UI now fills these URLs automatically. The backend also rejects a
benchmark/profile mismatch before cloning.

## Release-resolution rule

1. Use the configured historical tag/ref when it exists and is not after the
   fixed release date.
2. Otherwise use the final first-parent commit on or before the fixed release
   date.
3. If the correct mirror begins after the requested history start, use every
   available commit and return a visible partial-coverage note.
4. Fail only when the correct repository has no release-date commit or the
   required final module is absent.

Live resolver results:

| Profile | Snapshots | Effective start | Final date | Result |
|---|---:|---|---|---|
| JDT | 91 | 2005-01-01 | 2008-06-17 | exact ref |
| PDE | 97 | 2005-01-01 | 2008-09-11 | date fallback |
| EQ | 91 | 2005-01-01 | 2008-06-25 | exact ref |
| ML | 98 | 2005-06-17 | 2009-03-17 | exact ref + coverage note |
| LC | 99 | 2005-01-01 | 2008-10-08 | date fallback |

## Missing label behaviour

When the uploaded source contains `class` but the Label column field says
`bug`, the UI reads the CSV/ARFF header and changes the field to `class`
automatically when all uploaded datasets agree. Manual label changes are
validated immediately and clear stale server errors.

If the backend still receives a mismatch, it now returns an actionable message:

> Source dataset 'PDE.arff' does not contain label column 'bug'. Detected
> 'class' as the label column. Set Label column to 'class' and try again.

It no longer fills the UI with every available metric column.

## Meaning of the amber prediction text

The amber text is a validation limitation, not a prediction failure. It appears
when only one labelled source project is uploaded. In that case K or C and the
decision threshold are tuned using repeated stratified validation inside the
same source. The model can predict the target, but this does not measure
cross-project performance.

For stronger evidence, upload at least two labelled source projects so
leave-one-source-project-out validation can be used, or switch to **Evaluate**
and provide a labelled target.

## Verification

- Live AEEEM repository/release resolver: 5/5 profiles passed.
- Java 17 focused tests: 47/47 passed.
- Python prediction/CORAL tests: 38/38 passed.
- Angular production build: passed.
