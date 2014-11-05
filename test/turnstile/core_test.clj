(ns turnstile.core-test
  (:require [clojure.test :refer :all]
            [taoensso.carmine :as car]
            [turnstile.core :refer :all]))


(def test-conn (car/make-conn-spec))
(def test-pool (car/make-conn-pool))

(deftest redis-turnstile
  (let [req-id (java.util.UUID/randomUUID)
        now-ms (System/currentTimeMillis)
        turnstile (map->RedisTurnstile
                   {:conn-spec test-conn
                    :pool test-pool
                    :name "test-turnstile"
                    :expiration-ms 1000})]
    (reset turnstile)

    (add-timed-item turnstile req-id now-ms)
    
    (is (= req-id (first  (car/with-conn test-pool test-conn (car/zrange "test-turnstile" 0 1))))
        "item is added to turnstile")
    
    (is (= now-ms (read-string (car/with-conn test-pool test-conn (car/zscore "test-turnstile"  req-id))))
        "item time is set correctly.")

    (is (not (has-space? turnstile 1)) "doesn't allow more than limit items")
    

    (is (= (next-slot turnstile 1 (+ 100 now-ms)) 900) "next slot is calculated correctly")
    (is (= (next-slot turnstile 1 now-ms) 1000) "next slot is calculated correctly")

    (is (= (next-slot turnstile 1 (+ 10 now-ms)) 990) "net slot is calculated correctly")
    
    (is (has-space? turnstile 10) "allows less than limit items")
    (expire-entries turnstile (+  now-ms 100))
    (is (not (has-space? turnstile 1)) "does not expire events that are within duration")
    (expire-entries turnstile (+  now-ms 1001))
    (is (has-space? turnstile 10) "expired events outside duration")

    
    (add-timed-item turnstile req-id (+ 1 now-ms))
    (add-timed-item turnstile (java.util.UUID/randomUUID) (+ 2 now-ms))
    (is (= 2 (car/with-conn test-pool test-conn (car/zcard "test-turnstile")))
        "multiple items added.")

    (reset turnstile)
    (is (= 0 (car/with-conn test-pool test-conn (car/zcard "test-turnstile")))
        "reset clears all items")

    (is (has-space? turnstile 10) "has-space? works without any items in it yet.")
    (is (= (next-slot turnstile 1 now-ms) 0) "next slot works when empty")
    (is  (expire-entries turnstile (+  (System/currentTimeMillis) 1000))
         "expire-entries works without any items in it yet.")
    (is (has-space? turnstile 10) "works without any items in it yet.")
    
    ))




