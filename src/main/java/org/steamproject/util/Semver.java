package org.steamproject.util;


public final class Semver {
    private Semver() {}

    public static int compare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        String[] pa = normalize(a).split("\\.");
        String[] pb = normalize(b).split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? parsePart(pa[i]) : 0;
            int vb = i < pb.length ? parsePart(pb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static String normalize(String v) {
        
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
              int idx = v.indexOf('-');
        if (idx >= 0) v = v.substring(0, idx);
        return v;
    }

    private static int parsePart(String p) {
        try { return Integer.parseInt(p); } catch (Exception ex) { return 0; }
    }

    public static boolean isPreRelease(String version) {
        if (version == null) return false;
        String v = version.trim();
        if (v.contains("-")) return true;
        String[] parts = normalize(v).split("\\.");
        int major = parts.length > 0 ? parsePart(parts[0]) : 0;
        return major < 1;
    }

    /**
     * Vérifie si la version du joueur est compatible avec la version requise.
     * La version du joueur doit être >= à la version requise.
     * @param playerVersion La version que le joueur possède
     * @param requiredVersion La version minimale requise
     * @return true si compatible
     */
    public static boolean isCompatible(String playerVersion, String requiredVersion) {
        if (requiredVersion == null || requiredVersion.isEmpty()) return true;
        if (playerVersion == null || playerVersion.isEmpty()) return false;
        return compare(playerVersion, requiredVersion) >= 0;
    }

    /**
     * Incrémente la version minor
     */
    public static String incrementMinor(String version) {
        if (version == null) return "0.1.0";
        String[] parts = normalize(version).split("\\.");
        int major = parts.length > 0 ? parsePart(parts[0]) : 0;
        int minor = parts.length > 1 ? parsePart(parts[1]) : 0;
        return major + "." + (minor + 1) + ".0";
    }

    /**
     * Incrémente la version patch 
     */
    public static String incrementPatch(String version) {
        if (version == null) return "0.0.1";
        String[] parts = normalize(version).split("\\.");
        int major = parts.length > 0 ? parsePart(parts[0]) : 0;
        int minor = parts.length > 1 ? parsePart(parts[1]) : 0;
        int patch = parts.length > 2 ? parsePart(parts[2]) : 0;
        return major + "." + minor + "." + (patch + 1);
    }
}
