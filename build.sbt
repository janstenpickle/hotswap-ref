lazy val commonSettings = Seq(
  libraryDependencies += compilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.0").cross(CrossVersion.patch)),
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  scalacOptions := {
    val opts = scalacOptions.value :+ "-Wconf:src=src_managed/.*:s,any:wv"

    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => opts.filterNot(Set("-Xfatal-warnings"))
      case _ => opts
    }
  },
  Test / fork := true,
  resolvers += Resolver.sonatypeRepo("releases"),
  ThisBuild / evictionErrorLevel := Level.Warn,
)

lazy val noPublishSettings =
  commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None, publish / skip := true)

lazy val publishSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  Test / publishArtifact := false
)

lazy val documentationSettings = Seq(
  autoAPIMappings := true,
  apiMappings ++= {
    // Lookup the path to jar from the classpath
    val classpath = (Compile / fullClasspath).value
    def findJar(nameBeginsWith: String): File =
      classpath
        .find { attributed: Attributed[java.io.File] => (attributed.data ** s"$nameBeginsWith*.jar").get.nonEmpty }
        .get
        .data // fail hard if not found
    // Define external documentation paths
    Map(findJar("cats-effect") -> url("https://typelevel.org/cats-effect/api/3.x/cats/index.html"))
  }
)

lazy val root = (project in file("."))
  .settings(noPublishSettings)
  .settings(name := "HotswapRef")
  .aggregate(core)

lazy val core =
  (project in file("modules/core"))
    .settings(publishSettings)
    .settings(documentationSettings)
    .settings(
      name := "hotswap-ref",
      libraryDependencies ++= Seq(Dependencies.cats, Dependencies.catsEffectStd),
      libraryDependencies ++= Dependencies.test.map(_ % Test)
    )

addCommandAlias("fmt", "all root/scalafmtSbt root/scalafmtAll")
addCommandAlias("fmtCheck", "all root/scalafmtSbtCheck root/scalafmtCheckAll")
