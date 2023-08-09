(ns hassanya.ravenousdb.utils
  (:require [cheshire.core :as json]))
(defprotocol ConvertibleToClojure
  (->clj [o]))

(extend-protocol ConvertibleToClojure
  java.util.Map
  (->clj [o] (let [entries (.entrySet o)]
               (reduce (fn [m [^String k v]]
                         (assoc m (keyword k) (->clj v)))
                       {} entries)))

  java.util.List
  (->clj [o] (vec (map ->clj o)))

  java.lang.Object
  (->clj [o] o)

  nil
  (->clj [_] nil))

(defn java->clj
  [m]
  (->clj m))


(defn clj->java [coll]
  (if (map? coll)
    (-> coll
      json/generate-string
      json/parse-string
      java.util.HashMap.)
    (-> coll
     json/generate-string
     json/parse-string
     java.util.ArrayList.)))
