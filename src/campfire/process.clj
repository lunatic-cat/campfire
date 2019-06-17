(ns campfire.process
  (:refer-clojure :exclude [eval])
  (:require [clojure.string :as string]
            [nrepl.core :as nrepl]
            [campfire.autorequire :as autorequire]
            [campfire.file :refer [find-file-by-name]]
            [clojure.edn :as edn]
            [campfire.detect :as detect]
            [campfire.project :as proj])
  (:import [java.io File IOException PrintWriter]
           [java.net Socket ConnectException]))

(def host "127.0.0.1")
(def timeout 1000)

(declare init-proc)
(declare make-proc)
(declare halt-proc)

(defn- port-available? [port]
  (try (Socket. host port) false
       (catch IOException e true)))

(defn- wait-for-closed
  "Reset works too quickly, block until TIME_WAIT"
  [port]
  (loop [n 10]
    (when-not (port-available? port)
      (Thread/sleep n)
      (recur (* 2 n)))))

(defn- eval-with-nrepl [nrepl form]
  (-> (nrepl/client nrepl timeout)
      (nrepl/message {:op :eval :code (pr-str form)})
      nrepl/combine-responses))

(defn- print-flush [^PrintWriter w s]
  (when s
    (.write w s)
    (.flush w)))

(defrecord Proc [project port ^Process process nrepl]
  proj/Project
  (classpath [this]
    (proj/classpath (:project this)))

  proj/Evaluable
  (eval [this form]
    (let [with-require-form (autorequire/with-require-code form)
          {:keys [err out value status] :as msg} (eval-with-nrepl (:nrepl this)
                                                                  with-require-form)]
      (if (status "eval-error")
        (throw (Exception. err))
        (do
          (print-flush *err* err)
          (print-flush *out* out)
          (-> value last (or "") edn/read-string)))))

  proj/Lifecycle
  (init [this]
    (make-proc (:project this) (:port this)))
  (suspend [this]
    this)
  (resume [this opts old-opts]
    (when-not (= opts old-opts)
      (do (halt-proc this)
          (init-proc opts))))
  (halt [this]
    (halt-proc this)))

(defn- exec
  [cmd]
  (.exec (Runtime/getRuntime) (into-array ["/bin/sh" "-c" cmd])))

(defn init [classpath port]
  (let [cp (string/join File/pathSeparator classpath)
        cmd (str "java -cp " cp " clojure.main -m nrepl.cmdline -b " host " -p " port)]
    (exec cmd)))

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

(defn halt-proc [proc]
  (or (.close @@(:nrepl proc))
      (.destroy (:process proc))
      (wait-for-closed (:port proc))
      proc))

(defn init-proc [opts]
  (let [m (meta opts)
        project (-> opts :campfire.core/path detect/detect)
        proc (make-proc project (:campfire.core/port opts))]
    (with-meta proc m)))
