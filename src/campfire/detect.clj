(ns campfire.detect
  (:refer-clojure :exclude [eval])
  (:require [campfire.lein :as lein]
            [campfire.file :refer [find-file-by-name]]
            [clojure.java.io :as io]))

(defn detect
  ([path] (detect path {}))
  ([path opts]
   (let [local-path (io/file path)
         project-clj (find-file-by-name local-path "project.clj")
         build-boot (find-file-by-name local-path "build.boot")
         deps-edn (find-file-by-name local-path "deps.edn")]
     (cond
       project-clj (lein/make-project local-path project-clj opts)
       ;; build-boot (boot/make-project local-path butld-boot opts)
       ;; deps-edn (deps/make-project local-path deps-edn opts)
       :else (throw (Exception. "Cannot detect project type"))))))
