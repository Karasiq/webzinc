import sbt._

object ProjectDeps {
  type Deps = Seq[ModuleID]

  object akka {
    val version = "2.5.2"
    val httpVersion = "10.0.9"

    def actors: Deps = Seq(
      "com.typesafe.akka" %% "akka-actor" % version
    )

    def streams: Deps = Seq(
      "com.typesafe.akka" %% "akka-stream" % version
    )

    def http: Deps = Seq(
      "com.typesafe.akka" %% "akka-http" % httpVersion
    )

    def persistence: Deps = Seq(
      "com.typesafe.akka" %% "akka-persistence" % version
    )

    def testKit: Deps = Seq(
      "com.typesafe.akka" %% "akka-testkit" % version,
      "com.typesafe.akka" %% "akka-stream-testkit" % version,
      "com.typesafe.akka" %% "akka-http-testkit" % httpVersion
    )

    def slf4j: Deps = Seq(
      "com.typesafe.akka" %% "akka-slf4j" % version
    )

    def all: Deps = {
      actors ++ streams ++ http ++ persistence // ++ testKit.map(_ % "test")
    }
  }

  def scalaTest: Deps = Seq(
    "org.scalatest" %% "scalatest" % "3.0.3"
  )

  def commonsConfigs: Deps = Seq(
    "com.github.karasiq" %% "commons-configs" % "1.0.8"
  )

  def commonsNetwork: Deps = Seq(
    "com.github.karasiq" %% "commons-network" % "1.0.8"
  )

  def htmlUnit: Deps = Seq(
    "net.sourceforge.htmlunit" % "htmlunit" % "2.27"
  )

  def jsoup: Deps = Seq(
    "org.jsoup" % "jsoup" % "1.11.1"
  )

  def apacheCommonsIO: Deps = Seq(
    "commons-io" % "commons-io" % "2.6"
  )
}