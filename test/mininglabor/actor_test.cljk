(ns mininglabor.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [mininglabor.actor :as actor]
            [mininglabor.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-laborer! st {:laborer-id "laborer-1" :name "Kobo Yamada"})
    (store/register-site! st {:site-id "S-1" :name "Kobo Quarry" :max-supply-cost 2000})
    st))

(deftest commits-a-registered-labor-log
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:laborer-id "laborer-1" :op :log-labor-record :stake :low
                  :site-id "S-1" :task "shift-completion log"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "laborer-1"))))))

(deftest holds-an-unregistered-site-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:laborer-id "laborer-1" :op :log-labor-record :stake :low
                  :site-id "S-ghost" :task "shift-completion log"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "laborer-1")))))

(deftest interrupts-then-approves-safety-concern-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:laborer-id "laborer-1" :op :flag-safety-concern :stake :low
                  :site-id "S-1" :hazard-type :cave-in-risk}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "laborer-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "laborer-1")))))))
