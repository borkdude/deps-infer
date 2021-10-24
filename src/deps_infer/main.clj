(ns deps-infer.main
  (:require [babashka.fs :as fs]
            [clj-kondo.core :as clj-kondo]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [deps-infer.clojars :refer [index-jar lang]]
            [version-clj.core :as v]))

(def suffix-re #"\.clj$|\.cljs$|\.cljc$") ;; Not including __init\.class$ as this gives false positives!

(defn path->org
  [path]
  (let [path-str (str path)]
    (if (re-find #"[/](?=.*[/])" path-str)
      (-> path-str
        (str/replace #"[/](?=.*[/])" ".")
        symbol)
      path)))

(defn newest [versions snapshots]
  (reduce (fn [acc v]
            (let [parsed (v/parse v)
                  snapshot? (:snapshot? parsed)]
              (if snapshot?
                (if snapshots
                  (if (v/newer? v acc)
                    v acc)
                  acc)
                (if (v/newer? v acc)
                  v acc))))
          versions))

(defn select-deps [source-lang dep-entries]
  (case source-lang
    (:cljs :cljc) dep-entries
    (filter (fn [entry]
              (not= :cljs (lang (:file entry))))
            dep-entries)))

(def cli-options
  ;; An option with a required argument
  [["-r" "--repo M2_PATH" "The m2 repo to index. Defaults to ~/.m2."
    :default (str (io/file (System/getProperty "user.home") ".m2"))]
   ;; A non-idempotent option (:default is applied first)
   [nil "--analyze SOURCES" "The source file(s) to analyze"
    :default "src:test"]
   [nil "--snapshots" "Suggest snapshots"
    :default false]
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
            defined-namespaces (set (map :name (:namespace-definitions analysis)))
            used-namespaces (distinct (map (juxt :to (comp lang :filename))
                                           (:namespace-usages analysis)))
            entries (reduce (fn [acc [n lang]]
                              (if-let [dep-entries (get index n)]
                                (let [dep-entries (select-deps lang dep-entries)]
                                  (into acc dep-entries))
                                (do
                                  (when-not (contains? defined-namespaces n)
                                    (binding [*out* *err*]
                                      (println "WARNING: no dep found for" n)))
                                  acc)))
                            [] used-namespaces)
            grouped (group-by (juxt :group-id :artifact) entries)
            results (reduce (fn [acc [k v]]
                              (assoc acc (symbol (first k) (second k))
                                     {:mvn/version (newest (map :mvn/version v)
                                                           (:snapshots opts))}))
                            (sorted-map)
                            grouped)]
        (doseq [[k v] results]
          (prn k v))))))
          (prn (path->org k) v))))))
