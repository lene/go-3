import sbt.Keys.libraryDependencies

val scala3Version = "3.8.2"
val circeVersion = "0.14.15"
val libgdxVersion = "1.14.0"
val http4sVersion = "1.0.0-M46"

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / scapegoatVersion := "3.3.3"
// OptionGet is already covered by WartRemover's OptionPartial (0 violations in our code).
// Disabled here because sbt-scapegoat's own source (Literals.scala) triggers a false positive.
ThisBuild / scapegoatDisabledInspections := Seq("OptionGet")

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "go-3d",
    version := "0.7.21",
    maintainer := "lene.preuss@gmail.com",
    scalaVersion := scala3Version,

    scalacOptions ++= Seq("-deprecation", "-explain", "-feature", "-Wunused:all"),

    // WartRemover configuration
    // Exclude IterableOps (head/tail/last) - all usages are protected by preconditions
    // Exclude Throw from warnings since it is promoted to an error below
    wartremoverWarnings ++= Warts.unsafe.filterNot(w =>
      w == Wart.IterableOps || w.toString.contains("IterableOps") || w == Wart.Throw
    ),
    // Wart.Throw is a build error: only 4 library-interop throws remain (Scallop onError),
    // each annotated with @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    wartremoverErrors += Wart.Throw,

    // Parallel collections
    libraryDependencies += "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0",
    // Logging
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.32",
    // JUnit
    libraryDependencies ++= Seq(
          "com.github.sbt.junit" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test
    ),
    // ScalaTest
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % "test",
    Test / logBuffered := false,
    // Scallop command line parser
    libraryDependencies += "org.rogach" %% "scallop" % "5.2.0",
    // requests
    libraryDependencies += "com.lihaoyi" %% "requests" % "0.9.2",
    libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-ember-client" % http4sVersion,
        "org.http4s" %% "http4s-ember-server" % http4sVersion,
        "org.http4s" %% "http4s-dsl"          % http4sVersion,
        "org.http4s" %% "http4s-circe"        % http4sVersion,
    ),
    // log4cats for http4s logging
    libraryDependencies ++= Seq(
        "org.typelevel" %% "log4cats-core"  % "2.7.1",
        "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
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
        "com.guardsquare" % "proguard-base" % "7.8.2" % "provided",
        "com.badlogicgames.gdx" % "gdx-backend-lwjgl3" % libgdxVersion,
        "com.badlogicgames.gdx" % "gdx-platform" % libgdxVersion classifier "natives-desktop",
    )

  )
