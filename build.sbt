val baseName = "webzinc"

lazy val commonSettings = Seq(
  organization := "com.github.karasiq",
  version := "1.0.0-SNAPSHOT",
  isSnapshot := version.value.endsWith("-SNAPSHOT"),
  scalaVersion := "2.12.3"
)

val packageSettings = Seq(
  // javaOptions in Universal += "-Xmx2G",
  name in Universal := baseName,
  version in Universal := version.value.replace("-SNAPSHOT", ""),
  maintainer := "Karasiq",
  packageSummary := "WebZinc",
  packageDescription := "WebZinc - web page archival utility.",
  jdkAppIcon := Some(file("setup/icon.ico")),
  jdkPackagerType := "installer",
  jdkPackagerJVMArgs := Seq("-Xmx2G"),
  jdkPackagerProperties := Map("app.name" -> baseName, "app.version" -> version.value.replace("-SNAPSHOT", ""))
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ ⇒ false },
  licenses := Seq("Apache License, Version 2.0" → url("http://opensource.org/licenses/Apache-2.0")),
  homepage := Some(url(s"https://github.com/Karasiq/$baseName")),
  pomExtra := <scm>
    <url>git@github.com:Karasiq/{baseName}.git</url>
    <connection>scm:git:git@github.com:Karasiq/{baseName}.git</connection>
  </scm>
    <developers>
      <developer>
        <id>karasiq</id>
        <name>Piston Karasiq</name>
        <url>https://github.com/Karasiq</url>
      </developer>
    </developers>
)

lazy val noPublishSettings = Seq(
  publishArtifact := false,
  publishArtifact in makePom := false,
  publishTo := Some(Resolver.file("Repo", file("target/repo")))
)

lazy val core = project
  .settings(
    commonSettings,
    name := s"$baseName-core",
    libraryDependencies ++= ProjectDeps.akka.all ++ ProjectDeps.commonsNetwork ++ ProjectDeps.htmlUnit ++ ProjectDeps.jsoup ++ ProjectDeps.scalaTest.map(_ % "test")
  )

lazy val app = project
  .settings(commonSettings, packageSettings, noPublishSettings, name := baseName)
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging, ClasspathJarPlugin, JDKPackagerPlugin)
  
lazy val root = project
  .settings(commonSettings, noPublishSettings, name := s"$baseName-root")
  .aggregate(app)