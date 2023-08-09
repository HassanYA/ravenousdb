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
## CRUD Operations
All CRUD operations must be made with a client that has a session open.

### Create Document
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

### Patch Document
To update a document, use `patch-document!` and pass in the client, ID and changes on the document
```clojure
(with-open [raven (rdb/new-session! client)]
 (rdb/patch-doc! raven "people/1" {:name "Hussain Ferguson"}))
```

### Load Document
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

### Delete Document (opinion: almost never delete)
```clojure
(with-open [raven (rdb/new-session! client)]
 (rdb/delete-doc! raven "people/01e11da0-36f2-11ee-8788-5d62d3ca0185"))
```

### Pipelining
All CRUD operations (except load-doc!) accept an additonal param to indicate whether the changes should be saved immediately or wait for a singal `save-changes!`. By default, all changes are executed when the function is called, this can be altered by passing an additonal parameter with value `false`.

```clojure
;; although five write operations are made, only one request to the server is made
(with-open [raven (rdb/new-session! client)]
  (rdb/patch-doc! raven "people-1" {:name "Marzook Tyson" :age 55} false)
  (rdb/patch-doc! raven "people-2" {:age "Nabeel Foreman"} false)
  (rdb/delete-doc! raven "people-3" false)
  (rdb/add-doc! raven {:name "Cute bbaby" :age 3} "people" false)
  (rdb/save-changes! raven))
```
Do note that once a request is made to the server, all changes will be submitted, regardless of `save-changes!`.

## Query Builder
Queries are be built using `query` function. The function takes in only one argument which is the collection name. Queries are not bound to a client, thus, you may store this and (re)use it when a session is opened. 

All query operations return an new instance of query, Therefore, using a threading macro makes a lot of sense to chain query operations.
```clojure
;; example of a query
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     ...
     ...
     (rdb/->vector raven)))
```


### Where Equals
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-equal :Name "Chang")
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 19.0,
  :UnitsOnOrder 17,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/1-A",
  :Name "Chang",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "24 - 12 oz bottles"})
```

### Where Not Equal
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-not-equal :Supplier "suppliers/1-A")
     (rdb/where-not-equal :Category "categories/2-A")
     (rdb/where-equal :UnitsInStock 4)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 97.0,
  :UnitsOnOrder 29,
  :Supplier "suppliers/4-A",
  :Discontinued true,
  :Category "categories/6-A",
  :Name "Mishi Kobe Niku",
  :UnitsInStock 4,
  :ReorderLevel 0,
  :QuantityPerUnit "18 - 500 g pkgs."}
 {:PricePerUnit 31.0,
  :UnitsOnOrder 31,
  :Supplier "suppliers/4-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Ikura",
  :UnitsInStock 4,
  :ReorderLevel 0,
  :QuantityPerUnit "12 - 200 ml jars"}
 {:PricePerUnit 10.0,
  :UnitsOnOrder 4,
  :Supplier "suppliers/4-A",
  :Discontinued false,
  :Category "categories/7-A",
  :Name "Longlife Tofu",
  :UnitsInStock 4,
  :ReorderLevel 5,
  :QuantityPerUnit "5 kg pkg."})
```

### Where Greater Than, Greater Than or Equal, Less Than, Less Than and Equal
All of these operation accept the same arguments, a query, a field and a value
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-greater :UnitsInStock 5)
     (rdb/where-less :UnitsInStock 10)
     (rdb/where-eq-or-greater :PricePerUnit 30)
     (rdb/where-eq-or-less :PricePerUnit 90)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 39.0,
  :UnitsOnOrder 0,
  :Supplier "suppliers/7-A",
  :Discontinued true,
  :Category "categories/6-A",
  :Name "Alice Mutton",
  :UnitsInStock 7,
  :ReorderLevel 0,
  :QuantityPerUnit "20 - 1 kg tins"}
 {:PricePerUnit 62.5,
  :UnitsOnOrder 42,
  :Supplier "suppliers/7-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Carnarvon Tigers",
  :UnitsInStock 7,
  :ReorderLevel 0,
  :QuantityPerUnit "16 kg pkg."}
 {:PricePerUnit 81.0,
  :UnitsOnOrder 40,
  :Supplier "suppliers/8-A",
  :Discontinued false,
  :Category "categories/3-A",
  :Name "Sir Rodney's Marmalade",
  :UnitsInStock 8,
  :ReorderLevel 0,
  :QuantityPerUnit "30 gift boxes"}
 {:PricePerUnit 43.9,
  :UnitsOnOrder 24,
  :Supplier "suppliers/7-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Vegie-spread",
  :UnitsInStock 7,
  :ReorderLevel 5,
  :QuantityPerUnit "15 - 625 g jars"})
```

### Where Between
```clojure
;; where-between expects a field, a min value and a max value 
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-between :UnitsInStock 3 5)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 19.0,
  :UnitsOnOrder 17,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/1-A",
  :Name "Chang",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "24 - 12 oz bottles"}
 {:PricePerUnit 10.0,
  :UnitsOnOrder 13,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Aniseed Syrup",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "12 - 550 ml bottles"}
 {:PricePerUnit 22.0,
  :UnitsOnOrder 53,
  :Supplier "suppliers/2-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Chef Anton's Cajun Seasoning",
  :UnitsInStock 2,
  ....
```
### Where Starts With, Ends With
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-starts-with :Name "Chef")
     (rdb/where-ends-with :Name "Mix")
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 21.35,
  :UnitsOnOrder 0,
  :Supplier "suppliers/2-A",
  :Discontinued true,
  :Category "categories/2-A",
  :Name "Chef Anton's Gumbo Mix",
  :UnitsInStock 2,
  :ReorderLevel 0,
  :QuantityPerUnit "36 boxes"})
```

### Where In, Not In
```clojure
(-> (rdb/query "products")
     (rdb/where-in :Supplier ["suppliers/5-A" "suppliers/6-A"])
     (rdb/where-not-in :Category ["categories/4-A" "categories/7-A"])
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 6.0,
  :UnitsOnOrder 24,
  :Supplier "suppliers/6-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Konbu",
  :UnitsInStock 6,
  :ReorderLevel 5,
  :QuantityPerUnit "2 kg box"}
 {:PricePerUnit 15.5,
  :UnitsOnOrder 39,
  :Supplier "suppliers/6-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Genen Shouyu",
  :UnitsInStock 6,
  :ReorderLevel 5,
  :QuantityPerUnit "24 - 250 ml bottles"})
```

### Order By (Asc/Desc)
Order the results of the query by a specific field. By default ordering is done in asc. This behavour can be altered by passing in an additonal parameter as true or using `order-by-desc`. `order-by-asc` is also available as an alias for `order-by` but does not accept an addional parameter for `desc?`
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/order-by :Name) 
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 39.0,
  :UnitsOnOrder 0,
  :Supplier "suppliers/7-A",
  :Discontinued true,
  :Category "categories/6-A",
  :Name "Alice Mutton",
  :UnitsInStock 7,
  :ReorderLevel 0,
  :QuantityPerUnit "20 - 1 kg tins"}
 {:PricePerUnit 10.0,
  :UnitsOnOrder 13,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Aniseed Syrup",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "12 - 550 ml bottles"}
 {:PricePerUnit 18.4,
  :UnitsOnOrder 123,
  :Supplier "suppliers/19-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Boston Crab Meat",
  :UnitsInStock 19,
  :ReorderLevel 30,
  :QuantityPerUnit "24 - 4 oz tins"}
....
```


### Limit, Skip
As the name implies, limit will restrict the result to x number of documents and skip will move past the first y number of documents of query results
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/limit 2)
     (rdb/skip 1)
     (rdb/order-by :Name)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 10.0,
  :UnitsOnOrder 13,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Aniseed Syrup",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "12 - 550 ml bottles"}
 {:PricePerUnit 18.4,
  :UnitsOnOrder 123,
  :Supplier "suppliers/19-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Boston Crab Meat",
  :UnitsInStock 19,
  :ReorderLevel 30,
  :QuantityPerUnit "24 - 4 oz tins"})
```

### Executing a Query
When executing a query a client with an open session is need. There are 3 option to execute a query as it stands (`->vector` `->first` `->count`). Here is a description for what each does:

#### ->vector
```clojure
;; `->vector` will return all query result documents (skip and limit do take effect) as a list
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/order-by :Name)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 39.0,
  :UnitsOnOrder 0,
  :Supplier "suppliers/7-A",
  :Discontinued true,
  :Category "categories/6-A",
  :Name "Alice Mutton",
  :UnitsInStock 7,
  :ReorderLevel 0,
  :QuantityPerUnit "20 - 1 kg tins"}
 {:PricePerUnit 10.0,
  :UnitsOnOrder 13,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Aniseed Syrup",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "12 - 550 ml bottles"}
 {:PricePerUnit 18.4,
  :UnitsOnOrder 123,
  :Supplier "suppliers/19-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Boston Crab Meat",
  :UnitsInStock 19,
  :ReorderLevel 30,
  :QuantityPerUnit "24 - 4 oz tins"}
  ....)
```
#### ->first
```clojure
;; `->first` will return the first document found in a query as a map
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/order-by :Name)
     (rdb/->first raven)))

;; Output
{:PricePerUnit 39.0,
 :UnitsOnOrder 0,
 :Supplier "suppliers/7-A",
 :Discontinued true,
 :Category "categories/6-A",
 :Name "Alice Mutton",
 :UnitsInStock 7,
 :ReorderLevel 0,
 :QuantityPerUnit "20 - 1 kg tins"}
```

### ->count
```clojure

;; Output
```# Ravenous DB
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
## CRUD Operations
All CRUD operations must be made with a client that has a session open.

### Create Document
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

### Patch Document
To update a document, use `patch-document!` and pass in the client, ID and changes on the document
```clojure
(with-open [raven (rdb/new-session! client)]
 (rdb/patch-doc! raven "people/1" {:name "Hussain Ferguson"}))
```

### Load Document
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

### Delete Document (opinion: almost never delete)
```clojure
(with-open [raven (rdb/new-session! client)]
 (rdb/delete-doc! raven "people/01e11da0-36f2-11ee-8788-5d62d3ca0185"))
```

### Pipelining
All CRUD operations (except load-doc!) accept an additonal param to indicate whether the changes should be saved immediately or wait for a singal `save-changes!`. By default, all changes are executed when the function is called, this can be altered by passing an additonal parameter with value `false`.

```clojure
;; although five write operations are made, only one request to the server is made
(with-open [raven (rdb/new-session! client)]
  (rdb/patch-doc! raven "people-1" {:name "Marzook Tyson" :age 55} false)
  (rdb/patch-doc! raven "people-2" {:age "Nabeel Foreman"} false)
  (rdb/delete-doc! raven "people-3" false)
  (rdb/add-doc! raven {:name "Cute bbaby" :age 3} "people" false)
  (rdb/save-changes! raven))
```
Do note that once a request is made to the server, all changes will be submitted, regardless of `save-changes!`.

## Query Builder
Queries are be built using `query` function. The function takes in only one argument which is the collection name. Queries are not bound to a client, thus, you may store this and (re)use it when a session is opened. 

All query operations return an new instance of query, Therefore, using a threading macro makes a lot of sense to chain query operations.
```clojure
;; example of a query
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     ...
     ...
     (rdb/->vector raven)))
```


### Where Equals
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-equal :Name "Chang")
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 19.0,
  :UnitsOnOrder 17,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/1-A",
  :Name "Chang",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "24 - 12 oz bottles"})
```

### Where Not Equal
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-not-equal :Supplier "suppliers/1-A")
     (rdb/where-not-equal :Category "categories/2-A")
     (rdb/where-equal :UnitsInStock 4)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 97.0,
  :UnitsOnOrder 29,
  :Supplier "suppliers/4-A",
  :Discontinued true,
  :Category "categories/6-A",
  :Name "Mishi Kobe Niku",
  :UnitsInStock 4,
  :ReorderLevel 0,
  :QuantityPerUnit "18 - 500 g pkgs."}
 {:PricePerUnit 31.0,
  :UnitsOnOrder 31,
  :Supplier "suppliers/4-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Ikura",
  :UnitsInStock 4,
  :ReorderLevel 0,
  :QuantityPerUnit "12 - 200 ml jars"}
 {:PricePerUnit 10.0,
  :UnitsOnOrder 4,
  :Supplier "suppliers/4-A",
  :Discontinued false,
  :Category "categories/7-A",
  :Name "Longlife Tofu",
  :UnitsInStock 4,
  :ReorderLevel 5,
  :QuantityPerUnit "5 kg pkg."})
```

### Where Greater Than, Greater Than or Equal, Less Than, Less Than and Equal
All of these operation accept the same arguments, a query, a field and a value
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-greater :UnitsInStock 5)
     (rdb/where-less :UnitsInStock 10)
     (rdb/where-eq-or-greater :PricePerUnit 30)
     (rdb/where-eq-or-less :PricePerUnit 90)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 39.0,
  :UnitsOnOrder 0,
  :Supplier "suppliers/7-A",
  :Discontinued true,
  :Category "categories/6-A",
  :Name "Alice Mutton",
  :UnitsInStock 7,
  :ReorderLevel 0,
  :QuantityPerUnit "20 - 1 kg tins"}
 {:PricePerUnit 62.5,
  :UnitsOnOrder 42,
  :Supplier "suppliers/7-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Carnarvon Tigers",
  :UnitsInStock 7,
  :ReorderLevel 0,
  :QuantityPerUnit "16 kg pkg."}
 {:PricePerUnit 81.0,
  :UnitsOnOrder 40,
  :Supplier "suppliers/8-A",
  :Discontinued false,
  :Category "categories/3-A",
  :Name "Sir Rodney's Marmalade",
  :UnitsInStock 8,
  :ReorderLevel 0,
  :QuantityPerUnit "30 gift boxes"}
 {:PricePerUnit 43.9,
  :UnitsOnOrder 24,
  :Supplier "suppliers/7-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Vegie-spread",
  :UnitsInStock 7,
  :ReorderLevel 5,
  :QuantityPerUnit "15 - 625 g jars"})
```

### Where Between
```clojure
;; where-between expects a field, a min value and a max value 
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-between :UnitsInStock 3 5)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 19.0,
  :UnitsOnOrder 17,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/1-A",
  :Name "Chang",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "24 - 12 oz bottles"}
 {:PricePerUnit 10.0,
  :UnitsOnOrder 13,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Aniseed Syrup",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "12 - 550 ml bottles"}
 {:PricePerUnit 22.0,
  :UnitsOnOrder 53,
  :Supplier "suppliers/2-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Chef Anton's Cajun Seasoning",
  :UnitsInStock 2,
  ....
```
### Where Starts With, Ends With
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/where-starts-with :Name "Chef")
     (rdb/where-ends-with :Name "Mix")
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 21.35,
  :UnitsOnOrder 0,
  :Supplier "suppliers/2-A",
  :Discontinued true,
  :Category "categories/2-A",
  :Name "Chef Anton's Gumbo Mix",
  :UnitsInStock 2,
  :ReorderLevel 0,
  :QuantityPerUnit "36 boxes"})
```

### Where In, Not In
```clojure
(-> (rdb/query "products")
     (rdb/where-in :Supplier ["suppliers/5-A" "suppliers/6-A"])
     (rdb/where-not-in :Category ["categories/4-A" "categories/7-A"])
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 6.0,
  :UnitsOnOrder 24,
  :Supplier "suppliers/6-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Konbu",
  :UnitsInStock 6,
  :ReorderLevel 5,
  :QuantityPerUnit "2 kg box"}
 {:PricePerUnit 15.5,
  :UnitsOnOrder 39,
  :Supplier "suppliers/6-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Genen Shouyu",
  :UnitsInStock 6,
  :ReorderLevel 5,
  :QuantityPerUnit "24 - 250 ml bottles"})
```

### Order By (Asc/Desc)
Order the results of the query by a specific field. By default ordering is done in asc. This behavour can be altered by passing in an additonal parameter as true or using `order-by-desc`. `order-by-asc` is also available as an alias for `order-by` but does not accept an addional parameter for `desc?`
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/order-by :Name) 
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 39.0,
  :UnitsOnOrder 0,
  :Supplier "suppliers/7-A",
  :Discontinued true,
  :Category "categories/6-A",
  :Name "Alice Mutton",
  :UnitsInStock 7,
  :ReorderLevel 0,
  :QuantityPerUnit "20 - 1 kg tins"}
 {:PricePerUnit 10.0,
  :UnitsOnOrder 13,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Aniseed Syrup",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "12 - 550 ml bottles"}
 {:PricePerUnit 18.4,
  :UnitsOnOrder 123,
  :Supplier "suppliers/19-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Boston Crab Meat",
  :UnitsInStock 19,
  :ReorderLevel 30,
  :QuantityPerUnit "24 - 4 oz tins"}
....
```


### Limit, Skip
As the name implies, limit will restrict the result to x number of documents and skip will move past the first y number of documents of query results
```clojure
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/limit 2)
     (rdb/skip 1)
     (rdb/order-by :Name)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 10.0,
  :UnitsOnOrder 13,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Aniseed Syrup",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "12 - 550 ml bottles"}
 {:PricePerUnit 18.4,
  :UnitsOnOrder 123,
  :Supplier "suppliers/19-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Boston Crab Meat",
  :UnitsInStock 19,
  :ReorderLevel 30,
  :QuantityPerUnit "24 - 4 oz tins"})
```

### Executing a Query
When executing a query a client with an open session is need. There are 3 option to execute a query as it stands (`->vector` `->first` `->count`). Here is a description for what each does:

#### ->vector
```clojure
;; `->vector` will return all query result documents (skip and limit do take effect) as a list
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/order-by :Name)
     (rdb/->vector raven)))

;; Output
({:PricePerUnit 39.0,
  :UnitsOnOrder 0,
  :Supplier "suppliers/7-A",
  :Discontinued true,
  :Category "categories/6-A",
  :Name "Alice Mutton",
  :UnitsInStock 7,
  :ReorderLevel 0,
  :QuantityPerUnit "20 - 1 kg tins"}
 {:PricePerUnit 10.0,
  :UnitsOnOrder 13,
  :Supplier "suppliers/1-A",
  :Discontinued false,
  :Category "categories/2-A",
  :Name "Aniseed Syrup",
  :UnitsInStock 1,
  :ReorderLevel 25,
  :QuantityPerUnit "12 - 550 ml bottles"}
 {:PricePerUnit 18.4,
  :UnitsOnOrder 123,
  :Supplier "suppliers/19-A",
  :Discontinued false,
  :Category "categories/8-A",
  :Name "Boston Crab Meat",
  :UnitsInStock 19,
  :ReorderLevel 30,
  :QuantityPerUnit "24 - 4 oz tins"}
  ....)
```
#### ->first
```clojure
;; `->first` will return the first document found in a query as a map
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/order-by :Name)
     (rdb/->first raven)))

;; Output
{:PricePerUnit 39.0,
 :UnitsOnOrder 0,
 :Supplier "suppliers/7-A",
 :Discontinued true,
 :Category "categories/6-A",
 :Name "Alice Mutton",
 :UnitsInStock 7,
 :ReorderLevel 0,
 :QuantityPerUnit "20 - 1 kg tins"}
```

#### ->count
```clojure
;; `->count` will return the number of documents that would be produced from a query (skip and limit do NOT take effect)
(with-open [raven (rdb/new-session! client)]
 (-> (rdb/query "products")
     (rdb/order-by :Name)
     (rdb/->count raven)))
;; Output
77
```
