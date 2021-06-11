import sbt.Keys.libraryDependencies

val scala3Version = "3.0.0"
val circeVersion = "0.14.1"

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "go-3d",
    version := "0.6.0",
    maintainer := "lene.preuss@gmail.com",
    scalaVersion := scala3Version,

    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    libraryDependencies +=  "com.lihaoyi" %% "requests" % "0.6.9",

    libraryDependencies ++= Seq(
        "org.eclipse.jetty" % "jetty-servlet" % "9.4.9.v20180320",
        "org.eclipse.jetty" % "jetty-server" % "9.4.9.v20180320",
    ),

    libraryDependencies ++= Seq(
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-generic",
        "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),
)
