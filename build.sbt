import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import sbt._

inThisBuild(
  Seq(
    organization := "io.bullet",
    homepage := Some(new URL("https://github.com/sirthias/spliff/")),
    description := "Efficient diffing in Scala",
    startYear := Some(2020),
    licenses := Seq("MPLv2" → new URL("https://www.mozilla.org/en-US/MPL/2.0/")),
    scmInfo := Some(ScmInfo(url("https://github.com/sirthias/spliff/"), "scm:git:git@github.com:sirthias/spliff.git")),
    developers :=
      List(
        "sirthias" -> "Mathias Doenitz",
      ).map { case (username, fullName) =>
        Developer(username, fullName, s"@$username", url(s"https://github.com/$username"))
      }
  )
)

lazy val commonSettings = Seq(
  scalaVersion := "2.13.5",
  crossScalaVersions := Seq("2.13.5", "3.0.0-RC3"),

  libraryDependencies += "org.scalameta" %% "munit" % "0.7.25" % Test,

  Compile / doc / scalacOptions += "-no-link-warnings",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:_",
    "-unchecked",
    "-Xfatal-warnings"
  ),

  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(
        "-target:jvm-1.8",
        "-Xlint:_,-missing-interpolator",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Ybackend-parallelism", "8",
        "-Ywarn-unused:imports,-patvars,-privates,-locals,-implicits,-explicits",
        "-Ycache-macro-class-loader:last-modified",
      )
      case Some((3, _)) => Seq(
        "-source:3.0-migration",
        "-language:implicitConversions"
      )
      case x => sys.error(s"unsupported scala version: $x")
    }
  },

  sourcesInBase := false,
  Compile / unmanagedResources += baseDirectory.value.getParentFile.getParentFile / "LICENSE",

  // file headers
  headerLicense := Some(HeaderLicense.MPLv2("2021", "Mathias Doenitz")),

  // reformat main and test sources on compile
  scalafmtOnCompile := true,

  testFrameworks += new TestFramework("munit.Framework"),

  // publishing
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := (_ ⇒ false),
  publishTo := sonatypePublishToBundle.value,
)

lazy val scalajsSettings = Seq(
  scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule).withSourceMap(false)),
  scalaJSStage in Global := FastOptStage,
  scalacOptions ~= { _.filterNot(_ == "-Ywarn-dead-code") }
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

lazy val root = project.in(file("."))
  .aggregate(`spliff-jvm`, `spliff-js`)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(
    publish / skip := true,
  )

lazy val `spliff-jvm` = spliff.jvm
lazy val `spliff-js`  = spliff.js
lazy val spliff = crossProject(JSPlatform, JVMPlatform).withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(releaseSettings)
  .jsSettings(scalajsSettings: _*)