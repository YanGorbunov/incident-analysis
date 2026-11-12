# syntax=docker/dockerfile:1

# --- фронтенд -----------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN npm ci || npm install
COPY frontend/ ./
RUN npm run build

# --- бэкенд (фронтенд кладётся в static одного jar) ----------------------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /src/backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY backend/src ./src
COPY --from=frontend /src/frontend/dist /src/frontend/dist
RUN ./gradlew --no-daemon bootJar -x test \
 && java -Djarmode=tools -jar build/libs/incident-analytics-*.jar extract --layers --launcher --destination /app

# --- рантайм -----------------------------------------------------------
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=backend /app/dependencies/ ./
COPY --from=backend /app/spring-boot-loader/ ./
COPY --from=backend /app/snapshot-dependencies/ ./
COPY --from=backend /app/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
