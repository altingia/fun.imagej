(ns fun.imagej.ops
  (:require [fun.imagej.core :as ij]
            [clojure.string :as string]))

(defn make-typehint
  "Make the typehint for a command input's type."
  [input-type]
  (if-not input-type
    ""
    (let [type-string (.toString input-type)
          parts (string/split type-string #" ")]
      (if (= 1 (count parts))
        ""
        (let [classname (second parts)]
          (cond (= classname "[J") "^longs"
                (= classname "[I") "^ints"
                (= classname "[D") "^doubles"
                (.startsWith classname "[[") ""
                :else
                (str "^" classname)))))))

(defn guess-type
  "Guess the type of a command input's type."
  [input-type]
  
  (if-not input-type
    ""
    (let [type-string (.toString input-type)
          parts (string/split type-string #" ")]
      (if (= 1 (count parts))
        ""
        (let [classname (second parts)]
          (cond (= classname "[J") "longs"
                (= classname "[I") "ints"
                (= classname "[D") "doubles"
                (.startsWith classname "[[") ""
                :else
                (str classname)))))))

(def infos-ignore #{"eval" "help" "identity" "info" "infos" "join" "loop" "map" "module" "op" "ops" "parent" "namespace" "run" "slice"})

(def valid-ops (into #{} (.ops (.op ij/ij))))

(def ops-namespaces (atom []))

(def op-list
  (doall
    (for [[op-name op-infos] (group-by #(.getName %)
                                       (filter #(valid-ops (.getName %))
                                               (filter #(not (infos-ignore (.getName %)))
                                                       (.infos (.op ij/ij)))))]
      (let [op-info (first op-infos)
            op-expression "(.op ij/ij)"
            parts (string/split (.getName op-info) #"\.")
            cinfo (.cInfo op-info)
            op-namespace (symbol (string/join
                                   "."
                                   (concat [(ns-name *ns*)]
                                         (butlast (string/split op-name #"\.")))))
            function-name (last (string/split op-name #"\."))     ; We should adjust the function signature based on whether it is a function or computer

            fn-defs (doall
                      (for [op-info op-infos]
                        (let [cinfo (.cInfo op-info)                            
                              required-inputs (filter #(.isRequired %) (seq (.inputs cinfo)))
                              optional-inputs (filter #(not (.isRequired %)) (seq (.inputs cinfo)))
                              arg-list (string/join
                                         " "
                                         (map #(str (let [tpe (guess-type (.getType %)) ]
                                                      (if (empty? tpe)
                                                        ""
                                                        (str "^"tpe))) 
                                                    " " (.getName %))
                                              required-inputs))
                              args-to-pass (string/join
                                             " "
                                             (map #(.getName %)
                                                  required-inputs))]
                          [(count required-inputs)
                           (with-out-str
                             (println "([" arg-list "]")
                             (println (str "\t(." (second parts) " (." (first parts) " " op-expression ") " args-to-pass "))")))])))
            arity-map (apply hash-map (flatten fn-defs))
            expr (read-string
                   (str "(fn " (string/join " " (vals arity-map)) ")"))
            doc-string (str (.getTitle cinfo) "\n"
                            "Number of inputs: " (string/join " " (map #(str %) (keys arity-map))))]
        ; Test if the NS exists, if it doesn't then make it          
        (when-not (try (ns-name op-namespace) (catch Exception e nil))
          (create-ns op-namespace)
          (swap! ops-namespaces conj op-namespace))
        ; Make the function and load it into the respective namespace
        (intern (the-ns op-namespace)
                (symbol function-name)
                (let [this-fn (eval expr)]
                  (with-meta this-fn 
                    (assoc (meta this-fn)
                      :doc doc-string))))
        ; This doesnt need to be captured anymore
        {:function-name function-name
         :expression expr
         :namespace op-namespace
         }))))

(def + 
  ((fn [x]
     x)
    (fn [x y]
      (fun.imagej.ops.math/add x y))))
(def - 
  (fn [x y]
    (fun.imagej.ops.math/subtract x y)))
;(fn [x] (fun.imagej.ops.math/negate x) ; should figure this out for -
(def * fun.imagej.ops.math/multiply)
(def / fun.imagej.ops.math/divide)
(def or fun.imagej.ops.logic/or)
(def and fun.imagej.ops.logic/and)
(def > fun.imagej.ops.logic/greaterThan)
(def < fun.imagej.ops.logic/lessThan)
(def = fun.imagej.ops.logic/equal)
(def not= fun.imagej.ops.logic/notEqual)
(def >= fun.imagej.ops.logic/greaterThanOrEqual)
(def <= fun.imagej.ops.logic/lessThanOrEqual)

(defn run-op
  "Run an op using its string name and an array of args that will
  be converted into an object array"
  [op-name args]
  (.run (.op ij/ij) op-name (into-array Object args)))
