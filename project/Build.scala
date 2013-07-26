/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt._
import Keys._
import java.io.File

import scala.xml.{Node, Elem}
import scala.xml.transform.{RewriteRule, RuleTransformer}

object KafkaBuild extends Build {

  val commonSettings = Seq(
    version := "0.7.1-fs-a",
    organization := "kafka",
    scalacOptions ++= Seq("-deprecation", "-unchecked"),
    scalaVersion := "2.9.2",
    crossScalaVersions := Seq("2.9.1", "2.9.2", "2.10.2"),
    parallelExecution in Test := false, // Prevent tests from overrunning each other
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo <<= (version) { v =>
      Some("thirdparty-releases" at "http://nexus.prod.foursquare.com/nexus/content/repositories/thirdparty/")
    },

    // Hack to work around SBT bug generating scaladoc for projects with no dependencies.
    // https://github.com/harrah/xsbt/issues/85
    unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist")),
    
    libraryDependencies <++= (scalaVersion) { scalaVersion =>
      Seq(
        "org.scalatest"         %% "scalatest"    % "1.9.1" % "test",
        "junit"                 %  "junit"        % "4.1"   % "test",
        "org.easymock"          %  "easymock"     % "3.0"   % "test",
        "log4j"                 %  "log4j"        % "1.2.15",
        "net.sf.jopt-simple"    %  "jopt-simple"  % "3.2",
        "org.slf4j"             %  "slf4j-simple" % "1.6.4",
        "org.xerial.snappy" % "snappy-java" % "1.0.4.1"
      ) ++ (
        if (scalaVersion == "2.10.2") {
          Seq("org.scala-lang"  %  "scala-actors"  % "2.10.2")
        } else { Nil }
      )
    },
    // The issue is going from log4j 1.2.14 to 1.2.15, the developers added some features which required
    // some dependencies on various sun and javax packages.
    ivyXML := <dependencies>
        <exclude module="javax"/>
        <exclude module="jmxri"/>
        <exclude module="jmxtools"/>
        <exclude module="mail"/>
        <exclude module="jms"/>
        <dependency org="org.apache.zookeeper" name="zookeeper" rev="3.3.4">
          <exclude org="log4j" module="log4j"/>
          <exclude org="jline" module="jline"/>
        </dependency>
        <dependency org="com.github.sgroschupf" name="zkclient" rev="0.1">
        </dependency>
        <dependency org="org.apache.zookeeper" name="zookeeper" rev="3.3.4">
          <exclude module="log4j"/>
          <exclude module="jline"/>
        </dependency>
      </dependencies>,

    credentials ++= {
      val sonatype = ("Sonatype Nexus Repository Manager", "nexus.prod.foursquare.com")
      def loadMavenCredentials(file: java.io.File) : Seq[Credentials] = {
        xml.XML.loadFile(file) \ "servers" \ "server" map (s => {
          val host = (s \ "id").text
          val realm = if (host == sonatype._2) sonatype._1 else "Unknown"
          Credentials(realm, host, (s \ "username").text, (s \ "password").text)
        })
      }
      val ivyCredentials   = Path.userHome / ".ivy2" / ".credentials"
      val mavenCredentials = Path.userHome / ".m2"   / "settings.xml"
      (ivyCredentials.asFile, mavenCredentials.asFile) match {
        case (ivy, _) if ivy.canRead => Credentials(ivy) :: Nil
        case (_, mvn) if mvn.canRead => loadMavenCredentials(mvn)
        case _ => Nil
      }
    }
  )

  val hadoopSettings = Seq(
    libraryDependencies ++= Seq(
      "org.apache.avro"      % "avro"               % "1.4.0",
      "org.apache.pig"       % "pig"                % "0.8.0",
      "commons-logging"      % "commons-logging"    % "1.0.4",
      "org.codehaus.jackson" % "jackson-core-asl"   % "1.5.5",
      "org.codehaus.jackson" % "jackson-mapper-asl" % "1.5.5",
      "org.apache.hadoop"    % "hadoop-core"        % "0.20.2"
    ),
    ivyXML := 
       <dependencies>
         <exclude module="netty"/>
         <exclude module="javax"/>
         <exclude module="jmxri"/>
         <exclude module="jmxtools"/>
         <exclude module="mail"/>
         <exclude module="jms"/>
         <dependency org="org.apache.hadoop" name="hadoop-core" rev="0.20.2">
           <exclude org="junit" module="junit"/>
         </dependency>
         <dependency org="org.apache.pig" name="pig" rev="0.8.0">
           <exclude org="junit" module="junit"/>
         </dependency>
       </dependencies>
  )

  val runRat = TaskKey[Unit]("run-rat-task", "Runs Apache rat on Kafka")
  val runRatTask = runRat := {
    "bin/run-rat.sh" !
  }

  lazy val kafka    = Project(id = "Kafka", base = file(".")).aggregate(core, examples, contrib, perf).settings((commonSettings ++ runRatTask): _*)
  lazy val core     = Project(id = "core", base = file("core")).settings(commonSettings: _*)
  lazy val examples = Project(id = "java-examples", base = file("examples")).settings(commonSettings :_*) dependsOn (core)
  lazy val perf     = Project(id = "perf", base = file("perf")).settings((Seq(name := "kafka-perf") ++ commonSettings):_*) dependsOn (core)

  lazy val contrib        = Project(id = "contrib", base = file("contrib")).aggregate(hadoopProducer, hadoopConsumer).settings(commonSettings :_*)
  lazy val hadoopProducer = Project(id = "hadoop-producer", base = file("contrib/hadoop-producer")).settings(hadoopSettings ++ commonSettings: _*) dependsOn (core)
  lazy val hadoopConsumer = Project(id = "hadoop-consumer", base = file("contrib/hadoop-consumer")).settings(hadoopSettings ++ commonSettings: _*) dependsOn (core)


}
