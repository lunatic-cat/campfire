(ns campfire.autorequire
  (:require [clojure.zip :as z]))

(defn postwalk-reduce
  ([f x] (postwalk-reduce f nil x))
  ([f acc x]
   (loop [loc (z/seq-zip x)
          acc acc]
     (cond
       (z/end? loc)
       (conj acc (f (z/node loc)))

       (z/branch? loc)
       (recur (z/next loc) acc)

       :else
       (recur
        (z/next loc)
        (into
         (conj acc (f (z/node loc)))
         (f (reverse
             (drop
              ((fnil count [nil]) (z/path (z/next loc)))
              (z/path loc))))))))))

(defn- extract-namespaces [x]
  (remove nil? (postwalk-reduce (fn [s] (when (qualified-symbol? s) (namespace s))) #{} x)))

(defn- make-require [ns-name]
  `(require (quote ~(symbol ns-name))))

(defn with-require-code [x]
  (let [requires (map make-require (extract-namespaces x))]
    `(do ~@requires
         ~x)))
