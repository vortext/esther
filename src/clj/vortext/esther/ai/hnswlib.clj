(ns vortext.esther.ai.hnswlib
  (:require
   [babashka.fs :as fs]
   [vortext.esther.util.polyglot :as polyglot]))


(def llvm-bitcode-path
  (str (fs/real-path (fs/path "native/hnswlib/hnswlib_wrapper.bc"))))







(def hnswlib
  (polyglot/create-ctx "llvm" llvm-bitcode-path))


