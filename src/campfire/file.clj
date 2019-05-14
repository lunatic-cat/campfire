(ns campfire.file
  (:require [clojure.java.io :as io]))

(defn abspath [^java.io.File f]
  (.getAbsolutePath f))

(defn file? [^java.io.File f]
  (.isFile f))

(defn filename [^java.io.File f]
  (.getName f))

(defn find-file-by-name [dir fname]
  (->> dir
       file-seq
       (filter #(and (file? %) (-> % filename (= fname))))
       first))
