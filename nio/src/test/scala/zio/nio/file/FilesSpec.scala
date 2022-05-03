package zio.nio.file

import zio.nio.BaseSpec
import zio.test.Assertion._
import zio.test._
import zio.{Chunk, Ref, ZIO}

object FilesSpec extends BaseSpec {

  override def spec: Spec[Any, Any] =
    suite("FilesSpec")(
      test("createTempFileInScoped cleans up temp file") {
        val sampleFileContent = Chunk.fromArray("createTempFileInScoped works!".getBytes)
        for {
          pathRef <- Ref.make[Option[Path]](None)
          readBytes <- ZIO.scoped {
                         Files
                           .createTempFileInScoped(dir = Path("."))
                           .flatMap { tmpFile =>
                             pathRef.set(Some(tmpFile)) *> writeAndThenRead(tmpFile)(sampleFileContent)
                           }
                       }
          tmpFilePath             <- pathRef.get.some
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      test("createTempFileScoped cleans up temp file") {
        val sampleFileContent = Chunk.fromArray("createTempFileScoped works!".getBytes)
        for {
          pathRef <- Ref.make[Option[Path]](None)
          readBytes <- ZIO.scoped {
                         Files
                           .createTempFileScoped()
                           .flatMap { tmpFile =>
                             pathRef.set(Some(tmpFile)) *> writeAndThenRead(tmpFile)(sampleFileContent)
                           }
                       }
          tmpFilePath             <- pathRef.get.some
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      test("createTempDirectoryScoped cleans up temp dir") {
        val sampleFileContent = Chunk.fromArray("createTempDirectoryScoped works!".getBytes)
        for {
          pathRef <- Ref.make[Option[Path]](None)
          readBytes <- ZIO.scoped {
                         Files
                           .createTempDirectoryScoped(
                             prefix = None,
                             fileAttributes = Nil
                           )
                           .flatMap { tmpDir =>
                             val sampleFile = tmpDir / "createTempDirectoryScoped"
                             pathRef.set(Some(tmpDir)) *> createAndWriteAndThenRead(sampleFile)(sampleFileContent)
                           }
                       }
          tmpFilePath             <- pathRef.get.some
          tmpFileExistsAfterUsage <- Files.exists(tmpFilePath)
        } yield assert(readBytes)(equalTo(sampleFileContent)) &&
          assert(tmpFileExistsAfterUsage)(isFalse)
      },
      test("createTempDirectoryscoped (dir) cleans up temp dir") {
        val sampleFileContent = Chunk.fromArray("createTempDirectoryScoped(dir) works!".getBytes)
        for {
          pathRef <- Ref.make[Option[Path]](None)
          readBytes <- ZIO.scoped {
                         Files
                           .createTempDirectoryScoped(
                             dir = Path("."),
                             prefix = None,
                             fileAttributes = Nil
                           )
                           .flatMap { tmpDir =>
                             val sampleFile = tmpDir / "createTempDirectoryScoped2"
                             pathRef.set(Some(tmpDir)) *> createAndWriteAndThenRead(sampleFile)(sampleFileContent)
                           }
                       }
          tmpFilePath             <- pathRef.get.some
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
