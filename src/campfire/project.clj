(ns campfire.project
  (:refer-clojure :exclude [eval]))

(defprotocol Project
  :extend-via-metadata true
  (classpath [_] "Returns project's classpath"))

(defprotocol Evaluable
  :extend-via-metadata true
  (eval [_ form] "Takes ast, returns evaled-in-project result"))

(defprotocol Lifecycle
  :extend-via-metadata true
  (init [_] "Starts process with nrepl")
  (halt [_] "Kills process"))
