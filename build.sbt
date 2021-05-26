lazy val commonSettings = Seq(
  scalaVersion := Dependencies.Versions.scala213,
  organization := "io.janstenpickle",
  organizationName := "janstenpickle",
  developers := List(
    Developer(
      "janstenpickle",
      "Chris Jansen",
      "janstenpickle@users.noreply.github.com",
      url = url("https://github.com/janstepickle")
    )
  ),
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("https://github.com/janstenpickle/hotswap-ref")),
  scmInfo := Some(
    ScmInfo(url("https://github.com/janstenpickle/hotswap-ref"), "scm:git:git@github.com:janstenpickle/hotswap-ref.git")
  ),
  Compile / compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  scalacOptions := {
    val opts = scalacOptions.value :+ "-Wconf:src=src_managed/.*:s,any:wv"

    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) => opts.filterNot(Set("-Xfatal-warnings"))
      case _ => opts
    }
  },
  Test / fork := true,
  Global / releaseEarlyWith := SonatypePublisher,
  credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
  releaseEarlyEnableSyncToMaven := true,
  pgpPublicRing := file("./.github/git adlocal.pubring.asc"),
  pgpSecretRing := file("./.github/local.secring.asc"),
  crossScalaVersions := Seq(Dependencies.Versions.scala213, Dependencies.Versions.scala212),
  resolvers += Resolver.sonatypeRepo("releases"),
  ThisBuild / evictionErrorLevel := Level.Warn
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

lazy val noPublishSettings = commonSettings ++ Seq(publish := {}, publishArtifact := false, publishTo := None)

lazy val publishSettings = commonSettings ++ Seq(
  publishMavenStyle := true,
  pomIncludeRepository := { _ =>
    false
  },
  Test / publishArtifact := false
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
