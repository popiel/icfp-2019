name := "icfp-2019-invisible-imp"

scalaVersion := "2.13.0"

// logLevel in Global := Level.Debug

libraryDependencies += "org.scalactic"  %% "scalactic"       % "3.0.8"
libraryDependencies += "org.scalatest"  %% "scalatest"       % "3.0.8" % "test"
libraryDependencies += "org.slf4j"      %  "slf4j-api"       % "1.7.26"
libraryDependencies += "ch.qos.logback" %  "logback-classic" % "1.2.3"
libraryDependencies += "io.spray"       %% "spray-json"      % "1.3.5"

libraryDependencies += "com.typesafe.akka" %% "akka-actor"   % "2.5.23"
libraryDependencies += "com.typesafe.akka" %% "akka-remote"  % "2.5.23"
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j"   % "2.5.23"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.5.23"

// scalacOptions += "-P:artima-supersafe:config-file:project/supersafe.cfg"

fork in (Test,run) := true
publishArtifact in Test := true
