(ns funimage.test.imp.calculator
  (:use [funimage imp]
        [funimage.imp calculator statistics]
        [clojure.test]))

(deftest test-subtract-image
  (let [imp1 (ij.IJ/openImage "http://imagej.nih.gov/ij/images/boats.gif")        
        imp2 (imp-subtract imp1 imp1)]
    (is (zero? (:mean (get-image-statistics imp2))))))

(deftest test-create-like-difference
  (let [imp1 (ij.IJ/openImage "http://imagej.nih.gov/ij/images/boats.gif")
        imp2 (create-imp-like imp1)
        imp3 (imp-difference imp1 imp2)]
    (is (zero? (:mean (get-image-statistics imp3))))))
