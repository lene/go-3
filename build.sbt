import sbt.Keys.libraryDependencies

val scala3Version = "3.7.3"
val circeVersion = "0.14.15"
val libgdxVersion = "1.13.5"
val http4sVersion = "1.0.0-M45"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "go-3d",
    version := "0.7.16",
    maintainer := "lene.preuss@gmail.com",
    scalaVersion := scala3Version,

    scalacOptions ++= Seq("-deprecation", "-explain", "-feature", "-Wunused:all"),

    // WartRemover configuration - use warnings instead of errors for evaluation
    wartremoverWarnings ++= Warts.unsafe,

    // Parallel collections
    libraryDependencies += "org.scala-lang.modules" %% "scala-parallel-collections" % "1.1.0",
    // Logging
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.15",
    // JUnit
    libraryDependencies ++= Seq(
          "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test
    ),
    // ScalaTest
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % "test",
    Test / logBuffered := false,
    // Scallop command line parser
    libraryDependencies += "org.rogach" %% "scallop" % "5.2.0",
    // requests
    libraryDependencies += "com.lihaoyi" %% "requests" % "0.9.0",
    libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-ember-client" % http4sVersion,
        "org.http4s" %% "http4s-ember-server" % http4sVersion,
        "org.http4s" %% "http4s-dsl"          % http4sVersion,
        "org.http4s" %% "http4s-circe"        % http4sVersion,
    ),
    // log4cats for http4s logging
    libraryDependencies ++= Seq(
        "org.typelevel" %% "log4cats-core"  % "2.7.0",
        "org.typelevel" %% "log4cats-slf4j" % "2.7.0",
    ),
    // circe
    libraryDependencies ++= Seq(
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-generic",
        "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),
    // libGDX
    libraryDependencies ++= Seq(
        "com.badlogicgames.gdx" % "gdx" % libgdxVersion,
        "net.sf.proguard" % "proguard-base" % "6.2.2" % "provided",
        "com.badlogicgames.gdx" % "gdx-backend-lwjgl3" % libgdxVersion,
        "com.badlogicgames.gdx" % "gdx-platform" % libgdxVersion classifier "natives-desktop",
    )

  )
