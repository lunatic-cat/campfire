(ns campfire.lein
  (:refer-clojure :exclude [eval])
  (:require [campfire.project :as proj]
            [campfire.process :as process]
            [campfire.file :refer [abspath]]
            [tempfile.core :refer [tempdir]]
            [clojure.java.io :as io]
            [leiningen.core.classpath :as lein-classpath]
            [leiningen.core.project :as lein-project]))

(defrecord Project [source project]
  proj/Project
  (classpath [this]
    (lein-classpath/get-classpath (:project this))))

(defn make-project [source project-clj]
  (let [project (lein-project/read (io/reader project-clj))
        ;; project (assoc project :local-repo (abspath (tempdir)))
        project (assoc project :root (abspath source))
        clsp-files (lein-classpath/resolve-managed-dependencies
              :dependencies :managed-dependencies project)]
    (->Project source project)))
