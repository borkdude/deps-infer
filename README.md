# deps-infer

Infer mvn deps from sources.

## Status

Proof of concept, subject to change. Improvements welcome!

## Use cases

This tool can be used to:

- Make implicit deps explicit
- Check which deps you are _not_ using by comparing the output with your existing `deps.edn`
- Port a `lein` `project.clj` or `boot` `build.boot` to `deps.edn`
- Port scripts (e.g. [babashka](https://github.com/babashka/babashka)
scripts) to a `deps.edn` project.
- Migrate from git deps to mvn deps
- Check if there is a newer version available

## Run

``` clojure
$ clojure -M -m deps-infer.main
```

This will index your `.m2/repository` and will analyze your sources under `src`
and `test`.

By default it will write the analysis output on a `.deps-infer/` inside the `--repo`
directory. You can change the location using the `--cache-dir` option.

After that it will suggest a list of dependencies that you can add to your
`deps.edn`. It will always pick the newest version that is available in your .m2
repo.

For this project it will print:

``` clojure
babashka/fs {:mvn/version "0.0.1"}
clj-kondo/clj-kondo {:mvn/version "2021.02.13"}
org.clojure/clojure {:mvn/version "1.10.3-rc1"}
org.clojure/tools.cli {:mvn/version "1.0.194"}
version-clj/version-clj {:mvn/version "2.0.1"}
```

Namespaces that cannot be resolved will cause a warning to be printed:

``` clojure
$ cat corpus/non_existing.clj
(ns non-existing
  (:require [foo.bar :as x]))
```

```
$ clojure -M -m deps-infer.main --analyze corpus/non_existing.clj
WARNING: no dep found for foo.bar
```

## CLI options

- `--repo`: The location of the mvn repo.
- `--analyze`: The file, directory or directories of sources to analyze. You can
combine multiple files and directories using the OS-specific path separator:
`src:test`.
- `--snapshots`: Suggest `SNAPSHOT` versions.

## Possible improvements

PRs welcome.

- [ ] Download an index of all of Clojars for inferencing of deps that are not
      in your local `.m2/repository`. This index must be kept up to date,
      e.g. daily, and be committed to some git repo where we can then fetch it.

## Troubleshooting

To re-index your repo, remove the index of namespaces to jars, run:

``` bash
$ rm -rf .work/index.edn
```

and then run this tool again.

## Credits

- [hiredman](https://gist.github.com/hiredman/15186e238dc365fd72e2e09c3eb7561a)
for coming up with the idea.

## License

Copyright © 2021 Michiel Borkent

Distributed under the EPL License. See LICENSE.
