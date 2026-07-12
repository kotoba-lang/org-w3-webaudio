(ns w3.webaudio.protocol
  "Portable (JVM + cljs) encode/decode for the control-message envelope sent
   across an AudioWorkletNode's MessagePort (`w3.webaudio/post-message!` /
   `on-message!`). PCM sample data itself is a Float32Array and crosses the
   port directly (optionally as a Transferable) -- this namespace only frames
   small control messages (note-on/note-off/param changes/acks/etc.), the
   part of the port boundary that has no browser-only API surface and can
   therefore be unit tested without a browser.

   Wire messages are plain associative structures with STRING keys (postMessage
   payloads are structured-clone data, not EDN) and a string \"type\" -- see
   `encode-message`/`decode-message`. Keys are converted kebab-case <->
   camelCase at the boundary, matching the JS-side convention an
   AudioWorkletProcessor would expect from `event.data`."
  (:require [clojure.string :as str]))

(def valid-types
  #{:note-on :note-off :set-param :pcm-block :ack :error})

(defn- kebab->camel [k]
  (let [[first-word & more] (str/split (name k) #"-")]
    (apply str first-word (map str/capitalize more))))

(defn- camel->kebab [s]
  (-> s
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      str/lower-case))

(defn- keys->camel [m]
  (into {} (map (fn [[k v]] [(kebab->camel k) v])) m))

(defn- keys->kebab [m]
  (into {} (map (fn [[k v]] [(keyword (camel->kebab k)) v])) m))

(defn valid-message-type? [type]
  (contains? valid-types type))

(defn encode-message
  "{:type :note-on :note 60 :velocity 100 :voice-id \"a\"}
   -> {\"type\" \"note-on\" \"note\" 60 \"velocity\" 100 \"voiceId\" \"a\"}

   Throws (ex-info) if :type is missing or not in `valid-types`."
  [{:keys [type] :as msg}]
  (when-not (valid-message-type? type)
    (throw (ex-info "invalid webaudio protocol message type"
                     {:type type :valid valid-types})))
  (-> msg
      (dissoc :type)
      keys->camel
      (assoc "type" (name type))))

(defn decode-message
  "Inverse of `encode-message`. Throws (ex-info) if \"type\" is missing,
   not a string, or not one of `valid-types`."
  [wire]
  (let [type-str (get wire "type")
        type     (when (string? type-str) (keyword type-str))]
    (when-not (valid-message-type? type)
      (throw (ex-info "invalid webaudio protocol wire type"
                       {:type type-str :valid valid-types})))
    (-> wire
        (dissoc "type")
        keys->kebab
        (assoc :type type))))

(defn round-trip?
  "True if (decode-message (encode-message msg)) = msg."
  [msg]
  (= msg (decode-message (encode-message msg))))
