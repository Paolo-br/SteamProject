package org.steamproject.model;

import java.util.List;

/**
 * Représente une plateforme de distribution de jeux vidéo.
 * Conformément au cahier des charges qui parle de "plateformes" au pluriel,
 * le projet doit supporter plusieurs plateformes de distribution, chacune
 * pouvant proposer des jeux pour différents supports matériels.
 * 
 * Cette classe utilise le concept de types scellés (sealed) vu en cours
 * pour définir les plateformes connues tout en permettant l'extension.
 */
public sealed interface DistributionPlatform permits 
    DistributionPlatform.Steam, 
    DistributionPlatform.PlayStationStore,
    DistributionPlatform.XboxStore,
    DistributionPlatform.NintendoEShop,
    DistributionPlatform.Other {

    /**
     * Identifiant unique de la plateforme.
     */
    String getId();

    /**
     * Nom commercial de la plateforme.
     */
    String getName();

    /**
     * Supports matériels pris en charge par cette plateforme.
     * Ex: Steam supporte PC ; PlayStation Store supporte PS4, PS5 ; etc.
     */
    List<String> getSupportedHardware();

    /**
     * URL du site web de la plateforme.
     */
    String getWebsiteUrl();

    /**
     * Indique si la plateforme propose des jeux gratuits régulièrement.
     */
    boolean offersFreeGames();

    // ========== PLATEFORMES DE DISTRIBUTION CONNUES ==========

    /**
     * Steam - Plateforme de Valve, principalement PC.
     */
    record Steam() implements DistributionPlatform {
        @Override public String getId() { return "steam"; }
        @Override public String getName() { return "Steam"; }
        @Override public List<String> getSupportedHardware() { 
            return List.of("PC", "Steam Deck", "Linux", "macOS"); 
        }
        @Override public String getWebsiteUrl() { return "https://store.steampowered.com"; }
        @Override public boolean offersFreeGames() { return false; }
    }

    

    /**
     * PlayStation Store - Plateforme de Sony pour les consoles PlayStation.
     */
    record PlayStationStore() implements DistributionPlatform {
        @Override public String getId() { return "psn"; }
        @Override public String getName() { return "PlayStation Store"; }
        @Override public List<String> getSupportedHardware() { 
            return List.of("PS4", "PS5", "PS Vita"); 
        }
        @Override public String getWebsiteUrl() { return "https://store.playstation.com"; }
        @Override public boolean offersFreeGames() { return false; }
    }

    /**
     * Xbox Store - Plateforme de Microsoft pour Xbox et PC (Xbox Game Pass).
     */
    record XboxStore() implements DistributionPlatform {
        @Override public String getId() { return "xbox"; }
        @Override public String getName() { return "Xbox Store"; }
        @Override public List<String> getSupportedHardware() { 
            return List.of("Xbox One", "Xbox Series X", "Xbox Series S", "PC"); 
        }
        @Override public String getWebsiteUrl() { return "https://www.xbox.com/games/store"; }
        @Override public boolean offersFreeGames() { return false; }
    }

    /**
     * Nintendo eShop - Plateforme de Nintendo pour Switch et consoles portables.
     */
    record NintendoEShop() implements DistributionPlatform {
        @Override public String getId() { return "nintendo"; }
        @Override public String getName() { return "Nintendo eShop"; }
        @Override public List<String> getSupportedHardware() { 
            return List.of("Nintendo Switch", "Nintendo 3DS", "Wii U"); 
        }
        @Override public String getWebsiteUrl() { return "https://www.nintendo.com/store"; }
        @Override public boolean offersFreeGames() { return false; }
    }

    

    /**
     * Autre plateforme personnalisée.
     * Permet d'étendre le système sans modifier le code source.
     */
    record Other(
        String id, 
        String name, 
        List<String> supportedHardware, 
        String websiteUrl
    ) implements DistributionPlatform {
        
        public Other {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("L'identifiant de la plateforme est obligatoire");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Le nom de la plateforme est obligatoire");
            }
            if (supportedHardware == null) {
                supportedHardware = List.of();
            }
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public List<String> getSupportedHardware() { return supportedHardware; }
        @Override public String getWebsiteUrl() { return websiteUrl != null ? websiteUrl : ""; }
        @Override public boolean offersFreeGames() { return false; }
    }

    // ========== MÉTHODES UTILITAIRES ==========

    /**
     * Retourne toutes les plateformes de distribution connues.
     */
    static List<DistributionPlatform> getAllKnownPlatforms() {
        return List.of(
            new Steam(),
            new PlayStationStore(),
            new XboxStore(),
            new NintendoEShop()
        );
    }

    /**
     * Recherche une plateforme par son identifiant.
     */
    static DistributionPlatform fromId(String id) {
        if (id == null || id.isBlank()) {
            return new Steam(); // Par défaut
        }
        return switch (id.toLowerCase()) {
            case "steam" -> new Steam();
            case "psn", "playstation", "ps_store" -> new PlayStationStore();
            case "xbox", "xbox_store", "microsoft" -> new XboxStore();
            case "nintendo", "eshop", "nintendo_eshop" -> new NintendoEShop();
            
            default -> new Other(id, id, List.of(), null);
        };
    }

    /**
     * Détermine la plateforme de distribution probable à partir d'un code de support matériel.
     * 
     * Cette méthode est utilisée pour la rétrocompatibilité avec les données
     * existantes qui ne contiennent que des codes de support (PS4, Wii, PC, etc.)
     * sans information sur la plateforme de distribution.
     */
    static DistributionPlatform inferFromHardwareCode(String hardwareCode) {
        if (hardwareCode == null || hardwareCode.isBlank()) {
            return new Steam();
        }
        
        String code = hardwareCode.toUpperCase();
        
        // Consoles Sony -> PlayStation Store
        if (code.startsWith("PS") || code.equals("PSP") || code.equals("PSV")) {
            return new PlayStationStore();
        }
        
        // Consoles Microsoft -> Xbox Store
        if (code.startsWith("X") || code.equals("XB") || code.equals("X360") || 
            code.equals("XONE") || code.equals("XS")) {
            return new XboxStore();
        }
        
        // Consoles Nintendo -> Nintendo eShop
        if (code.equals("WII") || code.equals("WIIU") || code.equals("NS") ||
            code.equals("N64") || code.equals("GC") || code.equals("GB") ||
            code.equals("GBA") || code.equals("DS") || code.equals("3DS")) {
            return new NintendoEShop();
        }
        
        // PC et autres -> Steam par défaut
        return new Steam();
    }

    /**
     * Description utilisant le pattern matching sur types scellés.
     */
    static String getDescription(DistributionPlatform platform) {
        return switch (platform) {
            case Steam s -> "Steam : La plateforme gaming de Valve";
            case PlayStationStore ps -> "PlayStation Store : Boutique officielle Sony";
            case XboxStore x -> "Xbox Store : Boutique Microsoft pour Xbox et PC";
            case NintendoEShop n -> "Nintendo eShop : Boutique Nintendo Switch et 3DS";
            case Other o -> "Plateforme : " + o.name();
        };
    }
}
