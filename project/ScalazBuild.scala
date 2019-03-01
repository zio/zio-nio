import sbt._
import sbt.Keys._

object Scalaz {

  lazy val scalazVersion    = "7.2.27"
  lazy val scalazZioVersion = "0.6.3"

  val testDeps = Seq(
    "org.scalacheck" %% "scalacheck"   % "1.14.0" % "test",
    "org.scalaz"     %% "testz-core"   % "0.0.5"  % "test",
    "org.scalaz"     %% "testz-stdlib" % "0.0.5"  % "test",
    "org.scalaz"     %% "testz-runner" % "0.0.5"  % "test",
    "org.scalaz"     %% "testz-scalaz" % "0.0.5"  % "test",
    "org.scalaz"     %% "testz-specs2" % "0.0.5"  % "test"
  )
  val compileOnlyDeps = Seq("com.github.ghik" %% "silencer-lib" % "1.3.1" % "provided")

  val compileAndTest = Seq(
    "org.scalaz" %% "scalaz-core"                 % scalazVersion % "compile, test",
    "org.scalaz" %% "scalaz-zio"                  % scalazZioVersion,
    "org.scalaz" %% "scalaz-zio-interop-java"     % scalazZioVersion,
    "org.scalaz" %% "scalaz-zio-interop-scalaz7x" % scalazZioVersion
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
    name := s"scalaz-$prjName",
    scalacOptions := stdOptions,
    crossScalaVersions := Seq("2.12.6", "2.11.12"),
    scalaVersion in ThisBuild := crossScalaVersions.value.head,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
    libraryDependencies ++= compileOnlyDeps ++ testDeps ++ compileAndTest ++ Seq(
      compilerPlugin("org.spire-math"  %% "kind-projector"  % "0.9.9"),
      compilerPlugin("com.github.ghik" %% "silencer-plugin" % "1.3.1")
    ),
    incOptions ~= (_.withLogRecompileOnMacro(false))
  )
}
