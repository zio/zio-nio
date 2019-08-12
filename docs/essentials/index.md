---
id: essentials_index
title:  "Overview"
---

ZIO-NIO is a small, unopinionated ZIO interface to NIO. 

 - **[File Channel](files.md)** — For processing files that are available locally. For every operation a new fiber is started to perform operation
 - **[Socket Channel](sockets.md)** — Provides API for remote communication with `InetSocket`s 
 
## So what is NIO once again? 

As standard input-output processing we are used to stream-like operations. 
Here we are given buffer-oriented processing where read input is stored in buffer which we can traverse multiple times.

Another but even more important is `NIO` being non-blocking.
Non-blocking IO ensures that a thread won't be hanging on some IO operation, but instead the idle time will spend on some other IO.
This solution gives possibility to service multiple channels with single thread. 

As already mentioned `NIO` is using channels that can be processed by single thread. To perform this `NIO` provides 
selectors for monitoring multiple channels by threads.

## Installation

`ZIO-NIO` is available via maven repo so import in `build.sbt` is sufficient:

```scala
libraryDependencies += "dev.zio" %% "zio-nio" % "0.1.1"
```

## References

 - [ZIO github page](http://github.com/zio/zio)
 - [Java NIO wikipedia](https://en.wikipedia.org/wiki/Non-blocking_I/O_(Java))
