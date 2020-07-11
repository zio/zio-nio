# ZIO-NIO

| CI | Release | Snapshot | Discord |
| --- | --- | --- | --- |
| [![Build Status][badge-ci]][link-ci] | [![Release Artifacts][badge-releases]][link-releases] | [![Snapshot Artifacts][badge-snapshots]][link-snapshots] | [![badge-discord]][link-discord] |

ZIO interface to Java NIO.

Java NIO is unsafe, and can surprise you a lot with e.g. hiding the actual error in IO operation and giving you only true/false values when IO was successful/not successful. ZIO-NIO on the other hand embraces the full power of ZIO effects, environment, error and resource management to provide type-safe, performant, purely-functional, low-level, and unopinionated wrapping of Java NIO functionality.

ZIO-NIO comes in two flavours:

 - `zio.nio.core` - a small and unopinionated ZIO interface to NIO that just wraps NIO API in ZIO effects,
 - `zio.nio` - an opinionated interface with deeper ZIO integration that provides more type and resource safety.

Learn more about ZIO-NIO at:

 - [Homepage](https://zio.github.io/zio-nio/)

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
[badge-discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"
[badge-releases]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-nio_2.12.svg "Sonatype Releases"
[badge-snapshots]: https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-nio_2.12.svg "Sonatype Snapshots"
[link-ci]: https://circleci.com/gh/zio/zio-nio/tree/master
[link-discord]: https://discord.gg/2ccFBr4 "Discord"
[link-releases]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-nio_2.12/ "Sonatype Releases"
[link-snapshots]: https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-nio_2.12/ "Sonatype Snapshots"
