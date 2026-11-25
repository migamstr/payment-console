# 多阶段构建：maven 编译 → jre 运行
# 本机没有 Maven/Docker，所以这个 Dockerfile 没有被本地验证过。
# 用户环境里 `docker compose build` 应该能直接跑通。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
