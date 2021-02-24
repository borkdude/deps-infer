# deps-infer

Infer mvn deps from sources.

## Status

Proof of concept, subject to change.

## Run

``` clojure
$ clojure -M deps-infer.main
```

This will index your `.m2/repository` and will analyze your sources under `src`
and `test`. The source path is configurable using the `--sources` argument.

After that it will suggest a list of dependencies that you can add to your
`deps.edn`. For this project it will print:

``` clojure
babashka/fs {:mvn/version "0.0.1"}
clj-kondo/clj-kondo {:mvn/version "2021.02.14-SNAPSHOT"}
org.clojure/clojure {:mvn/version "1.10.3-rc1"}
org.clojure/clojurescript {:mvn/version "1.10.773"}
org.clojure/tools.cli {:mvn/version "1.0.194"}
version-clj/version-clj {:mvn/version "2.0.1"}
```

## Troubleshooting

To re-index your repo, remove the index of namespaces to jars, run:

``` bash
$ rm -rf .work/index.edn
```

and then run this tool again.

## License

Copyright © 2021 Michiel Borkent

Distributed under the EPL License. See LICENSE.
