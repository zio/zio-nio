import BuildHelper._

inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://github.com/zio/zio-nio/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net"))
    ),
    pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/zio/zio-nio/"),
        "scm:git:git@github.com:zio/zio-nio.git"
      )
    )
  )
)

ThisBuild / publishTo := sonatypePublishToBundle.value

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val zioNioCore = project
  .in(file("nio-core"))
  .settings(stdSettings("zio-nio-core"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % ZioCoreVersion,
      "dev.zio"                %% "zio-streams"             % ZioCoreVersion,
      "dev.zio"                %% "zio-test"                % ZioCoreVersion % Test,
      "dev.zio"                %% "zio-test-sbt"            % ZioCoreVersion % Test,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val zioNio = project
  .in(file("nio"))
  .dependsOn(zioNioCore)
  .settings(stdSettings("zio-nio"))
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio"                %% "zio"                     % ZioCoreVersion,
      "dev.zio"                %% "zio-streams"             % ZioCoreVersion,
      "dev.zio"                %% "zio-test"                % ZioCoreVersion % Test,
      "dev.zio"                %% "zio-test-sbt"            % ZioCoreVersion % Test,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val docs = project
  .in(file("zio-nio-docs"))
  .settings(
    skip.in(publish) := true,
    moduleName := "zio-nio-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % ZioCoreVersion
    )
  )
  .dependsOn(zioNio)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)

lazy val examples = project
  .in(file("examples"))
  .settings(
    skip in publish := true,
    moduleName := "examples"
  )
  .dependsOn(zioNio)
