package construction.entity;

import java.util.ArrayList;
import java.util.List;

public class LocationNode {
    public String id;
    public String name;
    public String type;
    public List<LocationNode> children = new ArrayList<>();

    // 空构造和全参构造...
}
