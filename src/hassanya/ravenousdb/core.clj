(ns hassanya.ravenousdb.core
  (:import (net.ravendb.client.documents DocumentStore)
           (net.ravendb.client.documents.conventions DocumentConventions)
           (java.util Map))
  (:require [hassanya.ravenousdb.utils :refer [java->clj clj->java]]
            [piotr-yuxuan.closeable-map :refer [closeable-map] :as cm]
            [clj-uuid :as uuid]))

(def ^:private document-stores
  "Atom map containing all initialized document stores, client id as key and DocumentStore as val"
  (atom {}))

(defn- gen-conventions-proxy
  "Generate a proxy for DocumentConventions with a custom implementation for fetching collection name via @metadata, Prevents DocumentConventions cloning without a proxy"
  []
  (proxy [DocumentConventions] []
    (getCollectionName [doc]
      (let [collection (-> doc
                           (get "@metadata" {})
                           (get "@collection" nil))]
        (if (nil? collection)
          (-> doc .getClass .getSimpleName)
          collection)))
    (clone [] (gen-conventions-proxy))))

(defn new-client
  "Generates a ravendb client using `db` and `nodes`. `db` is the name of the database whilst `nodes` is a coll of strings with the ravendb nodes' base URL(s).
  Alternatively, `nodes` accepts a string rather than a coll"
  ([db nodes]
   (closeable-map
    {:id (str uuid/v1)
     :nodes (into-array String (if (coll? nodes) nodes [nodes]))
     :db db
     :document-session nil})))

(defn- get-store!
  "Get DocumentStore instance from an atom, based on client ID"
  [{:keys [id]}]
  (get @document-stores id nil))

(defn- new-store!
  "Initialize a new Document Store for client if no DocumentStore has not been created/initialized yet"
  [{:keys [id nodes db] :as client}]
  (let [atomized-store (get-store! id)]
    (when (nil? atomized-store)
      (let [store (new DocumentStore nodes db)]
        (.setConventions store (gen-conventions-proxy))
        (.initialize store)
        (swap! document-stores assoc id store)))
    client))

(defn new-session!
  "Takes a `client` map generated from `new-client` and opens a ravendb session"
  [client]
  (let [client-with-store (if (nil? (get-store! client))
                            (new-store! client)
                            client)
        document-store (get-store! client-with-store)]
    (assoc client-with-store :document-session (.openSession document-store))))

(defn load-doc!
  "Takes a `client` map, generated form `new-client`. `id` is the document id that needs to be fetched.
  Optionally `includes` is a vector of strings to fetch specific relationships of document within a single roundtrip"
  ([client id] (load-doc! client id []))
  ([client id includes]
   (let [sesh (reduce #(.include %1 %2)
                      (:document-session client)
                      includes)
         result (.load sesh Map id)]
     (java->clj result))))

(defn add-doc!
  "Takes a `client` map, generated form `new-client`. `doc` is a map which needs to be inserted into a collection (`coll-name`).
  `save-changes?` is bool (defaults to true) which can be optionally used to indicate whether doc should be inserted or wait for `save-changes` signal"
  ([client doc coll-name] (add-doc! client doc coll-name (str coll-name "/" (uuid/v1)) true))
  ([client doc coll-name save-changes?] (add-doc! client doc coll-name (str coll-name "/" (uuid/v1)) save-changes?))
  ([{:keys [document-session]} doc coll-name id save-changes?]
   (.store document-session
           (-> doc
               (assoc "@metadata" {"@collection" coll-name})
               clj->java)
           id)
   (when save-changes? (.saveChanges document-session))
   (with-meta doc {:collection coll-name
                   :id id})))

(defn patch-doc!
  "Takes a `client` map, generated form `new-client`. `id` is the ID of the targeted document to be patched. `changes-map` is a map of changes that need to be updated in the document.
  `save-changes?` is bool (defaults to true) which can be optionally used to indicate whether action should be taken now or wait for a signal"
  ([client id changes-map] (patch-doc! client id changes-map true))
  ([client id changes-map save-changes?]
   (let [sesh (:document-session client)]
     (doseq [kv changes-map]
       (let [key (-> (first kv) name)
             val (second kv)]
         (-> sesh
             .advanced
             (.patch id key (if (coll? val)
                              (-> val
                                  clj->java)
                              val)))))
     (when save-changes? (.saveChanges sesh))
     changes-map)))

(defn delete-doc!
  "Takes a `client` map, generated form `new-client`. `id` is the Id of the targeted document to be deleted
  `save-changes?` is bool (defaults to true) which can be optionally used to indicate whether action should be taken now or wait for a signal"
  ([client id] (delete-doc! client id true))
  ([{:keys [document-session]} id save-changes?]
   (.delete document-session id)
   (when save-changes? (.saveChanges document-session))
   id))

(defn save-changes!
  "Saves unsaved changes in the session. Accepts a map with a `document-session`"
  [{:keys [document-session]}] (.saveChanges document-session))

(defn query
  "Generates a query map"
  ([coll-name] {:collection coll-name
                :ops []}))

(defn- conj-ops [{:keys [ops] :as q} kind args]
  (assoc q :ops (conj ops {:kind kind
                           :args args})))

(defn where-equal
  "Takes a `query` and adds a where equal clause to it using `field` and `val`"
  [query field val] (conj-ops query :where-eq [field val]))

(defn where-not-equal
  "Takes a `query` and adds a where not equal clause to it using `field` and `val`"
  [query field val] (conj-ops query :where-neq [field val]))

(defn where-between
  "Takes a `query` and adds a where between clause to it, on `field`, value must start from `start` until `end`"
  [query field start end] (conj-ops query :where-between [field start end]))

(defn where-in
  "Takes a `query` and adds a where between clause to it, on `field`, value must exist in `vect`"
  [query field vect] (conj-ops query :where-in [field vect]))

(defn unless
  "Apply the inverse of upcoming query clause"
  [query] (conj-ops query :not []))

(defn where-not-in
  "Takes a `query` and adds a where between clause to it, on `field`, value must not exist in `vect`"
  [query field vect]
  (assoc query :ops (conj
                     (:ops (unless query))
                     {:kind :where-in
                      :args [field vect]})))

(defn where-starts-with
  "Takes a `query` and adds a where clause to it using `field` and `val`. The value of the field must start with `val`"
  [query field val] (conj-ops query :where-starts-with [field val]))

(defn where-ends-with
  "Takes a `query` and adds a where clause to it using `field` and `val`. The value of the field must start with `val`"
  [query field val] (conj-ops query :where-ends-with [field val]))

(defn where-eq-or-greater
  "Takes a `query` and adds a where clause to it using `field` and `val`. The value of the field must equal or greater than `val`"
  [query field val] (conj-ops query :where-eq-or-greater [field val]))

(defn where-greater
  "Takes a `query` and adds a where clause to it using `field` and `val`. The value of the field must greater than `val`"
  [query field val] (conj-ops query :where-greater [field val]))

(defn where-less
  "Takes a `query` and adds a where clause to it using `field` and `val`. The value of the field must be less than `val`"
  [query field val] (conj-ops query :where-less [field val]))

(defn where-eq-or-less
  "Takes a `query` and adds a where clause to it using `field` and `val`. The value of the field must equal or less than `val`"
  [query field val] (conj-ops query :where-eq-or-less [field val]))

(defn limit
  "Takes a `query` and adds a `limit` on the number of items in the query results"
  [query limit] (conj-ops query :take [limit]))

(defn skip
  "Takes a `query` and skips the first `x` items of the query results"
  [query x] (conj-ops query :skip [x]))

(defn order-by
  "Takes a `query` and orders the results by `field` in asc or desc order. by default ordering is asc (set `desc?` to true to switch to desc)"
  ([query field] (order-by query field false))
  ([query field desc?] (conj-ops query :order-by [field desc?])))

(defn order-by-asc
  "Takes a `query` and orders the results by `field` in ascending order"
  ([query field] (order-by query field false)))

(defn order-by-desc
  "Takes a `query` and orders the results by `field` in decending order"
  ([query field] (order-by query field true)))

(defn- gen-document-query
  ([{:keys [collection ops]} {:keys [document-session]}]
   (let [document-query (.documentQuery document-session Map nil collection false)]
     (doseq [{:keys [kind args]} ops]
       (cond
         (= kind :not) (.not document-query)
         (= kind :skip) (.skip document-query (first args))
         (= kind :take) (.take document-query (first args))
         (= kind :order-by) (if (second args)
                              (.orderByDescending document-query (name (first args)))
                              (.orderBy document-query (name (first args))))
         (= kind :where-eq) (.whereEquals document-query (name (first args)) (second args))
         (= kind :where-starts-with) (.whereStartsWith document-query (name (first args)) (second args))
         (= kind :where-ends-with) (.whereEndsWith document-query (name (first args)) (second args))
         (= kind :where-neq) (.whereNotEquals document-query (name (first args)) (second args))
         (= kind :where-in) (.whereIn document-query (name (first args)) (second args))
         (= kind :where-greater) (.whereGreaterThan document-query (name (first args)) (second args))
         (= kind :where-eq-or-greater) (.whereGreaterThanOrEqual document-query (name (first args)) (second args))
         (= kind :where-less) (.whereLessThan document-query (name (first args)) (second args))
         (= kind :where-eq-or-less) (.whereLessThanOrEqual document-query (name (first args)) (second args))
         (= kind :where-between) (.whereBetween document-query (name (first args)) (second args) (get args 2))))
     document-query)))

(defn ->vector
  "Returns a vector of a prepared `query` map using a `client` with an open session"
  [query client] (java->clj (.toList (gen-document-query query client))))

(defn ->count
  "Returns count of `query` results using `client` with an open session"
  [query client] (java->clj (.count (gen-document-query query client))))

(defn ->first
  "Returns the first item from `query` results using `client` with an open session"
  [query client] (java->clj (.first (gen-document-query query client))))
