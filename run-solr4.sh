#!/bin/bash
# Downloads the spring-loaded lib if not existing and runs the full all-in-one
# (Alfresco + Share + Solr) using the runner project
springloadedfile=~/.m2/repository/org/springframework/springloaded/1.2.0.RELEASE/springloaded-1.2.0.RELEASE.jar

if [ ! -f $springloadedfile ]; then
mvn validate -Psetup
fi
cd solr
MAVEN_OPTS=" -Xms256m -Xmx2G -XX:PermSize=300m" mvn integration-test -Dmaven.tomcat.port=8082
#MAVEN_OPTS=" -Xms256m -Xmx2G -XX:PermSize=300m" mvn integration-test -Psolr-to-war -Dmaven.tomcat.port=8082