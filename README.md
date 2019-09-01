# ZIO-NIO

[![CircleCI][badge-ci]][link-ci]
[![Gitter][badge-gitter]][link-gitter]

A small, unopinionated ZIO interface to Java NIO.

Java NIO is unsafe, and can surprise you a lot with e.g. hiding actual error in IO operation and giving you only true/false values when IO was successful/not successful. ZIO-NIO on the other hand embraces the full power of ZIO effects, evironment, error and resource management to provide type-safe, performant, purely-functional, low-level, and unopinionated wrapping of Java NIO functionality.

Learn more about ZIO-NIO at:

 - [Homepage](https://zio.github.io/zio-nio/)

## Competition

* Scala standard
  * Cover File and Socket blocking/unblocking IO operations :x:
  * Performant  :white_check_mark:
  * Type-safe, pure FP :x:
  * Scalaz compatibility :x:
* http4s blaze
  * Cover File and Socket blocking/unblocking IO operations :x:
  * Performant :white_check_mark:
  * Type-safe, pure FP :x:
  * Scalaz compatibility :x:
* ammonite-ops
  * Cover File and Socket blocking/unblocking IO operations :x:
  * Performant :white_check_mark:
  * Type-safe, pure FP :x:
  * Scalaz compatibility :x:
* Better files
  * Cover File and Socket blocking/unblocking IO operations :x:
  * Performant :white_check_mark:
  * Type-safe, pure FP :x:
  * Scalaz compatibility :x:

## Background

* [Scala IO](https://www.scala-lang.org/api/2.12.3/scala/io/index.html)
* [Http4s Blaze](https://github.com/http4s/blaze)
* [Ammonite](https://github.com/lihaoyi/Ammonite/)
* [Better Files](https://github.com/pathikrit/better-files)
* [Towards a safe, sane I O library in Scala](https://www.youtube.com/watch?feature=player_embedded&v=uaYKkpqs6CE)
* [Haskell NIO](https://wiki.haskell.org/NIO)
* [Non Blocking IO](https://www.youtube.com/watch?v=uKc0Gx_lPsg)
* [Blocking vs Non-blocking IO](http://tutorials.jenkov.com/java-nio/nio-vs-io.html)

[badge-ci]: https://circleci.com/gh/zio/zio-nio/tree/master.svg?style=svg
[badge-gitter]: https://badges.gitter.im/ZIO/zio-nio.svg
[link-ci]: https://circleci.com/gh/zio/zio-nio/tree/master
[link-gitter]: https://gitter.im/ZIO/zio-nio?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge
