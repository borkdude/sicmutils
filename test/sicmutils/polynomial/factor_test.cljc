;;
;; Copyright © 2017 Colin Smith.
;; This work is based on the Scmutils system of MIT/GNU Scheme:
;; Copyright © 2002 Massachusetts Institute of Technology
;;
;; This is free software;  you can redistribute it and/or modify
;; it under the terms of the GNU General Public License as published by
;; the Free Software Foundation; either version 3 of the License, or (at
;; your option) any later version.
;;
;; This software is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; General Public License for more details.
;;
;; You should have received a copy of the GNU General Public License
;; along with this code; if not, see <http://www.gnu.org/licenses/>.
;;

(ns sicmutils.polynomial.factor-test
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [sicmutils.abstract.number]
            [sicmutils.expression :refer [expression-of]]
            [sicmutils.expression.analyze :as a]
            [sicmutils.generic :as g]
            [sicmutils.numbers]
            [sicmutils.polynomial :as p]
            [sicmutils.polynomial.factor :as pf]
            [sicmutils.simplify
             :refer [hermetic-simplify-fixture simplify-expression]]
            [sicmutils.value :as v]))

(use-fixtures :each hermetic-simplify-fixture)

(defn ->poly [x]
  (a/expression-> p/analyzer
                  (expression-of x)
                  (fn [p _] p)))

(deftest factoring
  (testing "simple test cases"
    (let [x-y (->poly '(- x y))
          x+y (->poly '(+ x y))]
      (is (= [1 1 x-y x+y]
             (pf/split-polynomial
              (->poly
               '(* (square (- x y))
                   (cube (+ x y)))))))

      (is (= '(* (expt (+ x (* -1 y)) 2)
                 (expt (+ x y) 3))
             (pf/factor-expression
              (g/* (g/square (g/- 'x 'y))
                   (g/cube (g/+ 'x 'y))))))

      (is (= '(expt (+ x (* -1 y)) 2)
             (pf/factor-expression
              (g/square (g/- 'x 'y)))))

      (is (= '(* 3 (+ (expt x 2) y) (expt z 3))
             (pf/factor-expression
              (g/* 3 (g/cube 'z) (g/+ (g/square 'x) 'y)))))

      (is (= '(* 3 (+ (expt x 2) y) (expt z 2))
             (pf/factor-expression
              (g/* 3 (g/square 'z) (g/+ (g/square 'x) 'y))))))))

(deftest factoring-2
  (testing "test poly - first example from split-poly.scm"
    (let [z (g/square (g/+ 'x (g/* 'x (g/expt 'y 2))))
          test-poly (g/simplify
                     (g/* (g/expt (g/+ (g/cos z) 'y) 2)
                          (g/expt (g/- (g/cos z) 'y) 3)))]
      (is (= '(* -1
                 (expt (+ y (cos (expt (+ (* x (expt y 2)) x) 2))) 2)
                 (expt (+ y (* -1 (cos (expt (+ (* x (expt y 2)) x) 2)))) 3))
             (pf/factor
              (v/freeze test-poly))))))

  (testing "factoring works on literals"
    (let [expr (g// (g/square (g/+ 'x 'y))
                    (g/square (g/+ 'x 'z)))]
      (is (= '(/ (expt (+ x y) 2)
                 (expt (+ x z) 2))
             (v/freeze expr))
          "unfactored before simplification.")

      (is (= '(/ (+ (expt x 2) (* 2 x y) (expt y 2))
                 (+ (expt x 2) (* 2 x z) (expt z 2)))
             (v/freeze
              (g/simplify expr)))
          "simplification expands by default.")

      (is (= '(/ (expt (+ x y) 2)
                 (expt (+ x z) 2))
             (v/freeze
              (pf/factor
               (g/simplify expr))))
          "calling factor re-factors the expression!"))))

(deftest root-out-squares-test
  (testing "one step"
    (is (= '(+ x (* -1 y))
           (pf/root-out-squares
            '(sqrt (square (- x y))))))

    (is (= '(sqrt (expt (+ x (* -1 y)) 3))
           (pf/root-out-squares
            '(sqrt (cube (- x y))))))

    (is (= '(expt (+ x (* -1 y)) 2)
           (pf/root-out-squares
            '(sqrt (expt (- x y) 4)))))

    (is (= '(* (sqrt (expt (+ x (* -1 y)) 3)) (+ x y))
           (pf/root-out-squares
            '(sqrt (* (square (+ x y)) (cube (- x y)))))))

    (is (= '(+ a b c)
           (pf/root-out-squares
            '(sqrt (+ (expt a 2) (* 2 a b) (* b b) (* 2 a c) (* 2 b c) (expt c 2))))))

    (is (= '(+ a b c d)
           (pf/root-out-squares
            '(sqrt (+ (expt a 2) (* 2 a b) (* b b) (* 2 a c) (* 2 b c) (expt c 2)
                      (* 2 a d) (* b d) (* b d) (* 2 c d) (* d d))))))

    (is (= '(+ (expt c 2) a b d)
           (pf/root-out-squares
            '(sqrt (+ (expt a 2) (* 2 a b) (* b b) (* 2 a c c) (* 2 b c c)
                      (expt c 4) (* 2 a d) (* b d) (* b d) (* 2 c c d) (* d d)))))))

  (testing "Second example from split-poly.scm"
    (is (= '(+ (* (expt x 2) (sqrt (+ x (* -1 y))))
               (* -1 (expt y 2) (sqrt (+ x (* -1 y)))))
           (simplify-expression
            '(sqrt (* (square (+ x y))
                      (cube (- x y))))))
        "This example exists in `split-poly.scm`, but uses the full simplifier,
        so doesn't actually check what `root-out-squares` is up to.")

    (is (= '(* (sqrt (expt (+ x (* -1 y)) 3)) (+ x y))
           (pf/root-out-squares
            '(sqrt (* (square (+ x y))
                      (cube (- x y))))))
        "This is the actual `root-out-squares` contribution, and matches
        scmutils.")))
