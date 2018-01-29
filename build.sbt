val baseName = "webzinc"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.3",
  crossScalaVersions := Seq("2.11.11", scalaVersion.value),
  organization := "com.github.karasiq",
  version := "1.0.5",
  isSnapshot := version.value.endsWith("-SNAPSHOT")
)

lazy val packageSettings = Seq(
  // javaOptions in Universal += "-Xmx2G",
  name in Universal := baseName,
  version in Universal := version.value.replace("-SNAPSHOT", ""),
  executableScriptName := baseName,
  mappings in Universal := {
    val universalMappings = (mappings in Universal).value
    val fatJar = (assembly in Compile).value
    val filtered = universalMappings.filterNot(_._2.endsWith(".jar"))
    filtered :+ (fatJar → ("lib/" + fatJar.getName))
  },
  test in assembly := {},
  assemblyJarName in assembly := s"$baseName.jar",
  scriptClasspath := Seq((assemblyJarName in assembly).value),
  maintainer := "Karasiq",
  packageSummary := "WebZinc",
  packageDescription := "WebZinc - web page archival utility.",
  jdkAppIcon := Some(file("setup/icon.ico")),
  jdkPackagerType := "installer",
  // jdkPackagerJVMArgs := Seq("-Xmx2G"),
  jdkPackagerProperties := Map("app.name" -> baseName, "app.version" -> version.value.replace("-SNAPSHOT", "")),
  // general package information (can be scoped to Windows)
  maintainer := "Karasiq <karasiq@airmail.cc>",
  packageSummary := "webzinc",
  packageDescription := """WebZinc application.""",
  wixProductId := "75bc6d56-5775-4a07-a006-1b1c75aefc3e",
  wixProductUpgradeId := "5cc0ee85-0c77-4059-9ea0-6b872b7739bd"
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
    publishSettings,
    name := baseName,
    libraryDependencies ++=
      ProjectDeps.akka.streams ++ ProjectDeps.akka.http ++ ProjectDeps.jsoup ++
      (ProjectDeps.scalaTest ++ ProjectDeps.akka.testKit).map(_ % "test")
  )

lazy val htmlunit = project
  .settings(
    commonSettings,
    publishSettings,
    name := s"$baseName-htmlunit",
    libraryDependencies ++= ProjectDeps.htmlUnit ++ ProjectDeps.commonsNetwork
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val libs = (project in file("target") / "libs")
  .settings(commonSettings, noPublishSettings, name := s"$baseName-libs")
  .aggregate(core, htmlunit)

lazy val app = project
  .settings(commonSettings, packageSettings, noPublishSettings, name := s"$baseName-app")
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging, WindowsPlugin /*, ClasspathJarPlugin, JDKPackagerPlugin*/)
  
lazy val webzinc = (project in file("."))
  .settings(commonSettings, noPublishSettings, name := s"$baseName-root")
  .aggregate(app)