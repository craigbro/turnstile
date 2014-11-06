# turnstile

A Clojure library providing a distributed rate-limiting service on top
of Redis.

A turnstile is a device for limiting a stream of events to a certain
rate, (N events per time period T).  There is no reference to "clock"
time, think of it more like a flow restrictor on a pipe.

Let's assume we want to limit events to 10 per minute.  If 10 events
occur, event-11 cannot occur until 1 minute after event-1 occured.
Then 1 minutes after event-2 occured, event-12 could occur.

Our turnstiles are impleemented as an ordered set of events with a
time associated with each.  They can be expired, we can check if a
space is available for another event, and we can ask it how long we
have to wait for the next event.

They is implemented as a Redis ZSET.  Multiple processes can use the
turnstile, sharing state.  This means you can have many API servers
using the same collection of turnstiles to due rate limiting on
requests.  The turnstiles will expire themselves from the Redis
database when not in use.


## Usage

    (def conn (taoensso.carmine/make-conn-spec))
    (def pool (taoensso.carmine/make-conn-pool))
    
    ;; turnstile with 1s time period
    (def turnstile
         (map->RedisTurnstile {:conn-spec conn
                               :pool pool
                               :name "test-turnstile"
                               :expiration-ms 1000}))
    ;; given a limit of 10 events per second
    ;; is there space for a new event
    (has-space? turnstile 10)

    ;; expire events more than a second old
    (expire-entries turnstile (System/currentTimeMillis))

    ;; how long, in ms, must I wait until I can add the next event?
    (next-slot turnstile 10 (System/currentTimeMillis))

    ;; record an event
    (add-timed-item turnstile (java.util.UUID/randomUUID) (System/currentTimeMillis))


The `name` provided to the turnstile is used literally as the key name
in redis, so be mindful of your naming schemes!

Turnstiles are cheap to make, and store no state internally.  This
means you could make a request rate limiter as follows:

    (def rate-limit-defaults {:conn-spec conn :pool pool :expiration-ms 60000})


    (defn check-rate-limit
      "Returns true if username can make another request."
      [username]
      (let [now (System/currentTimeMillis)
            turnstile (map->RedisTurnstile (assoc rate-limit-defaults :name (str username "-api-turnstile")))]
        (if (has-space? turnstile 10)
          (and (add-timed-item turnstile (java.util.UUID/randomUUID) now) true)
          (do (expire-entries turnstile now)
              (has-space? turnstile 10)))))


Note that we use a randomUUID as the value of each event.  If you
wanted to limit someone to downloading 10 unique images per minute,
you could use an even identifier based on the image filename.  When
they download the image a second time, it will only update the time
for that event, not make an entirely new event.

## License

Copyright © 2014 Craig Brozefsky

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
