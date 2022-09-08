ThisBuild / version := "1.0.0"

ThisBuild / scalaVersion := "2.13.8"

val bitcoinsV = "1.9.3-17-018a6e58-SNAPSHOT"
val awsSdkV = "1.12.296"
val awsLambdaCoreV = "1.2.1"
val awsRuntimeV = "2.1.1"

lazy val `voltage-auto-unlock` = (project in file("."))
  .settings(
    name := "voltage-auto-unlock",
    resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
    libraryDependencies ++= Seq(
      "org.bitcoin-s" %% "bitcoin-s-lnd-rpc" % bitcoinsV,
      "com.amazonaws" % "aws-java-sdk-secretsmanager" % awsSdkV,
      "com.amazonaws" % "aws-lambda-java-runtime-interface-client" % awsRuntimeV,
      "com.amazonaws" % "aws-lambda-java-core" % awsLambdaCoreV
    )
  )

// copied from TBC backend
assembly / assemblyMergeStrategy := {
  case "META-INF/javamail.default.address.map"            => MergeStrategy.first
  case "META-INF/services/io.grpc.LoadBalancerProvider"   => MergeStrategy.first
  case "META-INF/services/io.grpc.ManagedChannelProvider" => MergeStrategy.first
  case "META-INF/services/io.grpc.ServerProvider"         => MergeStrategy.first
  case "META-INF/services/io.grpc.NameResolverProvider"   => MergeStrategy.first
  case "META-INF/services/org.flywaydb.core.extensibility.Plugin" =>
    MergeStrategy.first
  case PathList("META-INF", _ @_*)         => MergeStrategy.discard
  case PathList("reference.conf", _ @_*)   => MergeStrategy.concat
  case PathList("application.conf", _ @_*) => MergeStrategy.concat
  case "reference.conf"                    => MergeStrategy.concat
  case "application.conf"                  => MergeStrategy.concat
  case PathList("logback.xml", _ @_*)      => MergeStrategy.concat
  case PathList("logback-test.xml", _ @_*) => MergeStrategy.concat
  case PathList("cacerts", _ @_*)          => MergeStrategy.first
  case "cacerts"                           => MergeStrategy.first

  // ignore bitcoin-s tor files
  case PathList("geoip", _ @_*)    => MergeStrategy.discard
  case "linux_64/tor"              => MergeStrategy.discard
  case "linux_64/tor.real"         => MergeStrategy.discard
  case "linux_64/libssl.so.1.1"    => MergeStrategy.discard
  case "linux_64/libcrypto.so.1.1" => MergeStrategy.discard
  case "linux_64/libstdc++/libstdc++.so.6" =>
    MergeStrategy.discard
  case "linux_64/LICENSE"            => MergeStrategy.discard
  case "osx_64/libevent-2.1.7.dylib" => MergeStrategy.discard
  case "osx_64/tor"                  => MergeStrategy.discard
  case "osx_64/tor.real"             => MergeStrategy.discard
  case "osx_64/LICENSE"              => MergeStrategy.discard
  case "windows_64/libcrypto-1_1-x64.dll" =>
    MergeStrategy.discard
  case "windows_64/libevent_core-2-1-7.dll" =>
    MergeStrategy.discard
  case "windows_64/libevent_extra-2-1-7.dll" =>
    MergeStrategy.discard
  case "windows_64/libevent-2-1-7.dll" =>
    MergeStrategy.discard
  case "windows_64/libgcc_s_seh-1.dll" =>
    MergeStrategy.discard
  case "windows_64/libssl-1_1-x64.dll" =>
    MergeStrategy.discard
  case "windows_64/libssp-0.dll" =>
    MergeStrategy.discard
  case "windows_64/tor.exe"          => MergeStrategy.discard
  case "windows_64/zlib1.dll"        => MergeStrategy.discard
  case "windows_64/LICENSE"          => MergeStrategy.discard
  case "bip39-wordlists/english.txt" => MergeStrategy.discard

  case _ => MergeStrategy.first
}

enablePlugins(AssemblyPlugin)
