# Script PowerShell - Build Docker image du projet Steam
# Usage : .\docker-build.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Steam Project - Docker Build" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier que Docker est installé
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "❌ Docker n'est pas installé ou pas dans le PATH" -ForegroundColor Red
    Write-Host "   Installer Docker Desktop : https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    exit 1
}

Write-Host "✅ Docker détecté" -ForegroundColor Green

# Build de l'image
Write-Host ""
Write-Host "🔨 Build de l'image Docker..." -ForegroundColor Cyan
docker build -t steam-project:latest .

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ Image construite avec succès : steam-project:latest" -ForegroundColor Green
    Write-Host ""
    Write-Host "Prochaines étapes :" -ForegroundColor Yellow
    Write-Host "  1. Lancer Kafka : docker-compose up -d kafka" -ForegroundColor White
    Write-Host "  2. Tester l'app : docker run --rm -it steam-project:latest ./gradlew test" -ForegroundColor White
    Write-Host "  3. Lancer l'UI en natif (pas Docker) : .\gradlew run" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "❌ Échec du build Docker" -ForegroundColor Red
    exit 1
}

