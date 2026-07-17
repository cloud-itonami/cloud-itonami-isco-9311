(ns mininglabor.governor
  "MiningLaborGovernor — the independent safety/scope layer gating
  every labor-scheduling/logistics proposal an advisor may make for a
  mining/quarrying crew. The governor never dispatches hardware
  itself, never enters a mine/quarry area, and never finalizes a
  blast-authorization or mine-entry/area-clearance decision — those
  are permanently out of this actor's scope and remain a site safety
  officer's exclusive judgment (README's 'Robotics premise': this
  actor coordinates LABOR SCHEDULING/LOGISTICS ONLY — it never
  performs mining/quarrying work or authorizes site operations
  itself). Modeled on cloud-itonami-isco-3313's
  accountingsupport.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. laborer provenance    — the crew member must be independently
                                verified/registered before any action.
    2. site provenance       — the mine/quarry site must be
                                independently verified/registered
                                before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never operates the site itself; it
                                only gates what the advisor may
                                coordinate).
    4. closed op-allowlist    — only :log-labor-record,
                                :schedule-labor-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                blast-authorization decision, a
                                mine-entry/area-clearance decision, or
                                to override a site safety officer's
                                judgment is a hard, permanent block
                                (checked both against the proposed
                                :op and, defense-in-depth, against the
                                proposal's :rationale text — matched
                                as full finalization/execution ACTION
                                phrases such as \"authorize the
                                blast\" / \"clear the area for mine
                                entry\", never as bare nouns like
                                \"blast\" or \"entry\", so the check
                                can never self-trip on the advisor's
                                own routine rationale text, e.g.
                                \"logged shift entry for laborer …\"
                                or \"scheduled labor crew for
                                blast-adjacent task …\" — both contain
                                the bare nouns but neither is a
                                finalization action, and both are
                                exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a cave-in-risk / gas-exposure /
                                equipment-hazard concern always
                                escalates to a human, never
                                auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`)."
  (:require [clojure.string :as str]
            [mininglabor.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-labor-record :schedule-labor-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:authorize-blast :finalize-blast-authorization
    :clear-area-for-mine-entry :authorize-mine-entry
    :override-site-safety-officer})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("blast", "entry", "clear", "officer") — so this can never match
;; inside the mock advisor's own default rationale text (which
;; legitimately contains those bare nouns, e.g. "shift entry" /
;; "blast-adjacent task" / "site safety officer review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["authorize the blast" "finalize blast authorization"
   "clear the area for mine entry" "authorize mine entry"
   "override the site safety officer" "override site safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower-case (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal laborer-record site-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? laborer-record)
      (conj {:rule :no-laborer
             :detail "未登録 laborer への提案は不可（laborer record は独立して検証・登録済みでなければならない）"})

      (nil? site-record)
      (conj {:rule :no-site
             :detail "未登録 site への提案は不可（site record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は現場作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "blast-authorization の確定・mine-entry/area-clearance 判断の直接実行・site safety officer の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `mininglabor.store/Store`. Pure — never mutates
  the store, never dispatches a site operation."
  [request _context proposal store]
  (let [laborer-record (store/laborer store (:laborer-id request))
        site-record (some->> (:site-id proposal) (store/site store))
        hard (hard-violations proposal laborer-record site-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
