package construction.dto;

import java.util.List;

public class EmptyProjectRequest {
    private String name;
    private List<String> hierarchyLevels;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getHierarchyLevels() { return hierarchyLevels; }
    public void setHierarchyLevels(List<String> hierarchyLevels) { this.hierarchyLevels = hierarchyLevels; }
}