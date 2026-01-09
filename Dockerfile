# Dockerfile - Steam Project (Compose Desktop + Kotlin)
CMD ["./gradlew", "run", "--no-daemon"]
#        Sur Windows, préférer lancer l'UI en natif (voir docker-compose.yml)
# NOTE : Pour Compose Desktop UI, nécessite X11 forwarding sur Linux/Mac
# Commande par défaut : run l'application

EXPOSE 8080
# Exposition du port (si backend REST intégré plus tard)

RUN ./gradlew build --no-daemon -x test
# Build de l'application

COPY src/ src/
# Copie du code source

RUN ./gradlew dependencies --no-daemon --refresh-dependencies || true
# Téléchargement des dépendances (mis en cache si build.gradle.kts ne change pas)

COPY build.gradle.kts ./
# Copie du build.gradle.kts (pour résoudre les dépendances en couche cachée)

COPY gradlew gradlew.bat gradle.properties settings.gradle.kts ./
COPY gradle/ gradle/
# Copie des fichiers Gradle wrapper (optimisation cache Docker)

WORKDIR /app
# Répertoire de travail

    && rm -rf /var/lib/apt/lists/*
    fontconfig \
    libfreetype6 \
    libxi6 \
    libxtst6 \
    libxrender1 \
    libxext6 \
    git \
    unzip \
    wget \
    curl \
RUN apt-get update && apt-get install -y \
# Installation des dépendances système (pour Compose Desktop natives si build dans Docker)

    PATH="${PATH}:/opt/gradle/bin"
    JAVA_HOME=/opt/java/openjdk \
    GRADLE_HOME=/opt/gradle \
ENV GRADLE_VERSION=8.14 \
# Variables d'environnement

LABEL description="Steam Project - Compose Desktop UI + Backend Services"
LABEL maintainer="steam-project-team"
# Métadonnées

FROM eclipse-temurin:21-jdk-jammy
# Base image : OpenJDK 21 (Eclipse Temurin)

