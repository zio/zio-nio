package zio.nio.channels

import zio.test._
import zio.test.Assertion._

import zio.nio.BaseSpec
import zio.IO

import zio.ZIO
import zio.Chunk
import zio.Promise

import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.KeyManagerFactory
import zio.blocking._
import javax.net.ssl.SSLContext

import zio.nio.SocketAddress

import java.io.FileInputStream
import java.io.FileOutputStream

//import zio.duration.Duration
//import java.util.concurrent.TimeUnit

object TlsChannelSpec
    extends BaseSpec(
      suite("TlsChannelSpec")(
        testM("TLS handshake, TLS channel read/write tests") {

          val INFILE   = "src/test/resources/ZIO.png"
          val OUTFILE1 = "src/test/resources/ZIO2.png"

          def buildSSLContext(
            protocol: String,
            JKSkeystore: String,
            password: String
          ): ZIO[Blocking, Exception, SSLContext] = {

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

          def writeFile(c: AsynchronousTlsByteChannel, chunkSize: Int, path: String) =
            for {

              buf <- IO.effectTotal(new Array[Byte](chunkSize))
              fp  <- effectBlocking(new FileInputStream(path))
              loop = effectBlocking(fp.read(buf)).flatMap { nBytes =>
                {
                  if (nBytes > 0) { //server and client are not syncronized, we allocate more memory on receiving end, delay here is not neccessary, just in case.
                    c.write(Chunk.fromArray(buf).take(nBytes)) *> IO.succeed(nBytes)
                  } else IO.succeed(nBytes)
                }
              }

              _ <- loop.repeat(zio.Schedule.doWhile(_ > 0))

              _ <- IO.effect(fp.close())

            } yield ()

          def readAndSafeFile(c: AsynchronousTlsByteChannel, maxChunkSize: Int, path: String) =
            for {
              fp <- effectBlocking(new FileOutputStream(path))

              loop = c
                .read(maxChunkSize)
                .flatMap { chunk =>
                  {
                    if ( chunk.isEmpty == false ) effectBlocking(fp.write(chunk.toArray)) *> IO.succeed(1)
                    else IO.succeed( 1 )
                  }
                }
                .catchAll(e => { println(e.toString); IO.succeed(0) }) //no content size - just timeout when over

              _ <- loop.repeat(zio.Schedule.doWhile(_ > 0))

              _ <- effectBlocking(fp.close())

              _ <- IO.effect(println("CLIENT: Saved: " + path))

            } yield ()

          def server(
            CHUNK_SZ: Int,
            filePath: String,
            clientDone: Promise[Exception, Boolean],
            serverReady: Promise[Exception, Boolean]
          ) =
            for {

              ssl_context <- buildSSLContext("TLS", "src/test/resources/keystore.jks", "password")
              address     <- SocketAddress.inetSocketAddress("127.0.0.1", 8090)

              _ <- AsynchronousServerSocketChannel().use {
                    server =>
                      for {
                        _ <- server.bind(address)
                        _ <- serverReady.succeed(true)
                        _ <- server.accept.use { worker =>
                              {
                                for {
                                  _ <- AsynchronousServerTlsByteChannel(worker, ssl_context).use { tls_socket =>
                                        for {
                                          _           <- writeFile(tls_socket, CHUNK_SZ, filePath)
                                          ssl_session <- tls_socket.getSession
                                          _ <- IO.effect(
                                                println(
                                                  "SERVER1: " + ssl_session.getProtocol + ", " + ssl_session.getCipherSuite
                                                )
                                              )
                                          _ <- clientDone.await
                                        } yield ()
                                      }
                                } yield ()
                              }
                            }

                      } yield ()
                  }
            } yield ()

          def client(CHUNK_SZ: Int, filePath: String, clientDone: Promise[Exception, Boolean]) =
            for {
              ssl_context <- buildSSLContext("TLS", "src/test/resources/keystore.jks", "password")
              address     <- SocketAddress.inetSocketAddress("127.0.0.1", 8090)
              chunk <- AsynchronousSocketChannel().use { client =>
                        for {
                          _ <- client.connect(address)

                          _ <- AsynchronousTlsByteChannel(client, ssl_context).use { client =>
                                IO.effect(client.keepAlive(200)) *> readAndSafeFile(client, CHUNK_SZ * 500, filePath) *> clientDone
                                  .succeed(true)

                              }
                          //socket write calls is a bit ahead or read, make sure we have enough memory stack up on them to read.
                        } yield ()

                      }
            } yield (chunk)

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
          // nice to have two separate tests: with 88096 and small 4096 byte
          //not sure how to do that without code duplication
          val r = for {

            serverReady <- Promise.make[Exception, Boolean]
            clientDone  <- Promise.make[Exception, Boolean]

            fib1 <- server(88096, INFILE, clientDone, serverReady).fork

            _ <- serverReady.await

            fib2 <- client(88096, OUTFILE1, clientDone).fork

            _ <- fib1.join
            _ <- fib2.join

            same <- effectBlocking(compareFiles(INFILE, OUTFILE1))

            _ <- IO.effect(new java.io.File(OUTFILE1).delete())

          } yield (assert(same, isTrue))

          val r1 = for {

            serverReady <- Promise.make[Exception, Boolean]
            clientDone  <- Promise.make[Exception, Boolean]

            fib1 <- server(4096, INFILE, clientDone, serverReady).fork

            _ <- serverReady.await

            fib2 <- client(4096, OUTFILE1, clientDone).fork

            _ <- fib1.join
            _ <- fib2.join

            same <- effectBlocking(compareFiles(INFILE, OUTFILE1))

            _ <- IO.effect(new java.io.File(OUTFILE1).delete())

          } yield (assert(same, isTrue))

          r *> r1

        }
      )
    )
