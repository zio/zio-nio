import sbt._
import sbt.Keys._

object BuildHelper {

  lazy val zioCoreVersion = "1.0.0-RC13"

  def testz           = "0.0.6"
  def silencerVersion = "1.4.3"
  def Scala212        = "2.12.10"
  def Scala213        = "2.13.0"

  val testDeps = Seq(
    "dev.zio" %% "zio-test"     % zioCoreVersion % "test",
    "dev.zio" %% "zio-test-sbt" % zioCoreVersion % "test"
  )

  val compileOnlyDeps = Seq(
    ("com.github.ghik" % "silencer-lib" % silencerVersion % Provided)
      .cross(CrossVersion.full)
  )

  val compileAndTest = Seq(
    "dev.zio" %% "zio-streams"      % zioCoreVersion,
    "dev.zio" %% "zio-interop-java" % "1.1.0.0-RC2"
  )

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

  val stdOpts213 = Seq(
    "-Wunused:imports",
    "-Wvalue-discard",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wunused:params",
    "-Wvalue-discard",
    "-Wdead-code"
  )

  val stdOptsUpto212 = Seq(
    "-Xfuture",
    "-Ypartial-unification",
    "-Ywarn-nullary-override",
    "-Yno-adapted-args",
    "-Ywarn-infer-any",
    "-Ywarn-inaccessible",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused-import"
  )

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 13)) =>
        stdOpts213
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        ) ++ stdOptsUpto212
      case _ =>
        Seq(
          "-Xexperimental"
        ) ++ stdOptsUpto212
    }

  def stdSettings(prjName: String) = Seq(
    name := s"zio-$prjName",
    scalacOptions := stdOptions,
    crossScalaVersions := Seq(Scala212, Scala213),
    scalaVersion in ThisBuild := Scala212,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    libraryDependencies ++= compileOnlyDeps ++ testDeps ++ compileAndTest ++ Seq(
      compilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
      compilerPlugin(
        ("com.github.ghik" % "silencer-plugin" % silencerVersion)
          .cross(CrossVersion.full)
      )
    ),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
