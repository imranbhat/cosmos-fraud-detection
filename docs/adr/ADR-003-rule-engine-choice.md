# ADR-003: Custom Strategy Pattern Rule Engine Over Drools

**Status:** Accepted
**Date:** 2024-04-01
**Deciders:** Fraud Engineering, Platform Engineering

---

## Context

The Scoring Service requires a rule engine to evaluate deterministic fraud signals before (and as a fallback to) the ML model. Rules are expected to include checks such as:

- Velocity limits (e.g., >5 transactions in 1 hour)
- High-amount thresholds relative to the card's historical average
- New device + high-amount combination
- Geographic anomaly (country change within N hours)
- Merchant category blacklist / whitelist
- Known fraudulent BIN ranges

The rule set is expected to be relatively small (~15–25 rules at launch, growing to ~50 over 12 months). Rules need to be:

- **Testable** — individually unit-tested with deterministic inputs.
- **Auditable** — every triggered rule must be logged in the decision output.
- **Hot-reloadable** — rule parameter changes (e.g., velocity thresholds) should not require a full service redeployment.
- **Low-latency** — rule evaluation must complete in <5ms to fit within the scoring latency budget.

The primary candidate considered was **Drools** (now Red Hat Decision Manager / Kogito), a mature Java-based rules management system.

---

## Decision

Implement a **custom rule engine using the Strategy pattern** in Java, rather than adopting Drools or another external rules framework.

---

## Design

Each rule is a self-contained class implementing a common interface:

```java
public interface FraudRule {
    String ruleId();
    RuleResult evaluate(ScoredTransaction tx, FeatureVector features);
}
```

Rules are registered in a `RuleRegistry` and executed sequentially (with short-circuit options for CRITICAL outcomes). The engine collects triggered rules and aggregates a composite rule score:

```java
public class RuleEngine {
    private final List<FraudRule> rules;

    public RuleEngineResult evaluate(ScoredTransaction tx, FeatureVector features) {
        List<String> triggered = new ArrayList<>();
        float maxScore = 0f;

        for (FraudRule rule : rules) {
            RuleResult result = rule.evaluate(tx, features);
            if (result.triggered()) {
                triggered.add(rule.ruleId());
                maxScore = Math.max(maxScore, result.score());
            }
            if (result.isTerminal()) break; // short-circuit for DECLINE/CRITICAL
        }

        return new RuleEngineResult(triggered, maxScore);
    }
}
```

Rule parameters (thresholds, limits) are loaded from a configuration service at startup and refreshed every 60 seconds, enabling hot-reload without redeployment.

---

## Rationale

### 1. Simpler Than Required

Drools is a full production rules management system designed for hundreds to thousands of rules, complex rule interdependencies, truth maintenance, and forward/backward chaining. Our use case involves:

- ~20 independent, stateless rules at launch.
- No rule interdependencies or inference chains.
- No non-developer stakeholders needing a GUI or DSL to author rules.

Drools's power introduces substantial overhead: a separate Kie container lifecycle, DRL file parsing, working memory management, and a steeper debugging surface. For 20 rules, this overhead is all cost and no benefit.

### 2. No DSL Overhead

Drools requires rules to be written in DRL (Drools Rule Language) or the newer spreadsheet/DMN formats. This means:

- Fraud engineers need to learn and maintain a non-standard DSL.
- Rules cannot be debugged with standard Java tooling (breakpoints, unit test frameworks without Drools harness).
- IDE support for DRL is limited compared to Java.

The Strategy pattern keeps rules as plain Java — debuggable, refactorable, and covered by standard code review processes.

### 3. Testability

Each `FraudRule` implementation is a pure function of inputs. Unit tests are straightforward:

```java
@Test
void velocityRule_triggersWhenTxCountExceedsThreshold() {
    FeatureVector features = FeatureVector.builder()
        .txCount1h(6)
        .build();
    RuleResult result = new VelocityRule(threshold = 5).evaluate(tx, features);
    assertTrue(result.triggered());
    assertEquals("RULE_VELOCITY_1H", result.ruleId());
}
```

Testing Drools rules requires spinning up a Kie session, loading DRL files, and asserting on working memory — significantly more test infrastructure.

### 4. Sufficient for ~20 Rules

The engineering cost of maintaining a custom rule engine at this scale is low. The `RuleEngine` class is ~80 lines. Adding a new rule is adding a new class and registering it. There is no runtime compilation, no classpath scanning, no DRL parsing.

For a set of rules that fits comfortably in a single review session, a custom implementation is not a liability — it is the lowest-complexity correct solution.

### 5. Hot-Reload Without DSL

Dynamic rule parameter updates are handled via a `RuleParameterConfig` object refreshed from a configuration service (e.g., Consul, environment variables backed by Kubernetes ConfigMap). This is simpler and more auditable than Drools's KieScanner or dynamic DRL reloading.

---

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Drools / Red Hat Decision Manager | Excessive complexity for ~20 rules; DRL DSL overhead; JVM memory footprint; difficult to unit test without harness |
| OpenRules | Commercial; less community support; same DSL complexity issues |
| Easy Rules (Java) | Reasonable, but adds a dependency for marginal benefit over a ~80-line custom implementation |
| Graal.js / Nashorn scripted rules | Dynamic rule authoring is a future requirement, not current; adds security and performance concerns |

---

## Consequences

**Positive:**
- Zero external dependencies for rule evaluation — no Kie container, no DRL parser.
- Rules are plain Java: fully debuggable, unit-testable, reviewable in standard code review.
- Rule evaluation is deterministic and fast (<1ms for 20 rules in benchmarks).
- Adding, removing, or modifying a rule is a standard code change with full test coverage.

**Negative / Risks:**
- Non-technical stakeholders (e.g., fraud analysts) cannot author rules without developer involvement. This is acceptable now; if self-service rule authoring becomes a requirement, a migration to a DSL-based engine may be warranted.
- As the rule count grows beyond ~100, the Strategy pattern approach may benefit from a more structured orchestration mechanism (e.g., rule groups, priority ordering, conflict resolution). This is a future scaling concern, not a current one.

---

## Review Trigger

Revisit this decision if:
- The rule set grows beyond ~100 rules with complex interdependencies.
- A requirement emerges for non-engineer stakeholders to author or modify rules via a UI.
- Rule authoring frequency increases to the point where dev cycle time for rule changes becomes a bottleneck.
