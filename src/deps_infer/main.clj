(ns deps-infer.main
  (:require [babashka.fs :as fs]
            [clj-kondo.core :as clj-kondo]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [version-clj.core :as v]))

(def suffix-re #"\.clj$|\.cljs$|\.cljc$") ;; Not including __init\.class$ as this gives false positives!

(defn index-jar [index jar]
  (let [jar (str jar)
        jar-file (fs/file jar)]
    (if-not (fs/directory? jar-file)
      (with-open [jar-resource (java.util.jar.JarFile. jar-file)]
        (let [entries (enumeration-seq (.entries jar-resource))]
          (reduce (fn [acc e]
                    (let [n (.getName e)]
                      (if (re-find suffix-re n)
                        (let [n (str/replace n suffix-re "")
                              n (str/replace n "_" "-")
                              n (str/replace n "/" ".")
                              n (symbol n)
                              [_ group-id artifact version _]
                              (re-find #"repository/(.*)/(.*)/(.*)/.*jar" jar)]
                          (update acc n
                                  (fn [deps]
                                    (update deps (symbol (str/replace group-id "/" ".") artifact)
                                            update :mvn/versions (fnil conj []) version))))
                        acc)))
                  index
                  entries)))
      index)))

(defn newest [versions]
  (reduce (fn [acc v]
            (if (v/newer? v acc) v acc))
          versions))

(def cli-options
  ;; An option with a required argument
  [["-r" "--repo M2_PATH" "The m2 repo to index. Defaults to ~/.m2."
    :default (str (io/file (System/getProperty "user.home") ".m2"))]
   ;; A non-idempotent option (:default is applied first)
   ["-s" "--sources SOURCES" "The sources to analyze"
    :default "src:test"]
   ["-h" "--help"]])

(defn -main [& args]
  (binding [*print-namespace-maps* false]
    (let [parsed (cli/parse-opts args cli-options)
          opts (:options parsed)
          index-file (fs/file ".work" "index.edn")]
      (when (not (fs/exists? index-file))
        (binding [*out* *err*]
          (println "Indexing" (:repo opts)))
        (io/make-parents index-file)
        (let [all-jars (fs/glob (:repo opts) "**.jar")
              index (reduce index-jar {} all-jars)
              index (into (sorted-map) index)
              index-str (with-out-str (pp/pprint index))]
          (spit index-file index-str)))
      (let [index (edn/read-string (slurp index-file))
            analysis (:analysis (clj-kondo/run! {:lint [(:sources opts)]
                                                 :config {:output {:analysis true}}}))
            used-namespaces (map :to (:namespace-usages analysis))
            deps-maps (vals (select-keys index used-namespaces))
            deps-map (apply merge deps-maps)
            results (reduce (fn [acc [k v]]
                              (assoc acc k {:mvn/version (newest (:mvn/versions v))}))
                            (sorted-map)
                            deps-map)]
        (doseq [[k v] results]
          (prn k v))))))
