package model;

public class Vertex {
    public final int id;
    public double x;
    public double y;
    public String label;

    public Vertex(int id, double x, double y, String label) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.label = label;
    }
}