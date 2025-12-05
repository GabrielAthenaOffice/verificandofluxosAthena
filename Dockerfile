# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copiar arquivos do Maven Wrapper
COPY mvnw .
COPY .mvn .mvn

# Copiar pom.xml e baixar dependências (cache layer)
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copiar código fonte
COPY src src

# Build da aplicação (skip tests para build mais rápido)
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Criar usuário não-root para segurança
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copiar JAR do stage de build
COPY --from=build /app/target/*.jar app.jar

# Alterar ownership do arquivo
RUN chown appuser:appgroup app.jar

# Usar usuário não-root
USER appuser

# Porta padrão do Spring Boot
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Configurações de JVM otimizadas para containers
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
