package model;


public class Edge {
    public final int id;
    public int u; // ID первой вершины
    public int v; // ID второй вершины
    public double weight;

    public Edge(int id, int u, int v, double weight) {
        this.id = id;
        this.u = u;
        this.v = v;
        this.weight = weight;
    }

    public boolean connects(int a, int b) {
        return (u == a && v == b) || (u == b && v == a);
    }
}