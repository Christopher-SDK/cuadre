# Imagen de la API. Para correr la suite de pruebas no hace falta: basta Java 21 y Docker (ver README).
FROM maven:3.9.11-eclipse-temurin-21 AS construccion
WORKDIR /cuadre
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 cuadre
WORKDIR /cuadre
COPY --from=construccion /cuadre/target/cuadre-1.0.0.jar cuadre.jar
USER cuadre
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "cuadre.jar"]
