package ui;

import model.Edge;
import model.Vertex;
import main.PrimAlgorithmVisualizer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.List;

public class GraphPanel extends JPanel {
    private final PrimAlgorithmVisualizer visualizer;

    public GraphPanel(PrimAlgorithmVisualizer visualizer) {
        this.visualizer = visualizer;
        setBackground(Color.WHITE);
        setFocusable(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                visualizer.onMousePressed(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                visualizer.onMouseReleased(e);
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                visualizer.onMouseDragged(e);
            }
        });
        addMouseWheelListener(e -> {
            double factor = e.getPreciseWheelRotation() < 0 ? 1.15 : 1 / 1.15;
            visualizer.zoomAt(factor, e.getX(), e.getY());
        });

        // Удаление по Delete/Backspace и сброс выделения по Escape
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("DELETE"), "deleteSel");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("BACK_SPACE"), "deleteSel");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "escSel");

        getActionMap().put("deleteSel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                visualizer.deleteSelection();
            }
        });
        getActionMap().put("escSel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                visualizer.clearSelections();
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        List<Edge> edgesList = visualizer.getEdgesList();
        List<Vertex> vertices = visualizer.getVertices();
        List<Edge> candidates = visualizer.getCurrentCandidates();
        List<Edge> mstEdges = visualizer.getMstEdges();
        Vertex selectedVertex = visualizer.getSelectedVertex();
        Edge selectedEdge = visualizer.getSelectedEdge();
        List<Vertex> ctrlChain = visualizer.getCtrlChain();
        Vertex shiftEdgeStart = visualizer.getShiftEdgeStart();

        // 1. Отрисовка рёбер
        for (Edge e : edgesList) {
            Vertex a = visualizer.findVertex(e.u);
            Vertex b = visualizer.findVertex(e.v);
            if (a == null || b == null) continue;

            boolean isMst = visualizer.isAlgoMode() && mstEdges.contains(e);
            boolean isCandidate = visualizer.isAlgoMode() && candidates.contains(e);
            boolean isChosen = visualizer.isAlgoMode() && visualizer.getCandidateChosen() != null
                    && e.connects(visualizer.getCandidateChosen().u, visualizer.getCandidateChosen().v);
            boolean isSelected = !visualizer.isAlgoMode() && e == selectedEdge;

            Color color;
            float strokeWidth;
            if (visualizer.isAlgoMode()) {
                if (isMst) {
                    color = Color.BLUE;
                    strokeWidth = 4f;
                } else if (isChosen && visualizer.isAnimating()) {
                    color = visualizer.isBlinkOn() ? Color.RED : new Color(230, 200, 0);
                    strokeWidth = 4f;
                } else if (isCandidate) {
                    color = new Color(230, 200, 0);
                    strokeWidth = 3f;
                } else if (visualizer.isFinished()) {
                    color = new Color(150, 150, 150, 130);
                    strokeWidth = 2.4f;
                } else {
                    color = new Color(170, 170, 170);
                    strokeWidth = 2.4f;
                }
            } else {
                if (isSelected) {
                    color = Color.ORANGE;
                    strokeWidth = 3.5f;
                } else {
                    color = new Color(120, 120, 120);
                    strokeWidth = 2.4f;
                }
            }

            g.setStroke(new BasicStroke(strokeWidth));
            g.setColor(color);
            double x1 = visualizer.toScreenX(a.x), y1 = visualizer.toScreenY(a.y);
            double x2 = visualizer.toScreenX(b.x), y2 = visualizer.toScreenY(b.y);
            g.draw(new Line2D.Double(x1, y1, x2, y2));

            // Подпись веса ребра (смещение от линии)
            double mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
            double edx = x2 - x1, edy = y2 - y1;
            double elen = Math.max(1e-6, Math.sqrt(edx * edx + edy * edy));
            double nx = -edy / elen, ny = edx / elen;
            double labelOffset = 14;
            double lx = mx + nx * labelOffset;
            double ly = my + ny * labelOffset;

            g.setColor(visualizer.isAlgoMode() && visualizer.isFinished() && !isMst ? new Color(120, 120, 120) : Color.BLACK);
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) Math.max(10, 13 * visualizer.getScale())));
            FontMetrics wfm = g.getFontMetrics();
            String wstr = visualizer.formatWeight(e.weight);
            int wtw = wfm.stringWidth(wstr);
            g.drawString(wstr, (int) (lx - wtw / 2.0), (int) (ly + wfm.getAscent() / 2.0) - 2);
        }

        // 2. Предварительная линия при зажатом Shift
        if (shiftEdgeStart != null) {
            g.setColor(Color.BLUE);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                    new float[]{6, 6}, 0));
            g.draw(new Line2D.Double(visualizer.toScreenX(shiftEdgeStart.x), visualizer.toScreenY(shiftEdgeStart.y),
                    visualizer.toScreenX(visualizer.getDragEdgeCurX()), visualizer.toScreenY(visualizer.getDragEdgeCurY())));
        }

        // 3. Отрисовка вершин
        double radius = visualizer.getVertexRadius() * visualizer.getScale();
        for (Vertex v : vertices) {
            double sx = visualizer.toScreenX(v.x), sy = visualizer.toScreenY(v.y);
            Ellipse2D circle = new Ellipse2D.Double(sx - radius, sy - radius, radius * 2, radius * 2);

            Color fill;
            if (visualizer.isAlgoMode()) {
                fill = visualizer.getInTree().contains(v.id) ? new Color(60, 170, 80) : Color.WHITE;
            } else {
                fill = Color.WHITE;
            }
            g.setColor(fill);
            g.fill(circle);

            Color border;
            float borderWidth = 1.8f;
            if (visualizer.isAlgoMode()) {
                border = (v.id == visualizer.getStartVertexId()) ? Color.MAGENTA : Color.BLACK;
                if (visualizer.getUnreachable().contains(v.id)) border = Color.ORANGE;
                if (v.id == visualizer.getStartVertexId()) borderWidth = 3f;
            } else {
                if (v == selectedVertex) {
                    border = Color.ORANGE;
                    borderWidth = 3.5f;
                } else if (ctrlChain.contains(v)) {
                    border = Color.BLUE;
                    borderWidth = 3f;
                } else {
                    border = Color.BLACK;
                }
            }
            g.setColor(border);
            g.setStroke(new BasicStroke(borderWidth));
            g.draw(circle);

            g.setColor(visualizer.isAlgoMode() && visualizer.getInTree().contains(v.id) ? Color.WHITE : Color.BLACK);
            g.setFont(g.getFont().deriveFont(Font.BOLD, (float) Math.max(10, 14 * visualizer.getScale())));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(v.label);
            g.drawString(v.label, (int) (sx - tw / 2.0), (int) (sy + fm.getAscent() / 2.0) - 2);
        }

        // 4. Текстовая плашка статуса внизу
        g.setColor(Color.DARK_GRAY);
        g.setFont(g.getFont().deriveFont(Font.ITALIC, 13f));
        String status;
        if (!visualizer.isAlgoMode()) {
            status = "Режим редактирования — вершин: " + vertices.size() + ", рёбер: " + edgesList.size();
        } else if (visualizer.isFinished()) {
            status = "Построение MST завершено. Общий вес: " + visualizer.formatWeight(visualizer.getTotalWeight())
                    + (visualizer.getUnreachable().isEmpty() ? "" : "  (недостижимых вершин: " + visualizer.getUnreachable().size() + ")");
        } else {
            status = "Строится остов... (жёлтый = кандидаты, красный = выбирается, синий = добавлено)";
        }
        g.drawString(status, 16, getHeight() - 16);
    }
}