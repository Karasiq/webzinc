logLevel := Level.Warn

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.3")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.7")

addSbtPlugin("ohnosequences" % "sbt-github-release" % "0.5.0")

resolvers += "repo.jenkins-ci.org" at "http://repo.jenkins-ci.org/public" // org.jenkins-ci#annotation-indexer;1.4: not found