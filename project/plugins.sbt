// Universal / packageBin
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.6")

// JUnit5/Jupiter
addSbtPlugin("net.aichler" % "sbt-jupiter-interface" % "0.11.1")

// Test coverage (see https://www.baeldung.com/scala/sbt-scoverage-code-analysis)
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.9")
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always