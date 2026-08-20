mvn spotless:apply -f core/java/pom.xml
mvn spotless:apply -f integrations/beast3/java/pom.xml
git diff --exit-code