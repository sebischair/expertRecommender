name := """07AKRec"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  filters,
  javaJdbc,
  cache,
  javaWs,
  ws,
  "com.google.code.gson" % "gson" % "2.8.0",
  "org.jsoup" % "jsoup" % "1.8.3",
  "org.webjars" %% "webjars-play" % "2.5.0",
  "org.webjars" % "jquery" % "3.2.1",
  "org.webjars" % "bootstrap" % "3.3.6",
  "org.webjars" % "materializecss" % "0.100.2",
  "org.webjars.bower" % "angular" % "1.6.1" force(),
  "org.webjars.bower" % "ngstorage" % "0.3.11",
  "org.webjars.bower" % "angular-resource" % "1.6.1",
  "org.webjars.bower" % "angular-checklist-model" % "0.10.0",
  "org.webjars.bower" % "angular-route" % "1.6.1",
  "org.webjars.bower" % "angular-material" % "1.1.4",
  "org.webjars.bower" % "angular-sanitize" % "1.6.9",
  "org.webjars" % "d3js" % "3.5.17",
  "org.mongodb.morphia" % "morphia" % "1.2.1",
  "org.mongodb" % "mongo-java-driver" % "3.5.0",

  // DKPro Core components
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core-asl" % "1.7.0",
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.io.text-asl" % "1.7.0",
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.tokit-asl" % "1.7.0",
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.opennlp-asl" % "1.7.0",
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.io.xml-asl" % "1.7.0", // for testing only!
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.treetagger-asl" % "1.7.0",
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.matetools-gpl" % "1.7.0",
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.languagetool-asl" % "1.7.0",
  "de.tudarmstadt.ukp.dkpro.core" % "de.tudarmstadt.ukp.dkpro.core.mallet-asl" % "1.7.0",
  // Apache RUTA (initial: 2.3.0)
  "org.apache.uima" % "ruta-core" % "2.3.0",
  // HTML parser
  "net.htmlparser.jericho" % "jericho-html" % "3.3",
  "junit" % "junit" % "4.12",
  "com.sun.jersey" % "jersey-bundle" % "1.19.1",
  "javax.ws.rs" % "jsr311-api" % "1.1.1",
  "com.sun.jersey" % "jersey-client" % "1.9.1",

  //For QueryExecutor
  "org.apache.jena" % "jena-core" % "3.1.0",
  "org.apache.jena" % "jena-arq" % "3.1.0",

  //Google Trends
  "commons-configuration" % "commons-configuration" % "1.6",
  "org.apache.commons" % "commons-lang3" % "3.0",
  "commons-httpclient" % "commons-httpclient" % "3.1",

  //Apache TIKA
  "org.apache.tika" % "tika-parsers" % "1.4",

  //AYLIEN
  "com.aylien.textapi" % "client" % "0.6.1",

  //Latent Semantic Analysis
  "nz.ac.waikato.cms.weka" % "weka-dev" % "3.9.2",
  "com.github.fommil.netlib" % "all" % "1.1.2" pomOnly(),
  "cc.mallet" % "mallet" % "2.0.8",
  "de.julielab" % "aliasi-lingpipe" % "4.1.0",
  "com.github.fracpete" % "snowball-stemmers-weka-package" % "1.0.1"
)

dependencyOverrides ++= Set(
  "org.webjars" % "jquery" % "3.2.1"
)
