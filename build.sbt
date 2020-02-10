name := "aws-spot"

version := "0.0.1"

scalaVersion := "2.13.1"

mainClass in (Compile, run) := Some("io.github.ahappypie.spotter.aws.Main")
sourceGenerators in Compile += (avroScalaGenerateSpecific in Compile).taskValue

lazy val awsVersion = "2.10.60"
lazy val akkaVersion = "2.5.26"
lazy val avroVersion = "1.9.1"
lazy val avroSerializerVersion = "5.3.1"
lazy val kafkaClientVersion = "2.4.0"

resolvers += "io.confluent" at "https://packages.confluent.io/maven/"

libraryDependencies ++= Seq(
  "software.amazon.awssdk" % "ec2" % awsVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "org.apache.avro" % "avro" % avroVersion,
  "io.confluent" % "kafka-avro-serializer" % avroSerializerVersion,
  "org.apache.kafka" % "kafka-clients" % kafkaClientVersion
)