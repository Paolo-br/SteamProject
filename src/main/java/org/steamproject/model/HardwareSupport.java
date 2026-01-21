package org.steamproject.model;

/**
 * Représente les supports matériels de jeu (hardware).
 * Cette interface utilise le concept de types scellés (sealed) vu en cours
 * pour définir une hiérarchie fermée de types de supports matériels.
 * 
 * @see DistributionPlatform pour les plateformes de distribution
 */
public sealed interface HardwareSupport permits HardwareSupport.Console, HardwareSupport.PC, HardwareSupport.Handheld {
    
    /**
     * Retourne le nom complet du support matériel.
     */
    String getName();
    
    /**
     * Retourne le code court du support (pour compatibilité avec les données CSV existantes).
     * Ex: "PS4", "PC", "3DS", "WII"
     */
    String getCode();
    
    /**
     * Vérifie si le support matériel permet l'installation de mods.
     */
    boolean supportsModding();

    /**
     * Retourne le fabricant du support matériel.
     */
    default String getManufacturer() {
        return switch (this) {
            case Console c -> c.manufacturer();
            case Handheld h -> h.manufacturer();
            case PC p -> "Multiple";
        };
    }
    
    // ========== TYPES DE SUPPORTS MATÉRIELS ==========
    
    /**
     * Représente une console de salon (PlayStation, Xbox, Nintendo, etc.)
     * Support fixe connecté à un téléviseur.
     */
    record Console(String name, String code, String manufacturer) implements HardwareSupport {
        
        public Console {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Le nom de la console est obligatoire");
            }
        }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public String getCode() { return code; }
        
        @Override
        public boolean supportsModding() { 
            return false; // Les consoles ne supportent généralement pas les mods officiellement
        }
    }
    
    /**
     * Représente un ordinateur personnel (Windows, macOS, Linux).
     * Support le plus ouvert en termes de modding et personnalisation.
     */
    record PC(String name, String code) implements HardwareSupport {
        
        public PC {
            if (name == null) name = "PC";
            if (code == null) code = "PC";
        }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public String getCode() { return code; }
        
        @Override
        public boolean supportsModding() { 
            return true; // Les PC supportent généralement les mods
        }
    }
    
    /**
     * Représente une console portable (Game Boy, PSP, DS, Switch en mode portable, etc.)
     * Support mobile avec écran intégré.
     */
    record Handheld(String name, String code, String manufacturer) implements HardwareSupport {
        
        public Handheld {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Le nom du support portable est obligatoire");
            }
        }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public String getCode() { return code; }
        
        @Override
        public boolean supportsModding() { 
            return false; 
        }
    }
    
    // ========== MÉTHODES UTILITAIRES ==========
    
    /**
     * Factory method pour créer un HardwareSupport à partir d'un code existant.
     * Compatible avec les codes présents dans le fichier CSV (PS4, Wii, PC, etc.)
     */
    static HardwareSupport fromCode(String code) {
        if (code == null || code.isBlank()) {
            return new PC("PC", "PC");
        }
        
        // Switch expression avec pattern matching
        return switch (code.toUpperCase()) {
            case "PC" -> new PC("PC", "PC");
            
            // Consoles Sony
            case "PS", "PS2", "PS3", "PS4", "PS5" -> 
                new Console("PlayStation " + (code.length() > 2 ? code.substring(2) : ""), code, "Sony");
            
            // Consoles Microsoft
            case "XB", "X360", "XONE", "XS" -> 
                new Console("Xbox " + code, code, "Microsoft");
            
            // Consoles Nintendo de salon
            case "WII" -> new Console("Nintendo Wii", code, "Nintendo");
            case "WIIU" -> new Console("Nintendo Wii U", code, "Nintendo");
            case "NS" -> new Console("Nintendo Switch", code, "Nintendo");
            case "N64" -> new Console("Nintendo 64", code, "Nintendo");
            case "GC" -> new Console("GameCube", code, "Nintendo");
            case "NES" -> new Console("Nintendo Entertainment System", code, "Nintendo");
            case "SNES" -> new Console("Super Nintendo", code, "Nintendo");
            
            // Consoles Sega
            case "GEN" -> new Console("Sega Genesis", code, "Sega");
            case "DC" -> new Console("Sega Dreamcast", code, "Sega");
            case "SAT" -> new Console("Sega Saturn", code, "Sega");
            
            // Consoles Atari
            case "2600" -> new Console("Atari 2600", code, "Atari");
            
            // Portables Nintendo
            case "GB" -> new Handheld("Game Boy", code, "Nintendo");
            case "GBA" -> new Handheld("Game Boy Advance", code, "Nintendo");
            case "DS" -> new Handheld("Nintendo DS", code, "Nintendo");
            case "3DS" -> new Handheld("Nintendo 3DS", code, "Nintendo");
            
            // Portables Sony
            case "PSP" -> new Handheld("PlayStation Portable", code, "Sony");
            case "PSV" -> new Handheld("PlayStation Vita", code, "Sony");
            
            // Par défaut, traiter comme console générique
            default -> new Console(code, code, "Unknown");
        };
    }
    
    /**
     * Retourne une description lisible du support matériel.
     * Utilise le pattern matching sur les types scellés.
     */
    static String getDescription(HardwareSupport hardware) {
        return switch (hardware) {
            case Console c -> "Console " + c.manufacturer() + " : " + c.name();
            case PC p -> "Ordinateur : " + p.name();
            case Handheld h -> "Portable " + h.manufacturer() + " : " + h.name();
        };
    }

    /**
     * Détermine la catégorie générale du support.
     */
    static String getCategory(HardwareSupport hardware) {
        return switch (hardware) {
            case Console c -> "Console de salon";
            case PC p -> "Ordinateur personnel";
            case Handheld h -> "Console portable";
        };
    }
}
