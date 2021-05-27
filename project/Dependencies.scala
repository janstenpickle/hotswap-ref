import sbt._

object Dependencies {
  object Versions {
    val cats = "2.6.1"
    val catsEffect = "3.1.1"
    val scala212 = "2.12.13"
    val scala213 = "2.13.6"

    val catsTestkitScalatest = "2.1.5"
    val disciplineScalatest = "2.1.5"
    val discipline = "1.1.5"
    val scalaCheck = "1.15.4"
    val scalaCheckShapeless = "1.2.5"
    val scalaTest = "3.2.9"
    val testContainers = "0.39.4"
  }

  lazy val cats = "org.typelevel"          %% "cats-core"       % Versions.cats
  lazy val catsEffectStd = "org.typelevel" %% "cats-effect-std" % Versions.catsEffect

  lazy val catsEffectTestkit = "org.typelevel"    %% "cats-effect-testkit"    % Versions.catsEffect
  lazy val catsTestkitScalatest = "org.typelevel" %% "cats-testkit-scalatest" % Versions.catsTestkitScalatest
  lazy val scalaTest = "org.scalatest"            %% "scalatest"              % Versions.scalaTest

  lazy val test =
    Seq(catsEffectTestkit, catsTestkitScalatest, scalaTest)
}
