(ns campfire.project
  (:refer-clojure :exclude [eval]))

(defprotocol Project
  (classpath [_] "Returns project's classpath"))

(defprotocol Evaluable
  (eval [_ form] "Takes ast, returns evaled-in-project result"))

(defprotocol Lifecycle
  (init [_] "Starts process with nrepl")
  (halt [_] "Kills process"))
