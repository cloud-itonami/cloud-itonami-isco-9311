(ns mininglabor.store
  "SSoT for the ISCO-08 9311 mining and quarrying labor coordination
  actor (itonami actor pattern, ADR-2607121000 / CLAUDE.md Actors
  section; README's 'Robotics premise' — a labor-scheduling/logistics
  coordination robot performs shift scheduling, task-completion
  logging and safety-equipment procurement coordination for a
  mining/quarrying labor crew under this advisor/governor pair, which
  never dispatches hardware itself, never enters a mine/quarry area,
  and never finalizes a blast-authorization or mine-entry/
  area-clearance decision — those remain a site safety officer's
  exclusive judgment). Modeled on cloud-itonami-isco-3313's
  accountingsupport.store.

  Domain:

    laborer — a registered mining/quarrying labor-crew member
              (:laborer-id, :name)
    site    — a registered mine/quarry site {:site-id :name
              :max-supply-cost number}. `:max-supply-cost` is an
              informational registered ceiling used only to decide
              whether a `:coordinate-supply-order` proposal escalates
              to human sign-off (the governor never blocks a
              within-threshold order outright; it only decides
              commit vs. escalate).
    record  — a committed operating record (a logged shift/task, a
              scheduled operation, a flagged safety concern, or a
              coordinated supply order) — written ONLY via
              commit-record!.
    ledger  — append-only audit trail, commit or hold.")

(defprotocol Store
  (laborer [s laborer-id])
  (site [s site-id])
  (records-of [s laborer-id])
  (ledger [s])
  (register-laborer! [s laborer])
  (register-site! [s site])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (laborer [_ laborer-id] (get-in @a [:laborers laborer-id]))
  (site [_ site-id] (get-in @a [:sites site-id]))
  (records-of [_ laborer-id] (filter #(= laborer-id (:laborer-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-laborer! [s l]
    (swap! a assoc-in [:laborers (:laborer-id l)] l) s)
  (register-site! [s st]
    (swap! a assoc-in [:sites (:site-id st)] st) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:laborers {} :sites {} :records [] :ledger []}
                                    seed)))))
