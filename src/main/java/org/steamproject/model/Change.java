package org.steamproject.model;

public class Change {
    private PatchType type;
    private String description;

    public Change() {}

    public Change(PatchType type, String description) {
        this.type = type;
        this.description = description;
    }

    public PatchType getType() { return type; }
    public void setType(PatchType type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "Change{" + "type=" + type + ", description='" + description + '\'' + '}';
    }
}
