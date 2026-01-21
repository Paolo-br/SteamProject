package org.example.model

/**
 * Représente une plateforme de distribution de jeux vidéo.
 * 
 * 
 * Conformément au cahier des charges qui parle de "plateformes" au pluriel,
 * le projet supporte plusieurs plateformes de distribution, chacune pouvant
 * proposer des jeux pour différents supports matériels.
 * 
 * @see HardwareSupport pour les supports matériels
 */
data class DistributionPlatform(
    /** Identifiant unique de la plateforme */
    val id: String,
    
    /** Nom commercial de la plateforme */
    val name: String,
    
    /** Supports matériels pris en charge (PC, PS5, Xbox Series X, etc.) */
    val supportedHardware: List<String> = emptyList(),
    
    /** URL du site web de la plateforme */
    val websiteUrl: String? = null,
    
    /** Indique si la plateforme propose des jeux gratuits régulièrement */
    val offersFreeGames: Boolean = false,
    
    /** Description de la plateforme */
    val description: String? = null,
    
    /** Nombre de jeux disponibles sur cette plateforme (statistique) */
    val gameCount: Int = 0,
    
    /** Nombre d'utilisateurs inscrits sur cette plateforme (statistique) */
    val userCount: Int = 0
) {
    companion object {
        /**
         * Plateformes de distribution connues.
         * Ces constantes peuvent être utilisées dans l'application pour référencer
         * les principales plateformes sans risque d'erreur de frappe.
         */
        val STEAM = DistributionPlatform(
            id = "steam",
            name = "Steam",
            supportedHardware = listOf("PC", "Steam Deck", "Linux", "macOS"),
            websiteUrl = "https://store.steampowered.com",
            offersFreeGames = false,
            description = "Plateforme de distribution de Valve, leader du marché PC"
        )
        
        val PLAYSTATION_STORE = DistributionPlatform(
            id = "psn",
            name = "PlayStation Store",
            supportedHardware = listOf("PS4", "PS5", "PS Vita"),
            websiteUrl = "https://store.playstation.com",
            offersFreeGames = false,
            description = "Boutique officielle Sony pour PlayStation"
        )
        
        val XBOX_STORE = DistributionPlatform(
            id = "xbox",
            name = "Xbox Store",
            supportedHardware = listOf("Xbox One", "Xbox Series X", "Xbox Series S", "PC"),
            websiteUrl = "https://www.xbox.com/games/store",
            offersFreeGames = false,
            description = "Boutique Microsoft pour Xbox et PC (via Xbox Game Pass)"
        )
        
        val NINTENDO_ESHOP = DistributionPlatform(
            id = "nintendo",
            name = "Nintendo eShop",
            supportedHardware = listOf("Nintendo Switch", "Nintendo 3DS", "Wii U"),
            websiteUrl = "https://www.nintendo.com/store",
            offersFreeGames = false,
            description = "Boutique Nintendo pour Switch et consoles portables"
        )
        
        /**
         * Retourne toutes les plateformes de distribution connues.
         */
        fun getAllKnown(): List<DistributionPlatform> = listOf(
            STEAM,
            PLAYSTATION_STORE,
            XBOX_STORE,
            NINTENDO_ESHOP
        )
        
        /**
         * Recherche une plateforme par son identifiant.
         */
        fun fromId(id: String): DistributionPlatform? {
            return getAllKnown().find { it.id.equals(id, ignoreCase = true) }
        }
        
        /**
         * Infère la plateforme de distribution probable à partir d'un code de support matériel.
         * 
         */
        fun inferFromHardwareCode(hardwareCode: String?): DistributionPlatform {
            if (hardwareCode.isNullOrBlank()) return STEAM
            
            val code = hardwareCode.uppercase()
            
            return when {
                // Consoles Sony -> PlayStation Store
                code.startsWith("PS") || code == "PSP" || code == "PSV" -> PLAYSTATION_STORE
                
                // Consoles Microsoft -> Xbox Store
                code.startsWith("X") || code in listOf("XB", "X360", "XONE", "XS") -> XBOX_STORE
                
                // Consoles Nintendo -> Nintendo eShop
                code in listOf("WII", "WIIU", "NS", "N64", "GC", "GB", "GBA", "DS", "3DS", "NES", "SNES") -> NINTENDO_ESHOP
                
                // PC et autres -> Steam par défaut
                else -> STEAM
            }
        }
    }
}

/**
 * Représente un support matériel sur lequel un jeu peut s'exécuter.
 * 
 * @see DistributionPlatform pour les plateformes de distribution
 */
data class HardwareSupport(
    /** Code court du support (ex: "PS4", "PC", "NS") */
    val code: String,
    
    /** Nom complet du support (ex: "PlayStation 4", "Nintendo Switch") */
    val name: String,
    
    /** Fabricant du matériel (Sony, Microsoft, Nintendo, etc.) */
    val manufacturer: String,
    
    /** Catégorie de support */
    val category: HardwareCategory = HardwareCategory.CONSOLE
) {
    companion object {
        /**
         * Crée un HardwareSupport à partir d'un code CSV.
         */
        fun fromCode(code: String): HardwareSupport {
            val upperCode = code.uppercase()
            return when {
                upperCode == "PC" -> HardwareSupport(code, "PC", "Multiple", HardwareCategory.PC)
                upperCode.startsWith("PS") -> HardwareSupport(code, "PlayStation $code", "Sony", HardwareCategory.CONSOLE)
                upperCode in listOf("XB", "X360", "XONE", "XS") -> HardwareSupport(code, "Xbox $code", "Microsoft", HardwareCategory.CONSOLE)
                upperCode in listOf("WII", "WIIU", "NS") -> HardwareSupport(code, "Nintendo $code", "Nintendo", HardwareCategory.CONSOLE)
                upperCode in listOf("GB", "GBA", "DS", "3DS", "PSP", "PSV") -> HardwareSupport(code, code, "Various", HardwareCategory.HANDHELD)
                else -> HardwareSupport(code, code, "Unknown", HardwareCategory.CONSOLE)
            }
        }
    }
}

/**
 * Catégories de supports matériels.
 */
enum class HardwareCategory {
    /** Ordinateur personnel */
    PC,
    /** Console de salon */
    CONSOLE,
    /** Console portable */
    HANDHELD
}
