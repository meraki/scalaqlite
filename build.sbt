name := "scalaqlite"

organization := "com.meraki"

version := "0.9-RC1"

scalaVersion := "2.12.6"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"

scalacOptions in Test ++= Seq("-deprecation")

testOptions in Test += Tests.Argument("-Djava.library.path=target/native")

publishTo := Some(Resolver.file("file",  new File( "releases" )) )

lazy val packageSqlite3C = taskKey[Unit]("Packages only Sqlite3C.java")

packageSqlite3C := {
  (compile in Compile).value
  val cmd = Seq("jar", "-cf", s"${crossTarget.value}/sqlite3c-${version.value}.jar",
    "-C", (classDirectory in Compile).value.toString, "org/srhea/scalaqlite/Sqlite3C.class")
  if ((cmd ! streams.value.log) != 0) error("Couldn't package Sqlite3C")
}
