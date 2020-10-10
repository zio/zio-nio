import sbt._
import sbt.Keys._
import dotty.tools.sbtplugin.DottyPlugin.autoImport._

object BuildHelper {

  private val SilencerVersion = "1.7.1"

  def stdSettings(prjName: String) =
    Seq(
      name := s"$prjName",
      scalacOptions := stdOptions,
      crossScalaVersions := Seq(Scala211, Scala212, Scala213),
      scalaVersion in ThisBuild := Scala213,
      scalacOptions := stdOptions ++ extraOptions(scalaVersion.value),
      libraryDependencies ++= {
        if (isDotty.value)
          Seq(("com.github.ghik" % "silencer-lib_2.13.1" % "1.6.0" % Provided).withDottyCompat(scalaVersion.value))
        else
          Seq(
            ("com.github.ghik"                % "silencer-lib"            % SilencerVersion % Provided).cross(CrossVersion.full),
            compilerPlugin(("com.github.ghik" % "silencer-plugin"         % SilencerVersion).cross(CrossVersion.full)),
            "org.scala-lang.modules"         %% "scala-collection-compat" % "2.2.0"
          )
      },
      incOptions ~= (_.withLogRecompileOnMacro(false))
    )

  val dottySettings = Seq(
    // Keep this consistent with the version in .circleci/config.yml
    crossScalaVersions += dottyVersion,
    scalacOptions ++= {
      if (isDotty.value)
        Seq("-noindent")
      else
        Seq()
    },
    sources in (Compile, doc) := {
      val old = (Compile / doc / sources).value
      if (isDotty.value)
        Nil
      else
        old
    },
    parallelExecution in Test := {
      val old = (Test / parallelExecution).value
      if (isDotty.value)
        false
      else
        old
    }
  )

  val ZioCoreVersion = "1.0.2"

  private val Scala211     = "2.11.12"
  private val Scala212     = "2.12.11"
  private val Scala213     = "2.13.3"
  private val dottyVersion = "0.27.0-RC1"

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked"
  )

  private val std2xOptions = Seq(
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
    "-Xsource:2.13",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfatal-warnings"
  )

  private def extraOptions(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((0, _))  =>
        Seq(
          "-language:implicitConversions",
          "-Xignore-scala2-macros"
        )
      case Some((2, 13)) =>
        Seq(
          "-Wunused:imports",
          "-Wvalue-discard",
          "-Wunused:patvars",
          "-Wunused:privates",
          "-Wunused:params",
          "-Wvalue-discard",
          "-Wdead-code"
        ) ++ std2xOptions
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
        ) ++ std2xOptions
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
        ) ++ std2xOptions
      case _             => Seq.empty
    }
}
