import sbt.Keys.libraryDependencies

val scala3Version = "3.0.0"

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "go-3d",
    version := "0.1.0",

    scalaVersion := scala3Version,

    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",

    libraryDependencies ++= Seq(
        "org.eclipse.jetty" % "jetty-servlet" % "9.4.9.v20180320",
        "org.eclipse.jetty" % "jetty-server" % "9.4.9.v20180320",
        "com.lihaoyi" %% "ujson" % "1.3.15"
    )
  )
