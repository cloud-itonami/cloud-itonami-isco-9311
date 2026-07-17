(ns mininglabor.advisor
  "Mining Labor Advisor — proposing a labor-scheduling/logistics
  coordination operation (log a labor record, schedule a labor
  operation, flag a safety concern, coordinate a supply order) from a
  crew roster, site registration and safety-reporting policy.
  Swappable mock/llm; the advisor ONLY proposes —
  `mininglabor.governor` independently gates every proposal and always
  escalates safety concerns and above-threshold supply orders. The
  advisor never proposes to finalize a blast-authorization or
  mine-entry/area-clearance decision, or to override a site safety
  officer's judgment — those stay permanently out of this actor's
  scope. Modeled on cloud-itonami-isco-3313's advisor.

  A proposal: {:op :log-labor-record|:schedule-labor-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :laborer-id str :site-id str
               :cost number :hazard-type kw :task str :stake kw
               :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- rationale-for [op laborer-id site-id hazard-type]
  (case op
    :log-labor-record
    (str "logged shift entry for laborer " laborer-id " at site " site-id)

    :schedule-labor-operation
    (str "scheduled labor crew for blast-adjacent task at site " site-id)

    :flag-safety-concern
    (str "flagged " (name (or hazard-type :hazard)) " concern for laborer "
         laborer-id " at site " site-id " — routed for site safety officer review")

    :coordinate-supply-order
    (str "coordinated supply order for laborer " laborer-id " at site " site-id)

    (str "proposed " (name op) " for laborer " laborer-id " at site " site-id)))

(defn- infer [_store {:keys [op stake laborer-id site-id cost hazard-type task]
                       :as request}]
  {:op op
   :effect :propose
   :laborer-id laborer-id
   :site-id site-id
   :cost cost
   :hazard-type hazard-type
   :task task
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (rationale-for op laborer-id site-id hazard-type)})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a mining and quarrying labor-scheduling/logistics
   coordination advisor. Given a request, propose an :op (one of
   :log-labor-record, :schedule-labor-operation, :flag-safety-concern,
   :coordinate-supply-order), the :laborer-id, :site-id, and any
   :cost/:hazard-type/:task fields, an honest :confidence and a
   :stake. Never propose an op outside this closed list, and never
   propose to finalize a blast-authorization decision, a mine-entry or
   area-clearance decision, or to override a site safety officer's
   judgment — those are always out of this actor's scope; it
   coordinates labor scheduling/logistics only and never performs
   mining/quarrying work or authorizes site operations itself. Safety
   concerns always require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
