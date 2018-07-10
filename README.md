# scalaz-nio

## Goal

Scalaz NIO provides performant, purely-functional, low-level, and unopinionated wrapping of Java NIO functionality.

## Introduction & Highlights

Java NIO is unsafe, and can surprise you a lot with e.g. hiding actual error in IO operation and giving you only true/false values - IO was seccessful/not successful.

## Competition

* Scala IO
  * Cover File and Socket blocking/unblocking IO operations :negative_squared_cross_mark:
  * Performant  :white_check_mark:
  * Type-safe, pure FP :negative_squared_cross_mark:
  * Scalaz compatibility :negative_squared_cross_mark:
* http4s blaze
  * Cover File and Socket blocking/unblocking IO operations :negative_squared_cross_mark:
  * Performant :white_check_mark:
  * Type-safe, pure FP :negative_squared_cross_mark:
  * Scalaz compatibility :negative_squared_cross_mark:
* Better files
  * Cover File and Socket blocking/unblocking IO operations :negative_squared_cross_mark:
  * Performant :white_check_mark:
  * Type-safe, pure FP :negative_squared_cross_mark:
  * Scalaz compatibility :negative_squared_cross_mark:

## Background

* [Scala IO](https://www.scala-lang.org/api/2.12.3/scala/io/index.html)
* [Http4s Blaze](https://github.com/http4s/blaze)
* [Better Files](https://github.com/pathikrit/better-files)
  * [Towards a safe, sane I O library in Scala](https://www.youtube.com/watch?feature=player_embedded&v=uaYKkpqs6CE)
* [Haskell NIO](https://wiki.haskell.org/NIO)