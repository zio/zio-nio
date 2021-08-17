package zio.nio.file

import zio.blocking.Blocking
import zio.clock.Clock
import zio.nio.BaseSpec
import zio.nio.core.file.Path
import zio.random.Random
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ Live, TestClock, TestConsole, TestRandom, TestSystem }
import zio.{ Chunk, Has, Ref }

object FilesSpec extends BaseSpec {

  override def spec: Spec[Has[Annotations.Service] with Has[Live.Service] with Has[Sized.Service] with Has[
    TestClock.Service
  ] with Has[TestConfig.Service] with Has[TestConsole.Service] with Has[TestRandom.Service] with Has[
    TestSystem.Service
  ] with Has[Clock.Service] with Has[zio.console.Console.Service] with Has[zio.system.System.Service] with Has[
    Random.Service
  ] with Has[Blocking.Service], TestFailure[Any], TestSuccess] =
    suite("FilesSpec")(
      testM("createTempFileInManaged cleans up temp file") {
        val sampleFileContent = Chunk.fromArray("createTempFileInManaged works!".getBytes)
        for {
          pathRef                 <- Ref.make[Option[Path]](None)
          readBytes               <- Files
                                       .createTempFileInManaged(dir = Path("."))
                                       .use { tmpFile =>
                                         pathRef.set(Some(tmpFile)) *> writeAndThenRead(tmpFile)(sampleFileContent)
                                       }
          Some(tmpFilePath)       <- pathRef.get
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      testM("createTempFileManaged cleans up temp file") {
        val sampleFileContent = Chunk.fromArray("createTempFileManaged works!".getBytes)
        for {
          pathRef                 <- Ref.make[Option[Path]](None)
          readBytes               <- Files
                                       .createTempFileManaged()
                                       .use { tmpFile =>
                                         pathRef.set(Some(tmpFile)) *> writeAndThenRead(tmpFile)(sampleFileContent)
                                       }
          Some(tmpFilePath)       <- pathRef.get
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      testM("createTempDirectoryManaged cleans up temp dir") {
        val sampleFileContent = Chunk.fromArray("createTempDirectoryManaged works!".getBytes)
        for {
          pathRef                 <- Ref.make[Option[Path]](None)
          readBytes               <- Files
                                       .createTempDirectoryManaged(
                                         prefix = None,
                                         fileAttributes = Nil
                                       )
                                       .use { tmpDir =>
                                         val sampleFile = tmpDir / "createTempDirectoryManaged"
                                         pathRef.set(Some(tmpDir)) *> createAndWriteAndThenRead(sampleFile)(sampleFileContent)
                                       }
          Some(tmpFilePath)       <- pathRef.get
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      testM("createTempDirectoryManaged (dir) cleans up temp dir") {
        val sampleFileContent = Chunk.fromArray("createTempDirectoryManaged(dir) works!".getBytes)
        for {
          pathRef                 <- Ref.make[Option[Path]](None)
          readBytes               <- Files
                                       .createTempDirectoryManaged(
                                         dir = Path("."),
                                         prefix = None,
                                         fileAttributes = Nil
                                       )
                                       .use { tmpDir =>
                                         val sampleFile = tmpDir / "createTempDirectoryManaged2"
                                         pathRef.set(Some(tmpDir)) *> createAndWriteAndThenRead(sampleFile)(sampleFileContent)
                                       }
          Some(tmpFilePath)       <- pathRef.get
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      testM("deleteRecursive (dir) deletes directory with subdirectories") {
        for {
          dir         <- Files.createTempDirectory(Path("."), None, Nil)
          subDir       = dir / Path("subDir")
          _           <- Files.createDirectory(subDir)
          _           <- Files.createFile(subDir / Path("subFile"))
          existingDir <- Files.exists(dir)
          _           <- Files.deleteRecursive(dir)
          deletedDir  <- Files.exists(dir)
        } yield assert(existingDir)(isTrue) && assert(deletedDir)(isFalse)
      }
    )

  private def createAndWriteAndThenRead(file: Path)(bytes: Chunk[Byte]) =
    Files.createFile(file) *> writeAndThenRead(file)(bytes)

  private def writeAndThenRead(file: Path)(bytes: Chunk[Byte]) =
    Files.writeBytes(file, bytes) *> Files.readAllBytes(file)
}
