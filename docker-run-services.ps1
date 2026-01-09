# Script PowerShell - Lancer les services Docker (Kafka, Backend, etc.)
# L'UI Compose Desktop tourne en NATIF Windows (pas dans Docker)
# Usage : .\docker-run-services.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Steam Project - Démarrage Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier Docker Compose
if (-not (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    if (-not (docker compose version 2>$null)) {
        Write-Host "❌ Docker Compose n'est pas disponible" -ForegroundColor Red
        exit 1
    }
    $composeCmd = "docker compose"
} else {
    $composeCmd = "docker-compose"
}

Write-Host "✅ Docker Compose détecté" -ForegroundColor Green

# Lancer les services (Kafka + Zookeeper pour l'instant)
Write-Host ""
Write-Host "🚀 Démarrage de Kafka + Zookeeper..." -ForegroundColor Cyan
Write-Host ""

& $composeCmd up -d kafka

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ Services démarrés avec succès" -ForegroundColor Green
    Write-Host ""
    Write-Host "Services actifs :" -ForegroundColor Yellow
    Write-Host "  • Kafka       : localhost:29092 (depuis Windows)" -ForegroundColor White
    Write-Host "  • Zookeeper   : localhost:2181" -ForegroundColor White
    Write-Host ""
    Write-Host "Commandes utiles :" -ForegroundColor Yellow
    Write-Host "  • Voir logs Kafka   : $composeCmd logs -f kafka" -ForegroundColor White
    Write-Host "  • Arrêter services  : $composeCmd down" -ForegroundColor White
    Write-Host "  • Lancer l'UI       : .\gradlew run" -ForegroundColor White
    Write-Host ""
    Write-Host "📝 NOTE : L'UI Compose Desktop tourne en NATIF (pas Docker)" -ForegroundColor Cyan
    Write-Host "          Utilise .\gradlew run pour afficher l'interface" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "❌ Échec du démarrage des services" -ForegroundColor Red
    exit 1
}

