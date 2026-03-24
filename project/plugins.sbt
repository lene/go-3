// Universal / packageBin
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.7")

// assembly / assembly (fat JAR for Lambda)
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")

// JUnit5/Jupiter
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.17.1")

// Test coverage (see https://www.baeldung.com/scala/sbt-scoverage-code-analysis)
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// Static analysis tools
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "3.5.6")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6")
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.2.9")
