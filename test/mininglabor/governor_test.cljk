(ns mininglabor.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [mininglabor.store :as store]
            [mininglabor.advisor :as advisor]
            [mininglabor.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-laborer! st {:laborer-id "laborer-1" :name "Kobo Yamada"})
    (store/register-site! st {:site-id "S-1" :name "Kobo Quarry" :max-supply-cost 2000})
    st))

(defn- op [op-kw & {:as extra}]
  (merge {:op op-kw :effect :propose :site-id "S-1"
          :confidence 0.9 :stake :low}
         extra))

(def ^:private req {:laborer-id "laborer-1"})

(deftest ok-log-labor-record
  (let [st (fresh-store)
        v (governor/check req {} (op :log-labor-record) st)]
    (is (:ok? v))))

(deftest ok-schedule-labor-operation
  (let [st (fresh-store)
        v (governor/check req {} (op :schedule-labor-operation) st)]
    (is (:ok? v))))

(deftest ok-supply-order-at-threshold-boundary
  (testing "the supply-cost threshold escalate boundary is exclusive (over, not at)"
    (let [st (fresh-store)
          v (governor/check req {} (op :coordinate-supply-order :cost 2000) st)]
      (is (:ok? v)))))

(deftest hard-on-unregistered-laborer
  (let [st (fresh-store)
        v (governor/check {:laborer-id "nobody"} {} (op :log-labor-record) st)]
    (is (:hard? v))
    (is (some #(= :no-laborer (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-site
  (let [st (fresh-store)
        v (governor/check req {} (op :log-labor-record :site-id "S-ghost") st)]
    (is (:hard? v))
    (is (some #(= :no-site (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-labor-record) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-op-outside-closed-allowlist
  (let [st (fresh-store)
        v (governor/check req {} (op :dispatch-equipment) st)]
    (is (:hard? v))
    (is (some #(= :unknown-op (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-op-authorize-blast
  (testing "finalizing a blast-authorization decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :authorize-blast) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-clear-area-for-mine-entry
  (testing "clearing an area for mine entry is a site safety officer's exclusive judgment, never this actor's"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :clear-area-for-mine-entry) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-even-with-allowlisted-op
  (testing "defense-in-depth: a rationale that itself attempts to authorize the blast is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :schedule-labor-operation)
                                           :rationale "recommend we authorize the blast now")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest always-escalates-safety-concern-even-at-high-confidence
  (testing "a cave-in-risk/gas-exposure/equipment-hazard concern always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :flag-safety-concern :hazard-type :cave-in-risk)
                                           :confidence 0.99)
                             st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-threshold
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :coordinate-supply-order :cost 5000) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-labor-record) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip-on-scope-exclusion
  (testing "the governor's scope-exclusion term list must never match the mock advisor's own default rationale text for any allowlisted op — CLAUDE.md's known self-tripping bug pattern (rationale legitimately contains bare nouns like 'entry'/'blast'/'officer', but never the full finalization-action phrases)"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          ops [:log-labor-record :schedule-labor-operation
               :flag-safety-concern :coordinate-supply-order]]
      (doseq [o ops]
        (let [request {:laborer-id "laborer-1" :op o :site-id "S-1"
                        :stake :low :task "routine site task"
                        :hazard-type :cave-in-risk :cost 500}
              proposal (advisor/-advise adv st request)
              v (governor/check request {} proposal st)]
          (is (not (:hard? v))
              (str o " proposal unexpectedly hard-blocked: " (:violations v))))))))
