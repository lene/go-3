// Universal / packageBin
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.13.0")

// assembly / assembly (fat JAR for Lambda)
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.5.0")

// JUnit5/Jupiter
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.20.0")

// Test coverage (see https://www.baeldung.com/scala/sbt-scoverage-code-analysis)
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

// Formatting (sbt scalafmtAll, checked in CI by scalafmtCheckAll)
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")

// Static analysis tools
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "3.6.3")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.10")
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat" % "1.2.13")
