import sbt._
import sbt.Keys._

object BuildHelper {

  lazy val zioCoreVersion = "1.0.0-RC10-1"

  def testz           = "0.0.5"
  def silencerVersion = "1.4.1"
  def Scala212        = "2.12.8"

  val testDeps = Seq(
    "org.scalacheck" %% "scalacheck"   % "1.14.0" % "test",
    "org.scalaz"     %% "testz-core"   % testz    % "test",
    "org.scalaz"     %% "testz-stdlib" % testz    % "test",
    "org.scalaz"     %% "testz-runner" % testz    % "test",
    "org.scalaz"     %% "testz-scalaz" % testz    % "test",
    "org.scalaz"     %% "testz-specs2" % testz    % "test"
  )
  val compileOnlyDeps = Seq("com.github.ghik" %% "silencer-lib" % silencerVersion % "provided")

  val compileAndTest = Seq(
    "dev.zio" %% "zio-streams"      % zioCoreVersion,
    "dev.zio" %% "zio-interop-java" % "1.1.0.0-RC2"
  )

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-Xfuture",
    "-Ypartial-unification",
    "-language:higherKinds",
    "-language:existentials",
    "-unchecked",
    "-Yno-adapted-args",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:2.13",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfatal-warnings"
  )

  def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 12)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused:_,imports",
          "-Ywarn-unused:imports",
          "-opt:l:inline",
          "-opt-inline-from:<source>"
        )
      case _ =>
        Seq(
          "-Xexperimental",
          "-Ywarn-unused-import"
        )
    }

  def stdSettings(prjName: String) = Seq(
    name := s"zio-$prjName",
    scalacOptions := stdOptions,
    crossScalaVersions := Seq(Scala212),
    scalaVersion in ThisBuild := Scala212,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    libraryDependencies ++= compileOnlyDeps ++ testDeps ++ compileAndTest ++ Seq(
      compilerPlugin("org.typelevel"   %% "kind-projector"  % "0.10.3"),
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % silencerVersion)
    ),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
