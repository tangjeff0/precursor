(ns pc.models.access-grant
  (:require [pc.datomic :as pcd]
            [pc.datomic.web-peer :as web-peer]
            [clj-time.core :as time]
            [clj-time.coerce]
            [crypto.random]
            [pc.datomic.web-peer :as web-peer]
            [datomic.api :refer [db q] :as d]))

(defn read-api [grant]
  (let [doc-id (:db/id (:access-grant/document-ref grant))
        team-uuid (:team/uuid (:access-grant/team grant))]
    (-> grant
      (select-keys [:access-grant/email
                    :access-grant/expiry
                    :access-grant/grant-date])
      (assoc :db/id (web-peer/client-id grant))
      (cond-> doc-id (assoc :access-grant/document doc-id)
              team-uuid (assoc :access-grant/team team-uuid)))))

(defn find-by-document [db doc]
  (->> (d/q '{:find [?t]
              :in [$ ?doc-id]
              :where [[?t :access-grant/document-ref ?doc-id]]}
            db (:db/id doc))
    (map first)
    (map #(d/entity db %))))

(defn find-by-team [db team]
  (->> (d/q '{:find [?t]
              :in [$ ?team-id]
              :where [[?t :access-grant/team ?team-id]]}
            db (:db/id team))
    (map first)
    (map #(d/entity db %))))

(defn find-by-token [db token]
  (->> (d/q '{:find [?t]
              :in [$ ?token]
              :where [[?t :access-grant/token ?token]]}
            db token)
    ffirst
    (d/entity db)))

(defn find-by-email [db email]
  (->> (d/q '{:find [[?t ...]]
              :in [$ ?email]
              :where [[?t :access-grant/email ?email]]}
            db email)
    (map #(d/entity db %))))

(defn grant-access [doc email granter annotations]
  (let [txid (d/tempid :db.part/tx)
        token (crypto.random/url-part 32)
        grant-date (java.util.Date.)
        expiry (clj-time.coerce/to-date (time/plus (clj-time.coerce/from-date grant-date) (time/weeks 2)))
        temp-id (d/tempid :db.part/user)]
    @(d/transact (pcd/conn)
                 [(assoc annotations :db/id txid)
                  (web-peer/server-frontend-id temp-id (:db/id doc))
                  {:db/id temp-id
                   :access-grant/document-ref (:db/id doc)
                   :access-grant/email email
                   :access-grant/token token
                   :access-grant/expiry expiry
                   :access-grant/grant-date grant-date
                   :access-grant/granter-ref (:db/id granter)
                   :needs-email :email/access-grant-created
                   :access-grant/doc-email (str (:db/id doc) "-" email)}])))

(defn grant-team-access [team email granter annotations]
  (let [txid (d/tempid :db.part/tx)
        token (crypto.random/url-part 32)
        grant-date (java.util.Date.)
        expiry (clj-time.coerce/to-date (time/plus (clj-time.coerce/from-date grant-date) (time/weeks 2)))
        temp-id (d/tempid :db.part/user)]
    @(d/transact (pcd/conn)
                 [(assoc annotations :db/id txid)
                  (web-peer/server-frontend-id temp-id (:db/id team))
                  {:db/id temp-id
                   :access-grant/team (:db/id team)
                   :access-grant/email email
                   :access-grant/token token
                   :access-grant/expiry expiry
                   :access-grant/grant-date grant-date
                   :access-grant/granter-ref (:db/id granter)
                   :needs-email :email/access-grant-created
                   :access-grant/team-email (str (:db/id team) "-" email)}])))
