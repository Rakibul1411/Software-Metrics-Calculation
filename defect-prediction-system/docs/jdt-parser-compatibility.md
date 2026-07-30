# JDT parser compatibility

## Runtime and target language are separate

The Spring Boot extractor runs on JDK 17 and uses Eclipse JDT 3.37.0. A target
repository is not automatically treated as Java 17. Every source module gets a
separate `source`, `compliance`, `target`, and encoding configuration.

Resolution priority:

1. nearest `.settings/org.eclipse.jdt.core.prefs`;
2. effective Maven compiler properties/plugin configuration;
3. Ant `build.xml`, `common-build.xml`, and property files;
4. selected AEEEM benchmark fallback;
5. oldest candidate with the fewest syntax errors.

Syntax fallback samples files across the complete source set instead of only
the first directory. Equal error counts retain the older candidate.

## AEEEM defaults

| Profile | Release | Default scope | Source | Compliance | Target |
|---|---|---|---:|---:|---:|
| JDT | `R3_4` | `org.eclipse.jdt.core` | 1.3 | 1.4 | 1.2 |
| PDE | `R3_4_1` | `ui/org.eclipse.pde.ui` | 1.3 | 1.4 | 1.2 |
| EQ | `R3_4` | `bundles/org.eclipse.osgi` | 1.3 | 1.4 | 1.2 |
| ML | `R_3_1_0` | repository/profile scope | 1.5 | 1.5 | 1.5 |
| LC | `releases/lucene/2.4.0` | repository/profile scope | 1.4 | 1.4 | 1.4 |

Repository metadata overrides these fallbacks when it is available. Benchmark
profiles export top-level dataset entities; current-project mode may also
export named member types. These release names are preferred historical refs,
not unconditional requirements: a missing migrated ref is resolved from the
fixed release date. EQ must use `eclipse-equinox/equinox.framework`, because
the similarly named `eclipse-equinox/equinox` repository does not contain the
historical framework module.

## PROMISE release handling

One extraction request represents one release, such as `camel-1.0` or
`lucene-2.4`. Release numbers are project versions, never Java versions.

The supplied collection contains 38 releases. It must be unpacked first and
each inner release archive uploaded separately. The backend rejects a
multi-release collection rather than parsing the one already-expanded folder
and silently ignoring the remaining nested archives.

Historical dataset scopes are applied where the distribution includes extra
modules:

| Family | Measured source scope |
|---|---|
| Lucene | `src/java` |
| POI | `src/java` |
| Synapse | `modules/core/src/main/java` |
| Velocity | `src/java` |
| Log4j | `src/java`, excluding test packages |
| Camel | production `src/main` modules |
| jEdit | product tree excluding installer and bundled plugins |
| Ant | release-aware core/product filtering |

## Validation performed

- Main code compiled successfully with Eclipse compiler 3.37 at Java 17.
- 44 backend tests passed, including Maven, Ant, Eclipse prefs, multi-module
  scope, old-source fallback, AEEEM benchmark entity filtering, and nested
  collection rejection.
- All 38 supplied PROMISE release trees produced a non-empty JDT AST/metric
  result.
- Corrected core-scope results include Lucene 2.0/2.2/2.4
  (`194/246/339` classes), POI 1.5/2.0/2.5/3.0
  (`237/314/385/442`), and Synapse 1.0/1.1/1.2
  (`157/222/256`).

## Reproducibility boundary

Successful AST construction does not imply byte-for-byte equality with every
published PROMISE or AEEEM metric row. Exact equality also depends on the
original class manifest, dependency binaries, compiler output, metric-tool
formula, generated-source policy, and historical SCM conversion. Missing
optional dependencies are handled with JDT binding recovery and capped
diagnostics; syntactically declared top-level PROMISE types are no longer
dropped only because a binding is unresolved.
