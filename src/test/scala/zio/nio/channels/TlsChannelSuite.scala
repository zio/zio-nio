package zio.nio.channels

import testz.{ Harness, assert }
//import zio.nio.{ Buffer, InetAddress, SocketAddress }
import zio.DefaultRuntime
import zio.ZIO
import zio.Chunk
import zio.IO

import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.KeyManagerFactory
import zio.blocking._
import javax.net.ssl.SSLContext

import zio.nio.SocketAddress

import java.io.FileInputStream
import java.io.FileOutputStream

import zio.duration.Duration
import java.util.concurrent.TimeUnit

object TlsChannelSuite extends DefaultRuntime {

  def tests[T](harness: Harness[T]): T = {

    //Test copies and compares png file with different buffers sizes
    //current implementation has no limits on data size processed by read() and write()
    val INFILE   = "src/test/resources/ZIO.png"
    val OUTFILE1 = "src/test/resources/ZIO2.png"
    val OUTFILE2 = "src/test/resources/ZIO3.png"
    // ^
    //after test run: all 3 files are exactly tge same.

    import harness._
    section(test("TLS handshake, TLS channel read/write tests") { () =>
      ////////////////////
      def server(CHUNK_SZ: Int, filePath: String) =
        for {
          ssl_context <- buildSSLContext("TLS", "src/test/resources/keystore.jks", "password")
          address     <- SocketAddress.inetSocketAddress("127.0.0.1", 8090)

          _ <- AsynchronousServerSocketChannel().use { server =>
                for {
                  _ <- server.bind(address)
                  _ <- server.accept.use { worker =>
                        {
                          for {
                            tls_worker  <- AsynchronousServerTlsByteChannel(worker, ssl_context)
                            _           <- writeFile(tls_worker, CHUNK_SZ, filePath)
                            ssl_session <- tls_worker.getSession
                            _ <- IO.effect(
                                  println("SERVER: " + ssl_session.getProtocol + ", " + ssl_session.getCipherSuite)
                                )

                          } yield ()
                        }
                      }

                } yield ()

              }

        } yield ()

      ////////////////////
      def client(CHUNK_SZ: Int, filePath: String) =
        for {
          ssl_context <- buildSSLContext("TLS", "src/test/resources/keystore.jks", "password")
          address     <- SocketAddress.inetSocketAddress("127.0.0.1", 8090)
          chunk <- AsynchronousSocketChannel().use { client =>
                    for {
                      _ <- client.connect(address)

                      tls_c <- AsynchronousTlsByteChannel(client, ssl_context)
                      //socket write calls is a bit ahead or read, make sure we have enough memory stack up on them to read.
                      _ <- readAndSafeFile(tls_c, CHUNK_SZ * 100, filePath)

                      _ <- client.close

                    } yield ()

                  }
        } yield (chunk)

      ///////////////////
      def buildSSLContext(
        protocol: String,
        JKSkeystore: String,
        password: String
      ): ZIO[Blocking, Exception, SSLContext] = {

        //resource close - TODO

        val test = effectBlocking {

          val sslContext: SSLContext = SSLContext.getInstance(protocol)

          val keyStore: KeyStore = KeyStore.getInstance("JKS")

          val ks = new java.io.FileInputStream(JKSkeystore)

          if (ks == null) IO.fail(new java.io.FileNotFoundException(JKSkeystore + " keystore file not found."))

          keyStore.load(ks, password.toCharArray())

          val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
          tmf.init(keyStore)

          val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
          kmf.init(keyStore, password.toCharArray())
          sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
          sslContext
        }

        test.refineToOrDie[Exception]

      }

      //////////////////////////////////
      def writeFile(c: AsynchronousTlsByteChannel, chunkSize: Int, path: String) =
        for {

          buf <- IO.effectTotal(new Array[Byte](chunkSize))
          _   <- ZIO.sleep(Duration(2000, TimeUnit.MILLISECONDS))

          fp <- effectBlocking(new FileInputStream(path))
          loop = effectBlocking(fp.read(buf)).flatMap { nBytes =>
            {
              if (nBytes > 0) { //server and client are not syncronized, we allocate more memory on receiving end, delay here is not neccessary, just in case.
                c.write(Chunk.fromArray(buf).take(nBytes)) *> ZIO.sleep(Duration(80, TimeUnit.MILLISECONDS)) *> IO
                  .succeed(nBytes)
              } else IO.succeed(nBytes)
            }
          }

          _ <- loop.repeat(zio.Schedule.doWhile(_ > 0))

          _ <- IO.effect(fp.close())

        } yield ()

      //////////////////////////////////////
      def readAndSafeFile(c: AsynchronousTlsByteChannel, maxChunkSize: Int, path: String) =
        for {
          fp <- effectBlocking(new FileOutputStream(path))

          loop = c
            .read(maxChunkSize)
            .flatMap { chunk =>
              {
                effectBlocking(fp.write(chunk.toArray)) *> IO.succeed(1)
              }
            }
            .catchAll(_ => { /*println(e.toString);*/ IO.succeed(0) }) //no content size - just timeout when over

          _ <- loop.repeat(zio.Schedule.doWhile(_ > 0))

          _ <- IO.effect(println("CLIENT: Saved: " + path))

        } yield ()

      //////////////////////////////////
      def compareFiles(path1: String, path2: String): Boolean = {
        val f1 = new FileInputStream(path1)
        val f2 = new FileInputStream(path2)

        var result = false;
        var b1     = -1;
        var b2     = -1;

        do {
          b1 = f1.read
          b2 = f2.read
          if (b1 == b2) result = true
          else result = false;
        } while ((b1 != -1 && b2 != -1 && result == true))

        f1.close
        f2.close

        result
      }

      //////////////////////////////////
      //more then TLS packet size
      def testProgram_bigBuffer = {
        val r = for {

          fib1 <- server(88096, INFILE).fork
          fib2 <- client(88096, OUTFILE1).fork

          _ <- fib1.join
          _ <- fib2.join

        } yield (true)

        r *> effectBlocking(compareFiles(INFILE, OUTFILE1))

      }

      //////////////////////////////////
      //less then TLS packet size
      def testProgram_smallBuffer = {
        val r = for {

          fib1 <- server(4096, INFILE).fork
          fib2 <- client(4096, OUTFILE2).fork

          _ <- fib1.join
          _ <- fib2.join

        } yield (true)

        r *> effectBlocking(compareFiles(INFILE, OUTFILE2))

      }

      new java.io.File(OUTFILE1).delete()
      new java.io.File(OUTFILE2).delete()

      assert(unsafeRun(testProgram_smallBuffer))
      assert(unsafeRun(testProgram_bigBuffer))

    })

  }
}
