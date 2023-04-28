import BuildHelper._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/zio-nio/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net"))
    )
  )
)

addCommandAlias("fix", "; all compile:scalafix test:scalafix; all scalafmtSbt scalafmtAll")
addCommandAlias("check", "; scalafmtSbtCheck; scalafmtCheckAll; Compile / scalafix --check; Test / scalafix --check")
addCommandAlias("coverageReport", "clean coverage test coverageReport coverageAggregate")
addCommandAlias(
  "testDotty",
  ";zioNio/test;examples/test"
)

val zioVersion = "1.0.18"

lazy val root = project
  .in(file("."))
  .settings(publish / skip := true)
  .aggregate(zioNio, examples)

lazy val zioNio = project
  .in(file("nio"))
  .settings(stdSettings("zio-nio"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % zioVersion,
      "dev.zio"                %% "zio-streams"             % zioVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.10.0",
      "dev.zio"                %% "zio-test"                % zioVersion % Test,
      "dev.zio"                %% "zio-test-sbt"            % zioVersion % Test
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
  .settings(dottySettings)

lazy val docs = project
  .in(file("zio-nio-docs"))
  .settings(stdSettings("zio-nio-docs"))
  .settings(
    publish / skip := true,
    moduleName     := "zio-nio-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    crossScalaVersions -= Scala211,
    projectName                                := "ZIO NIO",
    mainModuleName                             := (zioNio / moduleName).value,
    projectStage                               := ProjectStage.Development,
    docsPublishBranch                          := "master",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(),
    checkArtifactBuildProcessWorkflowStep      := None
  )
  .dependsOn(zioNio)
  .enablePlugins(WebsitePlugin)

lazy val examples = project
  .in(file("examples"))
  .settings(stdSettings("examples"))
  .settings(
    publish / skip := true,
    moduleName     := "examples"
  )
  .settings(dottySettings)
  .dependsOn(zioNio)
