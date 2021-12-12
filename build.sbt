import sbt.Keys.libraryDependencies

val scala3Version = "3.1.3"
val circeVersion = "0.14.1"

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "go-3d",
    version := "0.7.0",
    maintainer := "lene.preuss@gmail.com",
    scalaVersion := scala3Version,

    scalacOptions ++= Seq("-deprecation", "-explain", "-feature"),

    // Logging
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
    // JUnit
    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
    // requests
    libraryDependencies += "com.lihaoyi" %% "requests" % "0.6.9",

    // newer jetty versions require both code changes and Java >= 11
    libraryDependencies ++= Seq(
        "org.eclipse.jetty" % "jetty-servlet" % "9.4.42.v20210604",
        "org.eclipse.jetty" % "jetty-server" % "9.4.42.v20210604",
    ),

    // Circe
    libraryDependencies ++= Seq(
        "io.circe" %% "circe-core",
        "io.circe" %% "circe-generic",
        "io.circe" %% "circe-parser"
    ).map(_ % circeVersion),

    // ScalaFX
    libraryDependencies += "org.scalafx" %% "scalafx" % "16.0.0-R24",

    // LWJGL
    libraryDependencies ++= {
        val version = "3.3.0"
        lazy val osName = System.getProperty("os.name") match {
            case n if n.startsWith("Linux") => "linux"
            case n if n.startsWith("Mac") => "mac"
            case n if n.startsWith("Windows") => "win"
            case _ => throw new Exception("Unknown platform!")
        }
        Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
          .map(m => "org.openjfx" % s"javafx-$m" % "16" classifier osName)
      },

      libraryDependencies ++= {
        val version = "3.3.0"
        val os = "linux" // TODO: Change to "windows" or "macos" if necessary

        Seq(
            "lwjgl",
            "lwjgl-glfw",
            "lwjgl-opengl"
            // TODO: Add more modules here
        ).flatMap {
            module => {
                Seq(
                    "org.lwjgl" % module % version,
                    "org.lwjgl" % module % version classifier s"natives-$os"
                )
            }
        }
    }
)
