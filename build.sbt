import sbt._

enablePlugins(AutomateHeaderPlugin)

organization := "io.bullet"
homepage := Some(new URL("https://github.com/sirthias/spliff/"))
description := "Efficient diffing in Scala"
startYear := Some(2020)
licenses := Seq("MPLv2" → new URL("https://www.mozilla.org/en-US/MPL/2.0/"))
Compile / unmanagedResources += baseDirectory.value.getParentFile.getParentFile / "LICENSE"
scmInfo := Some(ScmInfo(url("https://github.com/sirthias/spliff/"), "scm:git:git@github.com:sirthias/spliff.git"))

scalaVersion := "2.13.5"

libraryDependencies += "org.scalameta" %% "munit" % "0.7.25" % Test

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:_",
  "-unchecked",
  "-target:jvm-1.8",
  "-Xlint:_,-missing-interpolator",
  "-Xfatal-warnings",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ybackend-parallelism", "8",
  "-Ywarn-unused:imports,-patvars,-privates,-locals,-implicits,-explicits",
  "-Ycache-macro-class-loader:last-modified",
)
  
Compile / console / scalacOptions ~= (_ filterNot(o => o.contains("warn") || o.contains("Xlint")))
Test / console / scalacOptions := (Compile / console / scalacOptions).value
Compile / doc / scalacOptions += "-no-link-warnings"
sourcesInBase := false

// file headers
headerLicense := Some(HeaderLicense.MPLv2("2021", "Mathias Doenitz"))

// reformat main and test sources on compile
scalafmtOnCompile := true

testFrameworks += new TestFramework("munit.Framework")

// publishing
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := (_ ⇒ false)
publishTo := sonatypePublishToBundle.value

developers := List(
  Developer("sirthias", "Mathias Doenitz", "devnull@bullet.io", url("https://github.com/sirthias/"))
)

lazy val releaseSettings = {
  import ReleaseTransformations._
  Seq(
    releaseCrossBuild := true,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      releaseStepCommand("sonatypeBundleRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
}