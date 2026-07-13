import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class PrimMST extends JFrame {

    private static class Vertex { int id; double x, y; String label; }
    private static class Edge { int id, u, v; double w; }

    private final List<Vertex> vertices = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Set<Integer> inTree = new HashSet<>();
    private final List<Edge> mst = new ArrayList<>();
    private double totalWeight = 0;
    private int startId = -1;
    private Set<Integer> unreachable = new HashSet<>();
    private boolean loaded = false;


    private GraphPanel panel;
    private DefaultListModel<String> listModel;
    private JLabel weightLabel, edgeCountLabel, startLabel;

    public PrimMST() {
        super("Алгоритм Прима");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);


        setLayout(new BorderLayout());
        panel = new GraphPanel();
        add(panel, BorderLayout.CENTER);


        JToolBar bar = new JToolBar();
        JButton loadBtn = new JButton("Загрузить граф из файла...");
        loadBtn.addActionListener(e -> loadFromFile());
        bar.add(loadBtn);
        add(bar, BorderLayout.NORTH);


        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        info.setPreferredSize(new Dimension(280, 0));

        startLabel = new JLabel("Старт: -");
        startLabel.setAlignmentX(LEFT_ALIGNMENT);
        edgeCountLabel = new JLabel("Рёбер в MST: 0");
        edgeCountLabel.setAlignmentX(LEFT_ALIGNMENT);
        weightLabel = new JLabel("Общий вес: 0");
        weightLabel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel listTitle = new JLabel("Рёбра MST (в порядке добавления):");
        listTitle.setAlignmentX(LEFT_ALIGNMENT);
        listTitle.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));

        listModel = new DefaultListModel<>();
        JList<String> list = new JList<>(listModel);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(260, 300));

        info.add(startLabel);
        info.add(Box.createVerticalStrut(5));
        info.add(edgeCountLabel);
        info.add(Box.createVerticalStrut(5));
        info.add(weightLabel);
        info.add(listTitle);
        info.add(scroll);

        add(info, BorderLayout.EAST);
    }

    private void loadFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Текстовые файлы (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        try {
            List<String> lines = Files.readAllLines(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8);

            List<String> clean = new ArrayList<>();
            for (String line : lines) {
                String s = line.trim();
                if (!s.isEmpty() && !s.startsWith("#")) clean.add(s);
            }
            if (clean.size() < 2) throw new Exception("Файл должен содержать N и M");

            int N = Integer.parseInt(clean.get(0));
            int M = Integer.parseInt(clean.get(1));
            if (clean.size() < M + 2) throw new Exception("Недостаточно строк для рёбер");


            vertices.clear();
            edges.clear();
            inTree.clear();
            mst.clear();
            listModel.clear();
            unreachable.clear();
            loaded = false;

            double cx = 400, cy = 300, r = 250;
            for (int i = 0; i < N; i++) {
                Vertex v = new Vertex();
                v.id = i;
                v.label = generateLabel(i);
                double angle = -Math.PI / 2 + 2 * Math.PI * i / N;
                v.x = cx + r * Math.cos(angle);
                v.y = cy + r * Math.sin(angle);
                vertices.add(v);
            }


            for (int i = 0; i < M; i++) {
                String[] parts = clean.get(i + 2).split("\\s+");
                if (parts.length < 3) throw new Exception("Некорректное ребро: " + clean.get(i + 2));
                int u = Integer.parseInt(parts[0]);
                int v = Integer.parseInt(parts[1]);
                double w = Double.parseDouble(parts[2].replace(',', '.'));
                if (u < 0 || u >= N || v < 0 || v >= N)
                    throw new Exception("Номер вершины вне диапазона");
                Edge e = new Edge();
                e.id = i;
                e.u = u;
                e.v = v;
                e.w = w;
                edges.add(e);
            }


            runPrim();
            loaded = true;
            updateInfo();
            panel.repaint();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Ошибка загрузки:\n" + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String generateLabel(int idx) {
        StringBuilder sb = new StringBuilder();
        int n = idx + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private void runPrim() {
        if (vertices.isEmpty()) return;
        inTree.clear();
        mst.clear();
        totalWeight = 0;
        startId = vertices.get(0).id;
        inTree.add(startId);

        while (inTree.size() < vertices.size()) {
            Edge best = null;
            double bestW = Double.POSITIVE_INFINITY;
            for (Edge e : edges) {
                boolean uIn = inTree.contains(e.u);
                boolean vIn = inTree.contains(e.v);
                if (uIn != vIn && e.w < bestW) {
                    best = e;
                    bestW = e.w;
                }
            }
            if (best == null) break;
            mst.add(best);
            totalWeight += best.w;
            int newV = inTree.contains(best.u) ? best.v : best.u;
            inTree.add(newV);
        }

        unreachable.clear();
        for (Vertex v : vertices) {
            if (!inTree.contains(v.id)) unreachable.add(v.id);
        }
    }

    private void updateInfo() {
        startLabel.setText("Старт: " + (startId >= 0 ? vertices.get(startId).label : "-"));
        edgeCountLabel.setText("Рёбер в MST: " + mst.size() + " / " + Math.max(0, vertices.size() - 1));
        weightLabel.setText("Общий вес: " + formatWeight(totalWeight) +
                (unreachable.isEmpty() ? "" : "  (недостижимых: " + unreachable.size() + ")"));

        listModel.clear();
        for (int i = 0; i < mst.size(); i++) {
            Edge e = mst.get(i);
            Vertex a = vertices.get(e.u), b = vertices.get(e.v);
            listModel.addElement(String.format("%d. %s — %s (вес %s)", i+1, a.label, b.label, formatWeight(e.w)));
        }
    }

    private String formatWeight(double w) {
        return (w == Math.floor(w)) ? String.valueOf((long) w) : String.valueOf(w);
    }


    private class GraphPanel extends JPanel {
        private double scale = 1.0, offX = 0, offY = 0;

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!loaded || vertices.isEmpty()) {
                g.drawString("Граф не загружен", 20, 30);
                return;
            }


            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Vertex v : vertices) {
                if (v.x < minX) minX = v.x;
                if (v.x > maxX) maxX = v.x;
                if (v.y < minY) minY = v.y;
                if (v.y > maxY) maxY = v.y;
            }
            double w = maxX - minX, h = maxY - minY;
            double pad = 60;
            double sx = (getWidth() - 2 * pad) / w;
            double sy = (getHeight() - 2 * pad) / h;
            scale = Math.min(sx, sy);
            if (scale < 0.01) scale = 1.0;

            double cx = (minX + maxX) / 2;
            double cy = (minY + maxY) / 2;
            offX = getWidth() / 2.0 - cx * scale;
            offY = getHeight() / 2.0 - cy * scale;


            for (Edge e : edges) {
                Vertex a = vertices.get(e.u);
                Vertex b = vertices.get(e.v);
                boolean isMst = mst.contains(e);
                g.setColor(isMst ? Color.BLUE : new Color(180, 180, 180, 100));
                g.setStroke(new BasicStroke(isMst ? 4f : 1.5f));
                g.draw(new Line2D.Double(offX + a.x * scale, offY + a.y * scale,
                                         offX + b.x * scale, offY + b.y * scale));

                double mx = (a.x + b.x) / 2 * scale + offX;
                double my = (a.y + b.y) / 2 * scale + offY;
                g.setColor(Color.DARK_GRAY);
                g.setFont(g.getFont().deriveFont(12f));
                g.drawString(formatWeight(e.w), (int)mx + 4, (int)my - 4);
            }


            double radius = 20 * scale;
            for (Vertex v : vertices) {
                double x = offX + v.x * scale;
                double y = offY + v.y * scale;
                Ellipse2D circle = new Ellipse2D.Double(x - radius, y - radius, 2*radius, 2*radius);

                Color fill;
                if (unreachable.contains(v.id)) fill = Color.ORANGE;
                else if (inTree.contains(v.id)) fill = new Color(60, 170, 80);
                else fill = Color.WHITE;
                g.setColor(fill);
                g.fill(circle);

                g.setColor(v.id == startId ? Color.MAGENTA : Color.BLACK);
                g.setStroke(new BasicStroke(v.id == startId ? 3f : 1.8f));
                g.draw(circle);

                g.setColor((inTree.contains(v.id) && !unreachable.contains(v.id)) ? Color.WHITE : Color.BLACK);
                g.setFont(g.getFont().deriveFont(Font.BOLD, (float)Math.max(10, 14*scale)));
                FontMetrics fm = g.getFontMetrics();
                int tw = fm.stringWidth(v.label);
                g.drawString(v.label, (int)(x - tw/2.0), (int)(y + fm.getAscent()/2.0) - 2);
            }

            
            g.setColor(Color.DARK_GRAY);
            g.setFont(g.getFont().deriveFont(Font.ITALIC, 13f));
            String status = "MST построено. Общий вес: " + formatWeight(totalWeight) +
                    (unreachable.isEmpty() ? "" : "  (недостижимых: " + unreachable.size() + ")");
            g.drawString(status, 16, getHeight() - 16);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrimMST().setVisible(true));
    }
}
