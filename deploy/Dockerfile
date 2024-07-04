# Build stage
FROM docker.io/library/openjdk:21-slim AS build
WORKDIR /app

COPY . .
RUN apt update -y
RUN apt install -y git
RUN ./gradlew build

# Final stage
FROM docker.io/library/openjdk:21-slim
WORKDIR /app

# Set environment variables
ENV JAVA_OPTS=""
ENV MONGO_URL="mongodb://root:1234@localhost:27017/"
ENV JWT_SECRET="defaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkeydefaultsecretkey"
ENV JWT_EXPIRATION=3600
ENV SPRING_PROFILES_ACTIVE="local"

COPY --from=build /app/build/libs/*-all.jar /app/operator.jar

# Use the environment variables in the CMD instruction
CMD java $JAVA_OPTS -DMONGO_URL=$MONGO_URL -Djwt.secret=$JWT_SECRET -Djwt.expiration=$JWT_EXPIRATION -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -jar /app/operator.jar
