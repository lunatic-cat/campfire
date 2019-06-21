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

(def default-host "127.0.0.1")
(def default-port 7878)
(def default-timeout 1000)

(declare init-proc)
(declare make-proc)
(declare halt-proc)

(defn- port-available? [{:keys [host port]}]
  (try (with-open [_s (Socket. host port)]
         false)
       (catch IOException e true)))

(defn- wait-for-closed
  "Reset works too quickly, block until TIME_WAIT"
  [opts]
  (loop [n 10]
    (when-not (port-available? opts)
      (Thread/sleep n)
      (recur (* 2 n)))))

(defn- eval-with-nrepl [{:keys [nrepl timeout]} form]
  (-> (nrepl/client nrepl timeout)
      (nrepl/message {:op :eval :code (pr-str form)})
      nrepl/combine-responses))

(defn- print-flush [^PrintWriter w s]
  (when s
    (.write w s)
    (.flush w)))

(defrecord Proc [project port ^Process process nrepl timeout host]
  proj/Project
  (classpath [this]
    (proj/classpath (:project this)))

  proj/Evaluable
  (eval [this form]
    (let [with-require-form (autorequire/with-require-code form)
          {:keys [err out value status]} (eval-with-nrepl this with-require-form)]
      (if (and status (status "eval-error"))
        (throw (Exception. err))
        (do
          (print-flush *err* err)
          (print-flush *out* out)
          (-> value last (or "") edn/read-string)))))

  proj/Lifecycle
  (init [this]
    (make-proc (:project this) (select-keys this [:port :host :timeout])))
  (suspend [this]
    this)
  (resume [this opts old-opts]
    (if (= opts old-opts)
      this
      (do (halt-proc this)
          (init-proc opts))))
  (halt [this]
    (halt-proc this)))

(defn- exec
  [cmd]
  (.exec (Runtime/getRuntime) (into-array ["/bin/sh" "-c" cmd])))

(defn init [classpath {:keys [host port]}]
  (let [cp (string/join File/pathSeparator classpath)
        cmd (str "java -cp " cp " clojure.main -m nrepl.cmdline -b " host " -p " port)]
    (exec cmd)))

(defn- wait-for-nrepl [{:keys [host port]}]
  (loop [n 10]
    (if-let [nrepl (try (nrepl/connect :host host :port port)
                        (catch ConnectException e nil))]
      nrepl
      (do (Thread/sleep n)
          (recur (* 2 n))))))

(defn make-proc [project {:keys [port timeout host]}]
  (let [host-port {:port (or port default-port) :host (or host default-host)}
        process (when (port-available? host-port)
                  (init (proj/classpath project) host-port))
        nrepl (wait-for-nrepl host-port)]
    (->Proc project port process nrepl (or timeout default-timeout) host)))

(defn halt-proc [proc]
  (or (some-> proc :nrepl .close)
      (when-let [p (:process proc)]
        (.destroy p)
        (wait-for-closed proc))
      proc))

(defn init-proc [opts]
  (let [m (meta opts)
        project (-> opts :campfire.core/path detect/detect)
        proc (make-proc project {:port (:campfire.core/port opts)
                                 :host (:campfire.core/host opts)
                                 :timeout (:campfire.core/timeout opts)})]
    (with-meta proc m)))
