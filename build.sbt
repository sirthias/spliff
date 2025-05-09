import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import sbt._
import java.net.URI

inThisBuild(
  Seq(
    organization := "io.bullet",
    homepage     := Some(URI.create("https://github.com/sirthias/spliff/").toURL),
    description  := "Efficient diffing in Scala",
    startYear    := Some(2020),
    licenses     := Seq("MPLv2" -> URI.create("https://www.mozilla.org/en-US/MPL/2.0/").toURL),
    scmInfo := Some(ScmInfo(url("https://github.com/sirthias/spliff/"), "scm:git:git@github.com:sirthias/spliff.git")),
    developers :=
      List(
        "sirthias" -> "Mathias Doenitz",
      ).map { case (username, fullName) =>
        Developer(username, fullName, s"@$username", url(s"https://github.com/$username"))
      },
    versionScheme := Some("early-semver")
  )
)

lazy val commonSettings = Seq(
  scalaVersion       := "2.13.14",
  crossScalaVersions := Seq("2.13.14", "3.3.6"),
  libraryDependencies ++= Seq(
    "org.scalameta"  %% "munit"            % "1.0.0"  % Test,
    "org.scalameta"  %% "munit-scalacheck" % "1.0.0"  % Test,
    "org.scalacheck" %% "scalacheck"       % "1.18.0" % Test
  ),
  Compile / doc / scalacOptions += "-no-link-warnings",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:implicitConversions",
    "-release:8",
    "-unchecked",
    "-Werror",
    "-Wnonunit-statement",
    "-Wvalue-discard",
  ),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "-Wdead-code",
          "-Wnumeric-widen",
          "-Wunused",
          "-Xlint",
          "-Xsource:3",
          "-Ybackend-parallelism",
          "8",
          "-Ycache-macro-class-loader:last-modified",
        )
      case Some((3, _)) =>
        Seq(
          "-Wunused:all",
        )
      case x => sys.error(s"unsupported scala version: $x")
    }
  },
  Compile / console / scalacOptions ~= (_ filterNot (o => o.contains("warn") || o.contains("Xlint"))),
  Test / console / scalacOptions := (Compile / console / scalacOptions).value,
  Compile / doc / scalacOptions += "-no-link-warnings",
  Compile / unmanagedResources += baseDirectory.value.getParentFile.getParentFile / "LICENSE",
  sourcesInBase := false,

  // file headers
  headerLicense := Some(HeaderLicense.MPLv2("2021 - 2024", "Mathias Doenitz")),

  // publishing
  publishMavenStyle      := true,
  Test / publishArtifact := false,
  pomIncludeRepository   := (_ ⇒ false),
  publishTo              := sonatypePublishToBundle.value,
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

lazy val `spliff-root` = project
  .in(file("."))
  .aggregate(`spliff-jvm`, `spliff-js`)
  .settings(commonSettings)
  .settings(releaseSettings)
  .settings(
    publish / skip := true,
  )

lazy val `spliff-jvm` = spliff.jvm
lazy val `spliff-js`  = spliff.js

lazy val spliff = crossProject(JSPlatform, JVMPlatform)
  .withoutSuffixFor(JVMPlatform)
  .crossType(CrossType.Pure)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(releaseSettings)
  .jsSettings(scalajsSettings: _*)
