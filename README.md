# cloud-itonami-isco-9311

Open Occupation Blueprint for **ISCO-08 9311**: Mining and Quarrying Labourers.

This repository designs a forkable OSS business for a mining/quarrying labor-scheduling and logistics coordination practice: a labor-crew scheduling and supply-coordination robot manages shift/task records under a governor-gated actor, so a crew keeps its own operating records instead of renting a closed workforce-management SaaS.

**Maturity: `:implemented`.** `src/mininglabor/` implements the
`MiningLaborActor` as a `langgraph.graph/state-graph`
(`mininglabor.actor`) wired to a `Mining Labor Advisor`
(`mininglabor.advisor`) and an independent `MiningLaborGovernor`
(`mininglabor.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 17 tests green (`clojure -M:test`).
HARD invariants (always hold, never overridable): laborer provenance,
site provenance, no-actuation (`:effect` must be `:propose`), a closed
op-allowlist (`:log-labor-record`, `:schedule-labor-operation`,
`:flag-safety-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any
proposal that would directly finalize a blast-authorization decision,
a mine-entry/area-clearance decision, or override a site safety
officer's judgment. Always-escalate paths (human sign-off regardless
of confidence, mapping this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always) and `:coordinate-supply-order` above
the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a labor-scheduling/logistics coordination robot performs shift scheduling, task-completion logging and safety-equipment supply-order coordination for a mining/quarrying labor crew, under an actor that proposes actions and an independent **Mining Labor Governor** that gates them. The governor never
dispatches hardware itself, never enters a mine/quarry area, and never finalizes a blast-authorization or mine-entry/area-clearance decision; `:high`/`:safety-critical` actions (such as a flagged cave-in-risk/gas-exposure/equipment-hazard concern, or an above-threshold supply order) require human sign-off. **This actor coordinates labor scheduling/logistics only — it never performs mining/quarrying work or authorizes site operations itself.**

## Core Contract

```text
crew roster + site registration + safety-reporting policy
        |
        v
Mining Labor Advisor -> Mining Labor Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, finalize
a blast-authorization or mine-entry/area-clearance decision, override a site
safety officer's judgment, suppress an operating record, or disclose
sensitive data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `9311`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
