inThisBuild(
  List(
    organization := "dev.zio",
    homepage := Some(url("https://github.com/zio/zio-nio/")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("jdegoes", "John De Goes", "john@degoes.net", url("http://degoes.net"))
    ),
    pgpPublicRing := file("/tmp/public.asc"),
    pgpSecretRing := file("/tmp/secret.asc"),
    releaseEarlyWith := SonatypePublisher,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/zio/zio-nio/"),
        "scm:git:git@github.com:zio/zio-nio.git"
      )
    )
  )
)

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val zioNio = project
  .in(file("."))
  .settings(
    name := "zio-nio",
    libraryDependencies ++= Seq(
      "dev.zio"        %% "zio"              % "1.0.0-RC12-1",
      "dev.zio"        %% "zio-streams"      % "1.0.0-RC12-1",
      "dev.zio"        %% "zio-interop-java" % "1.1.0.0-RC4"
    )
  )
  .settings(BuildHelper.stdSettings("zio-nio"))

lazy val docs = project
  .in(file("zio-nio-docs"))
  .settings(
    skip.in(publish) := true,
    moduleName := "zio-nio-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC12-1"
    )
  )
  .dependsOn(zioNio)
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
