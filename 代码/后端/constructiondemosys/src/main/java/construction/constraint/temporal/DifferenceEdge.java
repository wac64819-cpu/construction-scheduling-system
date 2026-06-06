package construction.constraint.temporal;

public class DifferenceEdge {
    public final int from;
    public final int to;
    public final double weight;

    public DifferenceEdge(int from, int to, double weight) {
        this.from = from;
        this.to = to;
        this.weight = weight;
    }
}

