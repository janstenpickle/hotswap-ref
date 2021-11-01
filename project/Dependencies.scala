import sbt._

object Dependencies {
  object Versions {
    val cats = "2.6.1"
    val catsEffect = "3.2.9"
    val scala212 = "2.12.15"
    val scala213 = "2.13.7"
    val scala3 = "3.0.2"

    val catsTestkitScalatest = "2.1.5"
    val scalaTest = "3.2.10"

    val kindProjector = "0.13.2"
  }

  lazy val cats = "org.typelevel"          %% "cats-core"       % Versions.cats
  lazy val catsEffectStd = "org.typelevel" %% "cats-effect-std" % Versions.catsEffect

  lazy val catsEffectTestkit = "org.typelevel"    %% "cats-effect-testkit"    % Versions.catsEffect
  lazy val catsTestkitScalatest = "org.typelevel" %% "cats-testkit-scalatest" % Versions.catsTestkitScalatest
  lazy val scalaTest = "org.scalatest"            %% "scalatest"              % Versions.scalaTest

  lazy val test =
    Seq(catsEffectTestkit, catsTestkitScalatest, scalaTest)

  lazy val kindProjector = ("org.typelevel" %% "kind-projector" % Versions.kindProjector).cross(CrossVersion.full)
}
