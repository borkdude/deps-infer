(ns deps-infer.clojars
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(def suffix-re #"\.clj$|\.cljs$|\.cljc$") ;; Not including __init\.class$ as this gives false positives!

(defn lang [filename]
  (keyword (last (str/split filename #"\."))))

(defn index-jar [index jar]
  (let [jar (str jar)
        jar-file (fs/file jar)]
    (if-not (fs/directory? jar-file)
      (with-open [jar-resource (java.util.jar.JarFile. jar-file)]
        (let [entries (enumeration-seq (.entries jar-resource))]
          (reduce (fn [acc e]
                    (let [raw-name (.getName e)]
                      (if (and (not (str/starts-with? raw-name "META"))
                               (re-find suffix-re raw-name))
                        (let [n (str/replace raw-name suffix-re "")
                              n (str/replace n "_" "-")
                              n (str/replace n "/" ".")
                              n (symbol n)
                              [_ group-id artifact version _]
                              (re-find #"repository/(.*)/(.*)/(.*)/.*jar" jar)]
                          (update acc n (fnil conj [])
                                  {:mvn/version version
                                   :file raw-name
                                   :group-id group-id
                                   :artifact artifact}))
                        acc)))
                  index
                  entries)))
      index)))

(def cli-options
  ;; An option with a required argument
  [["-r" "--repo M2_PATH" "The m2 repo to index. Defaults to ~/.m2."
    :default (str (io/file (System/getProperty "user.home") ".m2"))]
   ;; A non-idempotent option (:default is applied first)
   ["-h" "--help"]])

(defn -main [& args]
  (binding [*print-namespace-maps* false]
    (let [parsed (cli/parse-opts args cli-options)
          opts (:options parsed)
          all-jars (fs/glob (:repo opts) "**.jar")
          index (reduce index-jar {} all-jars)
          index (into (sorted-map) index)]
      (pp/pprint index))))
