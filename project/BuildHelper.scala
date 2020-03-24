import sbt._
import sbt.Keys._

object BuildHelper {

  def stdSettings(prjName: String) = Seq(
    name := s"$prjName",
    scalacOptions := stdOptions,
    crossScalaVersions := Seq(Scala211, Scala212, Scala213),
    scalaVersion in ThisBuild := Scala212,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    libraryDependencies ++=
      Seq(
        ("com.github.ghik" % "silencer-lib" % "1.4.4" % Provided).cross(CrossVersion.full),
        compilerPlugin(("com.github.ghik" % "silencer-plugin" % "1.4.4").cross(CrossVersion.full))
      ),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )

  val ZioCoreVersion = "1.0.0-RC18-2"

  private val Scala211 = "2.11.12"
  private val Scala212 = "2.12.10"
  private val Scala213 = "2.13.1"

  private val stdOptions = Seq(
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:2.13",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings"
  )

  private def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 13)) =>
        Seq(
          "-Wunused:imports",
          "-Wvalue-discard",
          "-Wunused:patvars",
          "-Wunused:privates",
          "-Wunused:params",
          "-Wvalue-discard",
          "-Wdead-code"
        )
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>",
          "-Xfuture",
          "-Ypartial-unification",
          "-Ywarn-nullary-override",
          "-Yno-adapted-args",
          "-Ywarn-infer-any",
          "-Ywarn-inaccessible",
          "-Ywarn-nullary-unit",
          "-Ywarn-unused-import"
        )
      case Some((2, 11)) =>
        Seq(
          "-Ypartial-unification",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit",
          "-Xexperimental",
          "-Ywarn-unused-import",
          "-Xfuture",
          "-Xsource:2.13",
          "-Xmax-classfile-name",
          "242"
        )
      case _ => Seq.empty
    }
}
