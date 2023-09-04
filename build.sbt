import BuildHelper._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://github.com/zio/zio-nio/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net"))
    ),
    scalaVersion      := "2.13.11",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; Compile / scalafix --check; Test / scalafix --check")
addCommandAlias("coverageReport", "clean coverage test coverageReport coverageAggregate")
addCommandAlias(
  "testDotty",
  ";zioNio/test;examples/test"
)

val zioVersion = "2.0.16"

lazy val root = crossProject(JVMPlatform, NativePlatform)
  .in(file("."))
  .settings(publish / skip := true)
  .aggregate(zioNio, examples)

lazy val zioNio = crossProject(JVMPlatform, NativePlatform)
  .in(file("nio"))
  .settings(stdSettings("zio-nio"))
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("zio.nio"))
  .settings(scala3Settings)
  .jvmSettings(
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % zioVersion,
      "dev.zio"                %% "zio-streams"             % zioVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.11.0",
      "dev.zio"                %% "zio-test"                % zioVersion % Test,
      "dev.zio"                %% "zio-test-sbt"            % zioVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .nativeSettings(Test / fork := false)
  .nativeSettings(
    libraryDependencies ++= Seq(
      "dev.zio"                %%% "zio"                     % zioVersion,
      "dev.zio"                %%% "zio-streams"             % zioVersion,
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.11.0",
      "dev.zio"                %%% "zio-test"                % zioVersion % Test,
      "dev.zio"                %%% "zio-test-sbt"            % zioVersion % Test,
      "io.github.cquiroz"      %%% "scala-java-time"         % "2.5.0"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val docs = project
  .in(file("zio-nio-docs"))
  .settings(
    publish / skip := true,
    moduleName     := "zio-nio-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioNio.jvm),
    ScalaUnidoc / unidoc / target              := (LocalRootProject / baseDirectory).value / "website" / "static" / "api",
    cleanFiles += (ScalaUnidoc / unidoc / target).value,
    docusaurusCreateSite     := docusaurusCreateSite.dependsOn(Compile / unidoc).value,
    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(Compile / unidoc).value
  )
  .dependsOn(zioNio.jvm)
  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)

lazy val examples = crossProject(JVMPlatform, NativePlatform)
  .in(file("examples"))
  .settings(stdSettings("examples"))
  .settings(
    publish / skip := true,
    moduleName     := "examples"
  )
  .settings(crossProjectSettings)
  .settings(buildInfoSettings("examples"))
  .settings(scala3Settings)
  .dependsOn(zioNio)
