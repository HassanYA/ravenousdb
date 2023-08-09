# Ravenous DB
A RavenDB client for Clojure (Wrapper around ravendb-jvm-client) 

⚠️ Experimental - Do not use in Production ⚠️

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
# CRUD Operations
All CRUD operations must be made with a client that has a session open.

## Create Document
Use `add-doc!` to create a document. The ID and Collection of the document can be found in the map's meta
```clojure
;; the function expects a client as first argument, a document/map as second argument and name of the collection as lasst argument
(with-open [raven (rdb/new-session! client)]
 (rdb/add-doc! raven {:name "Ali Ferguson" :age 22} "people"))

;; Output
{:name "Ali Ferguson", :age 22}
```

Using `meta` to get inserted document ID and collection
```clojure
;; the function expects a client as first argument, a document/map as second argument and name of the collection as lasst argument
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/add-doc! raven {:name "Ali Ferguson" :age 22} "people")))

;; Output
{:collection "people", :id "people/01e11da0-36f2-11ee-8788-5d62d3ca0185"}
```

Alternatively, you may pass in an ID for the document that needs to be inserted
```clojure
(rdb/add-doc! raven {:name "Ali Ferguson" :age 22} "people" "people/1" true)
```
