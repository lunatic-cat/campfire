(ns campfire.core
  (:refer-clojure :exclude [eval])
  (:require [campfire.lein :as lein]
            [campfire.detect :as detect]
            [campfire.project :as proj]
            [campfire.process :as proc]))

(def eval proj/eval)
(def detect detect/detect)
(def init proc/init-proc)
(def halt proj/halt)
