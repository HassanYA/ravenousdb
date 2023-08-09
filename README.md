# Ravenous DB
A RavenDB client for Clojure (Wrapper around ravendb-jvm-client) 

🚧 Experimental - Do not use in Production

### Why?
It is interesting, RavenDB seems awesome.

# Usage
## Creating a Client
To use ravendb, connection details must be present. To do this, use `new-client` and pass in db name in the first argument and node url(s) in the second argument
```clojure
;; Requiring RavenousDb
(require '[hassanya.ravenousdb.core :as rdb])

;; creating a client
(def client (new-client "northwind" "http://localhost:8080"))

;; alternatively, new-client also accepts a vector for more than one node
(def client (new-client "northwind" ["http://localhost:8080" "http://localhost:8000"]))
```
A client should be created once per application (given that there is only one DB that the application requires). 
Do not tamper with the map returned from this function. As changes after opening a session, will not take effect.

## Opening a Session
Before executing in operation against RavenDB server, a session must be created. This can be achieved using `new-session!`.
Session instance then can be used to send commands to RavenDB
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/order-by :Name)
     (rdb/limit 3)
     (rdb/->vector raven))))
```

