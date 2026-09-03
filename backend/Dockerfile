# ============================================================
# 悦享音乐 后端通用 Dockerfile（无编译版）
# 前置：先在 IDE 或终端执行 mvnw package -DskipTests
# 用法：docker compose build
# ============================================================
FROM eclipse-temurin:17-jre
WORKDIR /app

ARG SERVICE_NAME
COPY ${SERVICE_NAME}/target/*.jar app.jar

RUN mkdir -p /app/data/file

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
