(ns turnstile.core
  (:require [taoensso.carmine :as car]))


(defprotocol Turnstile
  (expire-entries [this now-ms])
  (space [this limit])
  (has-space? [this limit])
  (next-slot [this limit now-ms])
  (add-timed-item [this item time-ms])
  (reset [this]))

(defrecord RedisTurnstile [conn-spec pool name expiration-ms]
  Turnstile
  (expire-entries [this now-ms]
    (car/with-conn pool conn-spec
      ;; remove all entries whose score is more them expiration-ms + 1 old (relative to now-ms)
      (car/zremrangebyscore name 0 (- now-ms expiration-ms 1)))
    this)
  (space [this limit]
    (- limit (car/with-conn pool conn-spec (car/zcard name))))
  (has-space? [this limit]
    (<  (car/with-conn pool conn-spec
          (car/zcard name)) limit))
  (next-slot [this limit now-ms]
    (if-let [earliest (first (car/with-conn pool conn-spec (car/zrange name 0 1)))]
      (let [request-time (or (read-string (car/with-conn pool conn-spec (car/zscore name earliest)))
                             0)]
        (max 0 (- (+ expiration-ms request-time)
                  now-ms)))
      0))
  (add-timed-item [this item time-ms]
    (car/with-conn pool conn-spec
      (car/zadd name time-ms item)
      (car/pexpire name (+ expiration-ms 1000))
      this))
  (reset [this]
    (car/with-conn pool conn-spec
      (car/del name))
    this))
