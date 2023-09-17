import sbt.Keys.libraryDependencies

val scala3Version = "3.3.1"
val circeVersion = "0.14.1"
val libgdxVersion = "1.12.0"

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "go-3d",
    version := "0.7.5",
    maintainer := "lene.preuss@gmail.com",
    scalaVersion := scala3Version,

    scalacOptions ++= Seq("-deprecation", "-explain", "-feature"),

    // Logging
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.7",
    // JUnit
    libraryDependencies ++= Seq(
          "net.aichler" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test
    ),
    // ScalaTest
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % "test",
    // requests
    libraryDependencies += "com.lihaoyi" %% "requests" % "0.8.0",
    // newer jetty versions require both code changes and Java >= 11
    libraryDependencies ++= Seq(
        "org.eclipse.jetty" % "jetty-servlet" % "9.4.51.v20230217",
        "org.eclipse.jetty" % "jetty-server" % "9.4.51.v20230217",
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
