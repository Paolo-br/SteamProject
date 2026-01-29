package org.steamproject.model;

/**
 * Représente les plateformes de jeu supportées.
 * Cette interface utilise le concept de types scellés.
 * Conformément au cours, les types scellés permettent de limiter les sous-types
 * possibles d'un type, ce qui est utile pour les cas où on veut une hiérarchie
 * fermée (comme les différentes catégories de plateformes).
 */
public sealed interface Platform permits Platform.Console, Platform.PC, Platform.Handheld {
    
    /**
     * Retourne le nom de la plateforme.
     */
    String getName();
    
    /**
     * Retourne le code court de la plateforme (pour compatibilité avec les données existantes).
     */
    String getCode();
    
    /**
     * Vérifie si la plateforme supporte les mods.
     */
    boolean supportsModding();
    
    /**
     * Représente les plateformes de type console (PlayStation, Xbox, Nintendo, etc.)
     */
    record Console(String name, String code, String manufacturer) implements Platform {
        
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
            return false; // Les consoles ne supportent généralement pas les mods
        }
    }
    
    /**
     * Représente les plateformes PC.
     */
    record PC(String name, String code) implements Platform {
        
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
     * Représente les plateformes portables (Game Boy, PSP, DS, etc.)
     */
    record Handheld(String name, String code, String manufacturer) implements Platform {
        
        public Handheld {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Le nom de la plateforme portable est obligatoire");
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
    
    /**

    static Platform fromCode(String code) {
        if (code == null || code.isBlank()) {
            return new PC("PC", "PC");
        }
        
        // Utilisation de switch expression avec case multiples et valeur de retour.
        return switch (code.toUpperCase()) {
            case "PC" -> new PC("PC", "PC");
            
            // Consoles Sony
            case "PS", "PS2", "PS3", "PS4", "PS5" -> 
                new Console("PlayStation " + code.substring(2), code, "Sony");
            
            // Consoles Microsoft
            case "XB", "X360", "XONE", "XS" -> 
                new Console("Xbox " + code, code, "Microsoft");
            
            // Consoles Nintendo
            case "WII", "WIIU", "NS" -> 
                new Console("Nintendo " + code, code, "Nintendo");
            case "N64", "GC" -> 
                new Console(code.equals("N64") ? "Nintendo 64" : "GameCube", code, "Nintendo");
            
            // Portables
            case "GB", "GBA" -> 
                new Handheld("Game Boy" + (code.equals("GBA") ? " Advance" : ""), code, "Nintendo");
            case "DS", "3DS" -> 
                new Handheld("Nintendo " + code, code, "Nintendo");
            case "PSP", "PSV" -> 
                new Handheld(code.equals("PSP") ? "PlayStation Portable" : "PlayStation Vita", code, "Sony");
            
            // Par défaut, traiter comme PC
            default -> new PC(code, code);
        };
    }
    
    /**
     */
    static String getDescription(Platform platform) {
        return switch (platform) {
            case Console c -> "Console " + c.manufacturer() + " : " + c.name();
            case PC p -> "Plateforme PC : " + p.name();
            case Handheld h -> "Portable " + h.manufacturer() + " : " + h.name();
        };
    }
}
