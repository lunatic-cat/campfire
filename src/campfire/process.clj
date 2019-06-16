(ns campfire.process
  (:require [clojure.string :as string]
            [nrepl.core :as nrepl]
            [campfire.project :as proj])
  (:import [java.io File IOException]
           [java.net Socket ConnectException]))

(def host "127.0.0.1")
(def timeout 1000)

(declare make-proc)

(defrecord Proc [project port ^Process process nrepl]
  proj/Project
  (classpath [this]
    (proj/classpath (:project this)))

  proj/Evaluable
  (eval [this form]
    (-> (nrepl/client nrepl timeout)
        (nrepl/message {:op :eval :code (pr-str form)})
        nrepl/combine-responses))

  proj/Lifecycle
  (init [this]
    (make-proc (:project this) (:port this)))
  (halt [this]
    (or (.close (:nrepl this))
        (.destroy (:process this)))))

(defn- exec
  [cmd]
  (.exec (Runtime/getRuntime) (into-array ["/bin/sh" "-c" cmd])))

(defn init [classpath port]
  (let [cp (string/join File/pathSeparator classpath)
        cmd (str "java -cp " cp " clojure.main -m nrepl.cmdline -b " host " -p " port)]
    (exec cmd)))

(defn- port-available? [port]
  (try (Socket. host port) false
       (catch IOException e true)))

(defn- wait-for-nrepl [port]
  (loop [n 10]
    (if-let [nrepl (try (nrepl/connect :host host :port port)
                        (catch ConnectException e nil))]
      nrepl
      (do (Thread/sleep n)
          (recur (* 2 n))))))

(defn make-proc [project port]
  (let [process (when (port-available? port)
                  (init (proj/classpath project) port))
        nrepl (wait-for-nrepl port)]
    (->Proc project port process nrepl)))
