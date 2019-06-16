(ns campfire.core
  (:refer-clojure :exclude [eval])
  (:require [campfire.lein :as lein]
            [campfire.autorequire :as autorequire]
            [campfire.project :as proj]
            [campfire.process :as proc]
            [campfire.file :refer [find-file-by-name]]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import [java.io PrintWriter]))

(defn- print-flush [^PrintWriter w s]
  (when s
    (.write w s)
    (.flush w)))

(defn process [project port]
  (proc/make-proc project port))

(defn halt [process]
  (proj/halt process))

(defn eval [process form]
  (let [require-form (autorequire/with-require-code form)
        {:keys [err out value status] :as msg} (proj/eval process require-form)]
    (if (status "eval-error")
      (throw (Exception. err))
      (do
        (print-flush *err* err)
        (print-flush *out* out)
        (-> value last (or "") edn/read-string)))))

(defn detect [path]
  (let [local-path (io/file path)
        project-clj (find-file-by-name local-path "project.clj")
        build-boot (find-file-by-name local-path "build.boot")
        deps-edn (find-file-by-name local-path "deps.edn")]
    (cond
      project-clj (lein/make-project local-path project-clj)
      ;; build-boot (boot/make-project local-path butld-boot)
      ;; deps-edn (deps/make-project local-path deps-edn)
      :else (throw (Exception. "Cannot detect project type")))))

(defn init [opts]
  (let [m (meta opts)
        project (-> opts ::path detect)
        proc (process project (::port opts))]
    (with-meta proc m)))
