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
                      (if (re-find suffix-re raw-name)
                        (let [n (str/replace raw-name suffix-re "")
                              n (str/replace n "_" "-")
                              n (str/replace n "/" ".")
                              n (symbol n)
                              [_ group-id artifact version _]
                              (re-find #"repository/(.*)/(.*)/(.*)/.*jar" jar)]
                          (update acc n
                                  (fn [deps]
                                    (update deps (symbol (str/replace group-id "/" ".") artifact)
                                            (fn [dep-info]
                                              (-> dep-info
                                                  (update :mvn/versions (fnil conj []) version)
                                                  (update :langs (fnil conj #{}) (lang raw-name))))))))
                        acc)))
                  index
                  entries)))
      index)))

(defn newest [versions]
  (reduce (fn [acc v]
            (if (v/newer? v acc) v acc))
          versions))

(defn select-deps [source-lang deps]
  (case source-lang
    (:cljs :cljc) deps
    (reduce (fn [acc [k v]]
              (let [langs (:langs v)
                    match? (or (contains? langs :clj)
                               (contains? langs :cljc))]
                (if match?
                  (assoc acc k v)
                  acc)))
            deps)))

(def cli-options
  ;; An option with a required argument
  [["-r" "--repo M2_PATH" "The m2 repo to index. Defaults to ~/.m2."
    :default (str (io/file (System/getProperty "user.home") ".m2"))]
   ;; A non-idempotent option (:default is applied first)
   [nil "--analyze SOURCES" "The source file(s) to analyze"
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
            analysis (:analysis (clj-kondo/run! {:lint [(:analyze opts)]
                                                 :config {:output {:analysis true}}}))
            used-namespaces (distinct (map (juxt :to (comp lang :filename)) (:namespace-usages analysis)))
            deps-map (reduce (fn [acc [n lang]]
                               (if-let [deps-map (get index n)]
                                 (let [deps-map (select-deps lang deps-map)]
                                   (merge acc deps-map))
                                 (do
                                   (binding [*out* *err*]
                                     (println "WARNING: no dep found for" n))
                                   acc))) {} used-namespaces)
            results (reduce (fn [acc [k v]]
                              (assoc acc k {:mvn/version (newest (:mvn/versions v))}))
                            (sorted-map)
                            deps-map)]
        (doseq [[k v] results]
          (prn k v))))))
