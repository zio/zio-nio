package zio.nio.file

import zio.Clock
import zio.nio.BaseSpec
import zio.Random
import zio.test.Assertion._
import zio.test._
import zio.{Chunk, Ref}

object FilesSpec extends BaseSpec {

  override def spec: Spec[
    Annotations with Live with Sized with TestClock with TestConfig with TestConsole with TestRandom with TestSystem with Clock with zio.Console with zio.System with Random,
    TestFailure[Any],
    TestSuccess
  ] =
    suite("FilesSpec")(
      test("createTempFileInManaged cleans up temp file") {
        val sampleFileContent = Chunk.fromArray("createTempFileInManaged works!".getBytes)
        for {
          pathRef <- Ref.make[Option[Path]](None)
          readBytes <- Files
                         .createTempFileInManaged(dir = Path("."))
                         .use { tmpFile =>
                           pathRef.set(Some(tmpFile)) *> writeAndThenRead(tmpFile)(sampleFileContent)
                         }
          Some(tmpFilePath)       <- pathRef.get
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      test("createTempFileManaged cleans up temp file") {
        val sampleFileContent = Chunk.fromArray("createTempFileManaged works!".getBytes)
        for {
          pathRef <- Ref.make[Option[Path]](None)
          readBytes <- Files
                         .createTempFileManaged()
                         .use { tmpFile =>
                           pathRef.set(Some(tmpFile)) *> writeAndThenRead(tmpFile)(sampleFileContent)
                         }
          Some(tmpFilePath)       <- pathRef.get
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      test("createTempDirectoryManaged cleans up temp dir") {
        val sampleFileContent = Chunk.fromArray("createTempDirectoryManaged works!".getBytes)
        for {
          pathRef <- Ref.make[Option[Path]](None)
          readBytes <- Files
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
      test("createTempDirectoryManaged (dir) cleans up temp dir") {
        val sampleFileContent = Chunk.fromArray("createTempDirectoryManaged(dir) works!".getBytes)
        for {
          pathRef <- Ref.make[Option[Path]](None)
          readBytes <- Files
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
      }
    )

  private def createAndWriteAndThenRead(file: Path)(bytes: Chunk[Byte]) =
    Files.createFile(file) *> writeAndThenRead(file)(bytes)

  private def writeAndThenRead(file: Path)(bytes: Chunk[Byte]) =
    Files.writeBytes(file, bytes) *> Files.readAllBytes(file)
}
