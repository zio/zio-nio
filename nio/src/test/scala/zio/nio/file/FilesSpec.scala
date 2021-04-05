package zio.nio.file

import zio.{ Chunk, Ref }
import zio.nio.BaseSpec
import zio.nio.core.file.Path
import zio.test._
import zio.test.Assertion._

object FilesSpec extends BaseSpec {

  override def spec =
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
      }
    )

  private def createAndWriteAndThenRead(file: Path)(bytes: Chunk[Byte]) =
    Files.createFile(file) *> writeAndThenRead(file)(bytes)

  private def writeAndThenRead(file: Path)(bytes: Chunk[Byte]) =
    Files.writeBytes(file, bytes) *> Files.readAllBytes(file)
}
