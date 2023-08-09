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
Do not tamper with the map returned from this function. As changes after opening the first session, will not take effect.

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
;; the function expects a client as first argument, a document/map as second argument and name of the collection as the last argument
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/add-doc! raven {:name "Ali Ferguson" :age 22} "people")
     meta))

;; Output
{:collection "people", :id "people/01e11da0-36f2-11ee-8788-5d62d3ca0185"}
```

Alternatively, you may pass in an ID for the document that needs to be inserted
```clojure
(rdb/add-doc! raven {:name "Ali Ferguson" :age 22} "people" "people/1" true)
```

## Patch Document
To update a document, use `patch-document!` and pass in the client, ID and changes on the document
```clojure
(with-open [raven (rdb/new-session! client)]
 (rdb/patch-doc! raven "people/1" {:name "Hussain Ferguson"}))
```

## Load Document
```clojure
;; pass the ID of the document as second argument and client as first argument
(with-open [raven (rdb/new-session! client)]
 (rdb/load-doc! raven "products/2-A"))

;; Output
{:PricePerUnit 19.0,
 :UnitsOnOrder 17,
 :Supplier "suppliers/1-A",
 :Discontinued false,
 :@metadata
 {:@collection "Products",
  :@counters ["⭐" "⭐⭐" "⭐⭐⭐" "⭐⭐⭐⭐" "⭐⭐⭐⭐⭐"],
  :@timeseries ["INC:Views"],
  :@change-vector
  "A:2568-F9I6Egqwm0Kz+K0oFVIR9Q, A:13366-IG4VwBTOnkqoT/uwgm2OQg, A:2568-OSKWIRBEDEGoAxbEIiFJeQ, A:8429-OefqsROfpk+6rfR/KLluqQ",
  :@flags "HasCounters, HasTimeSeries",
  :@id "products/2-A",
  :@last-modified "2023-07-26T21:52:11.2286883Z"},
 :Category "categories/1-A",
 :Name "Chang",
 :UnitsInStock 1,
 :ReorderLevel 25,
 :QuantityPerUnit "24 - 12 oz bottles"}
```
You may additional pass a vector to include related documents in the same roundtrip to the server
```clojure
;; Although two documents are loaded. Only one request is sent to RavenDB server.
(with-open [raven (rdb/new-session! client)]
 (rdb/load-doc! raven "products/2-A" ["Supplier"])
 (rdb/load-doc! raven "suppliers/1-A"))

;;Output
{:Contact {:Name "Charlotte Cooper", :Title "Purchasing Manager"},
 :Name "Exotic Liquids",
 :Address
 {:Line1 "49 Gilbert St.",
  :Line2 nil,
  :City "London",
  :Region nil,
  :PostalCode "EC1 4SD",
  :Country "UK",
  :Location nil},
 :Phone "(171) 555-2222",
 :Fax nil,
 :HomePage nil,
 :@metadata
 {:@collection "Suppliers",
  :@change-vector "A:21-OefqsROfpk+6rfR/KLluqQ",
  :@id "suppliers/1-A",
  :@last-modified "2018-07-27T12:11:53.0318842Z"}}
```

## Delete Document (opinion: almost never delete)
```clojure
(with-open [raven (rdb/new-session! client)]
 (rdb/delete-doc! raven "people/01e11da0-36f2-11ee-8788-5d62d3ca0185"))
```
