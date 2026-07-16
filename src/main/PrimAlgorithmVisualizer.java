package main;

import model.Edge;
import model.Vertex;
import ui.GraphPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

public class PrimAlgorithmVisualizer extends JFrame {

    private final List<Vertex> vertices = new ArrayList<>();
    private final List<Edge> edgesList = new ArrayList<>();
    private int nextVertexId = 0;
    private int nextEdgeId = 0;
    private int labelCounter = 0;

    private static final double VERTEX_RADIUS = 22;
    private static final double EDGE_HIT_TOLERANCE = 8;

    private enum Mode { EDIT, ALGO }
    private Mode mode = Mode.EDIT;

    // Состояние алгоритма
    private final Set<Integer> inTree = new HashSet<>();
    private final List<Edge> mstEdges = new ArrayList<>();
    private double totalWeight;
    private int startVertexId = -1;
    private boolean finished;
    private Edge candidateChosen;
    private boolean animating;
    private final Set<Integer> unreachable = new HashSet<>();

    private Timer autoTimer;
    private Timer blinkTimer;
    private boolean blinkOn;
    private int blinkTicks;
    private boolean autoRunning;

    // Состояние редактирования
    private Vertex selectedVertex;
    private Edge selectedEdge;
    private final List<Vertex> ctrlChain = new ArrayList<>();

    private Vertex draggedVertex;
    private double dragOffsetX, dragOffsetY;

    private Vertex shiftEdgeStart;
    private double dragEdgeCurX, dragEdgeCurY;

    private boolean panning;
    private int panStartScreenX, panStartScreenY;
    private double panStartOffsetX, panStartOffsetY;

    // Вид (зум/панорамирование)
    private double scale = 1.0;
    private double offsetX = 0, offsetY = 0;
    private static final double MIN_SCALE = 0.3, MAX_SCALE = 3.0;

    // UI элементы
    private GraphPanel graphPanel;
    private JButton nextBtn, autoBtn, resetBtn, finishBtn, modeBtn, backBtn;
    private JButton clearBtn, sampleBtn, loadBtn, zoomInBtn, zoomOutBtn, zoomResetBtn, saveResultBtn, aboutBtn;
    private JSlider speedSlider;
    private JLabel weightLabel, stepLabel, startLabel, zoomLabel, modeStatusLabel;
    private DefaultListModel<String> mstListModel;
    private JList<String> mstList;

    public PrimAlgorithmVisualizer() {
        super("Алгоритм Прима — редактор графа и визуализация");
        buildUI();
        loadSampleGraph();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 700);
        setLocationRelativeTo(null);
    }

    // Геттеры для GraphPanel
    public List<Vertex> getVertices() { return vertices; }
    public List<Edge> getEdgesList() { return edgesList; }
    public List<Edge> getMstEdges() { return mstEdges; }
    public Vertex getSelectedVertex() { return selectedVertex; }
    public Edge getSelectedEdge() { return selectedEdge; }
    public List<Vertex> getCtrlChain() { return ctrlChain; }
    public Vertex getShiftEdgeStart() { return shiftEdgeStart; }
    public double getDragEdgeCurX() { return dragEdgeCurX; }
    public double getDragEdgeCurY() { return dragEdgeCurY; }
    public double getScale() { return scale; }
    public double getVertexRadius() { return VERTEX_RADIUS; }
    public boolean isAlgoMode() { return mode == Mode.ALGO; }
    public boolean isFinished() { return finished; }
    public boolean isAnimating() { return animating; }
    public boolean isBlinkOn() { return blinkOn; }
    public Edge getCandidateChosen() { return candidateChosen; }
    public double getTotalWeight() { return totalWeight; }
    public Set<Integer> getInTree() { return inTree; }
    public int getStartVertexId() { return startVertexId; }
    public Set<Integer> getUnreachable() { return unreachable; }

    private void buildUI() {
        setLayout(new BorderLayout());
        graphPanel = new GraphPanel(this);
        add(graphPanel, BorderLayout.CENTER);
        add(buildTopToolbar(), BorderLayout.NORTH);
        add(buildInfoPanel(), BorderLayout.EAST);
        add(buildControlPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildTopToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));

        modeBtn = new JButton("\u25B6 Запустить алгоритм");
        modeBtn.addActionListener(e -> toggleMode());
        bar.add(modeBtn);

        bar.add(new JSeparator(SwingConstants.VERTICAL));

        clearBtn = new JButton("Очистить граф");
        clearBtn.addActionListener(e -> clearGraph());
        bar.add(clearBtn);

        sampleBtn = new JButton("Пример графа");
        sampleBtn.addActionListener(e -> loadSampleGraph());
        bar.add(sampleBtn);

        loadBtn = new JButton("Загрузить из файла...");
        loadBtn.addActionListener(e -> loadGraphFromFile());
        bar.add(loadBtn);

        bar.add(new JSeparator(SwingConstants.VERTICAL));

        zoomOutBtn = new JButton("\u2212");
        zoomOutBtn.addActionListener(e -> zoomAtCenter(1 / 1.2));
        bar.add(zoomOutBtn);

        zoomLabel = new JLabel("100%");
        bar.add(zoomLabel);

        zoomInBtn = new JButton("+");
        zoomInBtn.addActionListener(e -> zoomAtCenter(1.2));
        bar.add(zoomInBtn);

        zoomResetBtn = new JButton("Сбросить вид");
        zoomResetBtn.addActionListener(e -> resetView());
        bar.add(zoomResetBtn);

        modeStatusLabel = new JLabel("  Режим: Редактирование графа");
        modeStatusLabel.setFont(modeStatusLabel.getFont().deriveFont(Font.BOLD));
        bar.add(modeStatusLabel);

        aboutBtn = new JButton("О разработчиках");
        aboutBtn.addActionListener(e -> showAboutDialog());
        bar.add(aboutBtn);

        return bar;
    }

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "Программу разработали студенты 2 курса ЛЭТИ из группы 4388:\n" +
                        "Максимова Мария,\n" +
                        "Ходыко Екатерина,\n" +
                        "Тарабанов Арсений.",
                "О разработчиках", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel();
        panel.setPreferredSize(new Dimension(280, 0));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTextArea help = new JTextArea(
                "Редактирование графа:\n" +
                        "• ЛКМ по пустому месту — добавить вершину\n" +
                        "• Перетаскивание вершины — переместить\n" +
                        "• Ctrl+клик по двум вершинам — создать ребро\n" +
                        "• Shift+перетаскивание между вершинами — тоже создать ребро\n" +
                        "• ПКМ по вершине/ребру — удалить (контекстное меню)\n" +
                        "• Delete/Backspace — удалить выделенное\n" +
                        "• Alt+перетаскивание фона — панорамирование\n" +
                        "• Колесо мыши — зум"
        );
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setBackground(panel.getBackground());
        help.setAlignmentX(Component.LEFT_ALIGNMENT);
        help.setFont(help.getFont().deriveFont(12f));

        JLabel title = new JLabel("Информация об алгоритме");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(14, 0, 6, 0));

        startLabel = new JLabel("Старт: -");
        startLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        stepLabel = new JLabel("Шаг: 0 / 0");
        stepLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        weightLabel = new JLabel("Текущий вес остова: 0");
        weightLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel listTitle = new JLabel("Рёбра MST (порядок добавления):");
        listTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        listTitle.setBorder(new EmptyBorder(10, 0, 4, 0));

        mstListModel = new DefaultListModel<>();
        mstList = new JList<>(mstListModel);
        JScrollPane scrollPane = new JScrollPane(mstList);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPane.setPreferredSize(new Dimension(250, 220));
        scrollPane.setMaximumSize(new Dimension(250, 400));

        JLabel legendTitle = new JLabel("Легенда (режим алгоритма):");
        legendTitle.setBorder(new EmptyBorder(12, 0, 4, 0));
        legendTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel legend = new JPanel();
        legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        legend.add(legendRow(new Color(60, 170, 80), "Вершина в остове"));
        legend.add(legendRow(new Color(230, 200, 0), "Ребро-кандидат"));
        legend.add(legendRow(Color.RED, "Выбираемое ребро (мигает)"));
        legend.add(legendRow(Color.BLUE, "Ребро в остове (MST)"));
        legend.add(legendRow(Color.GRAY, "Не вошло в остов"));
        legend.add(legendRow(Color.ORANGE, "Недостижимо (граф несвязный)"));

        panel.add(help);
        panel.add(title);
        panel.add(startLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(stepLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(weightLabel);
        panel.add(listTitle);
        panel.add(scrollPane);
        panel.add(legendTitle);
        panel.add(legend);

        return panel;
    }

    private JPanel legendRow(Color c, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel swatch = new JPanel();
        swatch.setPreferredSize(new Dimension(14, 14));
        swatch.setBackground(c);
        row.add(swatch);
        row.add(new JLabel(text));
        return row;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 10));

        nextBtn = new JButton("Следующий шаг");
        backBtn = new JButton("\u2190 Шаг назад");
        autoBtn = new JButton("Автозапуск");
        resetBtn = new JButton("Сброс");
        finishBtn = new JButton("Завершить");
        saveResultBtn = new JButton("Сохранить результат...");

        nextBtn.addActionListener(e -> nextStep(true));
        backBtn.addActionListener(e -> stepBack());
        autoBtn.addActionListener(e -> toggleAuto());
        resetBtn.addActionListener(e -> { if (mode == Mode.ALGO) resetAlgorithmState(); });
        finishBtn.addActionListener(e -> finishAll());
        saveResultBtn.addActionListener(e -> saveResultsToFile());

        panel.add(backBtn);
        panel.add(nextBtn);
        panel.add(autoBtn);
        panel.add(resetBtn);
        panel.add(finishBtn);
        panel.add(saveResultBtn);

        panel.add(new JLabel("Скорость:"));
        speedSlider = new JSlider(1, 10, 5);
        speedSlider.setPreferredSize(new Dimension(150, 30));
        speedSlider.addChangeListener(e -> {
            if (autoTimer != null && autoTimer.isRunning()) autoTimer.setDelay(currentAutoDelay());
        });
        panel.add(speedSlider);

        setAlgoControlsEnabled(false);
        return panel;
    }

    private void toggleMode() {
        if (mode == Mode.EDIT) {
            if (vertices.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Сначала добавьте хотя бы одну вершину.",
                        "Пустой граф", JOptionPane.WARNING_MESSAGE);
                return;
            }
            mode = Mode.ALGO;
            modeBtn.setText("\u270E Вернуться к редактированию");
            modeStatusLabel.setText("  Режим: Алгоритм Прима");
            setEditControlsEnabled(false);
            setAlgoControlsEnabled(true);
            resetAlgorithmState();
        } else {
            stopAuto();
            if (blinkTimer != null) blinkTimer.stop();
            animating = false;
            mode = Mode.EDIT;
            modeBtn.setText("\u25B6 Запустить алгоритм");
            modeStatusLabel.setText("  Режим: Редактирование графа");
            setEditControlsEnabled(true);
            setAlgoControlsEnabled(false);
            inTree.clear();
            mstEdges.clear();
            unreachable.clear();
            finished = false;
            candidateChosen = null;
            totalWeight = 0;
            mstListModel.clear();
            updateInfoLabels();
            updateSaveResultButton();
            graphPanel.repaint();
        }
    }

    private void setEditControlsEnabled(boolean enabled) {
        clearBtn.setEnabled(enabled);
        sampleBtn.setEnabled(enabled);
        loadBtn.setEnabled(enabled);
    }

    private void setAlgoControlsEnabled(boolean enabled) {
        nextBtn.setEnabled(enabled && !finished);
        autoBtn.setEnabled(enabled && !finished);
        finishBtn.setEnabled(enabled && !finished);
        resetBtn.setEnabled(enabled);
        speedSlider.setEnabled(enabled && !finished);
        updateSaveResultButton();
        updateBackButton();
    }

    private void updateBackButton() {
        backBtn.setEnabled(mode == Mode.ALGO && !animating && !mstEdges.isEmpty());
    }

    private void updateSaveResultButton() {
        saveResultBtn.setEnabled(mode == Mode.ALGO && !mstEdges.isEmpty());
    }

    private String generateLabel(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private Vertex addVertex(double x, double y) {
        Vertex v = new Vertex(nextVertexId++, x, y, generateLabel(labelCounter++));
        vertices.add(v);
        return v;
    }

    private void removeVertex(Vertex v) {
        vertices.remove(v);
        edgesList.removeIf(e -> e.u == v.id || e.v == v.id);
        if (selectedVertex == v) selectedVertex = null;
        ctrlChain.remove(v);
    }

    private void removeEdge(Edge e) {
        edgesList.remove(e);
        if (selectedEdge == e) selectedEdge = null;
    }

    private Edge findEdgeBetween(int a, int b) {
        for (Edge e : edgesList) if (e.connects(a, b)) return e;
        return null;
    }

    private void clearGraph() {
        if (mode != Mode.EDIT) return;
        vertices.clear();
        edgesList.clear();
        selectedVertex = null;
        selectedEdge = null;
        ctrlChain.clear();
        nextVertexId = 0;
        labelCounter = 0;
        resetView();
        graphPanel.repaint();
    }

    private void loadSampleGraph() {
        if (mode != Mode.EDIT) return;
        vertices.clear();
        edgesList.clear();
        selectedVertex = null;
        selectedEdge = null;
        ctrlChain.clear();
        nextVertexId = 0;
        labelCounter = 0;

        String[] names = {"A", "B", "C", "D", "E", "F", "G", "H", "I"};
        double cx = 380, cy = 300, r = 220;
        for (int i = 0; i < names.length; i++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * i / names.length;
            Vertex v = new Vertex(nextVertexId++, cx + r * Math.cos(angle), cy + r * Math.sin(angle), names[i]);
            vertices.add(v);
        }
        labelCounter = names.length;

        int[][] sample = {
                {0, 1, 4}, {0, 7, 8}, {1, 2, 8}, {1, 7, 11}, {2, 3, 7}, {2, 5, 4},
                {2, 8, 2}, {3, 4, 9}, {3, 5, 14}, {4, 5, 10}, {5, 6, 2}, {6, 7, 1}, {6, 8, 6}, {7, 8, 7}
        };
        for (int[] e : sample) edgesList.add(new Edge(nextEdgeId++, vertices.get(e[0]).id, vertices.get(e[1]).id, e[2]));

        resetView();
        graphPanel.repaint();
    }

    private void loadGraphFromFile() {
        if (mode != Mode.EDIT) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Текстовые файлы (*.txt)", "txt"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try (Scanner sc = new Scanner(file, "UTF-8")) {

            if (!sc.hasNextInt()) throw new IOException("Не найдено число N (количество вершин) в первой строке.");
            int N = sc.nextInt();
            if (N <= 0) throw new IOException("Количество вершин N должно быть положительным.");

            if (!sc.hasNextInt()) throw new IOException("Не найдено число M (количество рёбер) во второй строке.");
            int M = sc.nextInt();
            if (M < 0) throw new IOException("Количество рёбер M не может быть отрицательным.");

            int[] eu = new int[M], ev = new int[M];
            double[] ew = new double[M];
            int minIdx = Integer.MAX_VALUE, maxIdx = Integer.MIN_VALUE;

            for (int i = 0; i < M; i++) {
                if (!sc.hasNextInt()) throw new IOException("Ожидался номер вершины u в ребре №" + (i + 1) + ".");
                int u = sc.nextInt();
                if (!sc.hasNextInt()) throw new IOException("Ожидался номер вершины v в ребре №" + (i + 1) + ".");
                int v = sc.nextInt();
                if (!sc.hasNext()) throw new IOException("Ожидался вес w в ребре №" + (i + 1) + ".");
                String wTok = sc.next().replace(',', '.');
                double w;
                try {
                    w = Double.parseDouble(wTok);
                } catch (NumberFormatException nfe) {
                    throw new IOException("Не удалось разобрать вес w='" + wTok + "' в ребре №" + (i + 1) + ".");
                }
                if (w <= 0) throw new IOException("Вес ребра №" + (i + 1) + " должен быть положительным (получено " + w + ").");
                eu[i] = u; ev[i] = v; ew[i] = w;
                minIdx = Math.min(minIdx, Math.min(u, v));
                maxIdx = Math.max(maxIdx, Math.max(u, v));
            }

            boolean oneIndexed = M > 0 && minIdx >= 1 && maxIdx <= N;
            int lowerBound = oneIndexed ? 1 : 0;
            int upperBound = oneIndexed ? N : N - 1;
            if (M > 0 && (minIdx < lowerBound || maxIdx > upperBound)) {
                throw new IOException("Номера вершин выходят за допустимый диапазон "
                        + "[" + lowerBound + ".." + upperBound + "] для N=" + N + ".");
            }

            vertices.clear();
            edgesList.clear();
            nextVertexId = 0;
            nextEdgeId = 0;
            labelCounter = 0;

            double cx = 380, cy = 300, r = 220;
            List<Vertex> created = new ArrayList<>();
            for (int i = 0; i < N; i++) {
                double angle = -Math.PI / 2 + 2 * Math.PI * i / N;
                Vertex vtx = addVertex(cx + r * Math.cos(angle), cy + r * Math.sin(angle));
                created.add(vtx);
            }

            for (int i = 0; i < M; i++) {
                int u = oneIndexed ? eu[i] - 1 : eu[i];
                int v = oneIndexed ? ev[i] - 1 : ev[i];
                edgesList.add(new Edge(nextEdgeId++, created.get(u).id, created.get(v).id, ew[i]));
            }

            selectedVertex = null;
            selectedEdge = null;
            ctrlChain.clear();
            resetView();
            graphPanel.repaint();

            JOptionPane.showMessageDialog(this,
                    "Граф загружен: вершин — " + N + ", рёбер — " + M
                            + "\nИндексация вершин в файле: " + (oneIndexed ? "с 1" : "с 0"),
                    "Загрузка завершена", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Ошибка в формате файла:\n" + ex.getMessage() +
                            "\n\nОжидаемый формат:\nN\nM\nu1 v1 w1\nu2 v2 w2\n...",
                    "Ошибка загрузки", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Не удалось прочитать файл:\n" + ex.getMessage(),
                    "Ошибка загрузки", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveResultsToFile() {
        if (mstEdges.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ещё нет результатов для сохранения — выполните хотя бы один шаг алгоритма.",
                    "Нет данных", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Текстовые файлы (*.txt)", "txt"));
        chooser.setSelectedFile(new File("mst_result.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            file = new File(file.getParentFile(), file.getName() + ".txt");
        }
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            pw.println("Результат построения минимального остовного дерева (алгоритм Прима)");
            Vertex startV = findVertex(startVertexId);
            pw.println("Стартовая вершина: " + (startV != null ? startV.label : "?"));
            pw.println("Вершин в графе: " + vertices.size());
            pw.println("Рёбер в остове: " + mstEdges.size());
            if (!finished) pw.println("(внимание: построение ещё не завершено — сохранён промежуточный результат)");
            if (!unreachable.isEmpty()) pw.println("Недостижимых вершин: " + unreachable.size());
            pw.println();
            pw.println("Общий вес минимального остовного дерева: " + formatWeight(totalWeight));
            pw.println();
            pw.println("Список рёбер дерева в порядке их добавления:");
            for (int i = 0; i < mstEdges.size(); i++) {
                Edge e = mstEdges.get(i);
                Vertex a = findVertex(e.u), b = findVertex(e.v);
                pw.println((i + 1) + ". " + a.label + " - " + b.label + "  (вес " + formatWeight(e.weight) + ")");
            }
            JOptionPane.showMessageDialog(this, "Результат сохранён в файл:\n" + file.getAbsolutePath(),
                    "Сохранено", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Не удалось сохранить файл:\n" + ex.getMessage(),
                    "Ошибка сохранения", JOptionPane.ERROR_MESSAGE);
        }
    }

    public String formatWeight(double w) {
        if (w == Math.floor(w) && !Double.isInfinite(w)) return String.valueOf((long) w);
        return String.valueOf(w);
    }

    private Double askEdgeWeight(String labelA, String labelB, Double defaultValue) {
        String initial = defaultValue != null ? formatWeight(defaultValue) : "1";
        while (true) {
            String input = JOptionPane.showInputDialog(this,
                    "Введите вес ребра " + labelA + " — " + labelB + " (положительное число):",
                    initial);
            if (input == null) return null;
            input = input.trim().replace(',', '.');
            try {
                double val = Double.parseDouble(input);
                if (val <= 0) {
                    JOptionPane.showMessageDialog(this, "Вес должен быть положительным числом.",
                            "Некорректное значение", JOptionPane.ERROR_MESSAGE);
                    initial = input;
                    continue;
                }
                return val;
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Введите корректное число (целое или вещественное), например 4 или 3.5",
                        "Некорректное значение", JOptionPane.ERROR_MESSAGE);
                initial = input;
            }
        }
    }

    private void tryCreateEdge(Vertex a, Vertex b) {
        if (a == null || b == null || a == b) return;
        Edge existing = findEdgeBetween(a.id, b.id);
        Double result = askEdgeWeight(a.label, b.label, existing != null ? existing.weight : null);
        if (result == null) return;
        if (existing != null) {
            existing.weight = result;
        } else {
            edgesList.add(new Edge(nextEdgeId++, a.id, b.id, result));
        }
        graphPanel.repaint();
    }

    private void resetView() {
        scale = 1.0;
        offsetX = 0;
        offsetY = 0;
        updateZoomLabel();
        graphPanel.repaint();
    }

    private void zoomAtCenter(double factor) {
        zoomAt(factor, graphPanel.getWidth() / 2.0, graphPanel.getHeight() / 2.0);
    }

    public void zoomAt(double factor, double screenX, double screenY) {
        double worldX = (screenX - offsetX) / scale;
        double worldY = (screenY - offsetY) / scale;
        double newScale = scale * factor;
        newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
        offsetX = screenX - worldX * newScale;
        offsetY = screenY - worldY * newScale;
        scale = newScale;
        updateZoomLabel();
        graphPanel.repaint();
    }

    private void updateZoomLabel() {
        zoomLabel.setText(Math.round(scale * 100) + "%");
    }

    private double toWorldX(double screenX) { return (screenX - offsetX) / scale; }
    private double toWorldY(double screenY) { return (screenY - offsetY) / scale; }
    public double toScreenX(double worldX) { return offsetX + worldX * scale; }
    public double toScreenY(double worldY) { return offsetY + worldY * scale; }

    private Vertex vertexAtWorld(double wx, double wy) {
        for (int i = vertices.size() - 1; i >= 0; i--) {
            Vertex v = vertices.get(i);
            double dx = v.x - wx, dy = v.y - wy;
            if (Math.sqrt(dx * dx + dy * dy) <= VERTEX_RADIUS) return v;
        }
        return null;
    }

    private Edge edgeAtWorld(double wx, double wy) {
        Edge best = null;
        double bestDist = EDGE_HIT_TOLERANCE;
        for (Edge e : edgesList) {
            Vertex a = findVertex(e.u), b = findVertex(e.v);
            if (a == null || b == null) continue;
            double d = pointToSegmentDistance(wx, wy, a.x, a.y, b.x, b.y);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    public Vertex findVertex(int id) {
        for (Vertex v : vertices) if (v.id == id) return v;
        return null;
    }

    private static double pointToSegmentDistance(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx, projY = y1 + t * dy;
        double ddx = px - projX, ddy = py - projY;
        return Math.sqrt(ddx * ddx + ddy * ddy);
    }

    private void resetAlgorithmState() {
        stopAuto();
        if (blinkTimer != null) blinkTimer.stop();
        animating = false;

        inTree.clear();
        mstEdges.clear();
        unreachable.clear();
        totalWeight = 0;
        finished = false;
        candidateChosen = null;
        mstListModel.clear();

        if (vertices.isEmpty()) return;
        Vertex start = vertices.get(new Random().nextInt(vertices.size()));
        startVertexId = start.id;
        inTree.add(start.id);

        startLabel.setText("Старт: вершина " + start.label);
        updateInfoLabels();
        setAlgoControlsEnabled(true);
        graphPanel.repaint();
    }

    private void stepBack() {
        if (mode != Mode.ALGO || animating || mstEdges.isEmpty()) return;

        stopAuto();
        if (blinkTimer != null) blinkTimer.stop();
        candidateChosen = null;

        mstEdges.remove(mstEdges.size() - 1);
        mstListModel.remove(mstListModel.size() - 1);

        inTree.clear();
        inTree.add(startVertexId);
        totalWeight = 0;
        for (Edge e : mstEdges) {
            inTree.add(e.u);
            inTree.add(e.v);
            totalWeight += e.weight;
        }

        finished = false;
        unreachable.clear();

        updateInfoLabels();
        setAlgoControlsEnabled(true);
        graphPanel.repaint();
    }

    public List<Edge> getCurrentCandidates() {
        List<Edge> result = new ArrayList<>();
        for (Edge e : edgesList) {
            boolean uIn = inTree.contains(e.u), vIn = inTree.contains(e.v);
            if (uIn != vIn) result.add(e);
        }
        return result;
    }

    private void nextStep(boolean animate) {
        if (mode != Mode.ALGO || finished || animating) return;

        List<Edge> candidates = getCurrentCandidates();
        if (candidates.isEmpty()) {
            finishNow();
            return;
        }

        Edge min = candidates.get(0);
        for (Edge e : candidates) if (e.weight < min.weight) min = e;
        final Edge chosen = min;
        candidateChosen = chosen;
        graphPanel.repaint();

        if (!animate) {
            commitStep(chosen);
            return;
        }

        animating = true;
        setAlgoControlsEnabled(false);
        resetBtn.setEnabled(true);
        blinkOn = true;
        blinkTicks = 0;
        blinkTimer = new Timer(150, null);
        blinkTimer.addActionListener(e -> {
            blinkOn = !blinkOn;
            blinkTicks++;
            graphPanel.repaint();
            if (blinkTicks >= 6) {
                blinkTimer.stop();
                animating = false;
                commitStep(chosen);
                setAlgoControlsEnabled(!finished);
            }
        });
        blinkTimer.start();
    }

    private void commitStep(Edge e) {
        int newVertexId = inTree.contains(e.u) ? e.v : e.u;
        inTree.add(newVertexId);
        mstEdges.add(e);
        totalWeight += e.weight;
        candidateChosen = null;

        Vertex a = findVertex(e.u), b = findVertex(e.v);
        mstListModel.addElement(String.format(Locale.US, "%d.  %s — %s   (вес %s)",
                mstEdges.size(), a.label, b.label, formatWeight(e.weight)));

        updateInfoLabels();
        updateSaveResultButton();
        updateBackButton();

        if (inTree.size() == vertices.size()) {
            finished = true;
            stopAuto();
            setAlgoControlsEnabled(false);
            resetBtn.setEnabled(true);
        }
        graphPanel.repaint();
    }

    private void finishNow() {
        finished = true;
        stopAuto();
        unreachable.clear();
        for (Vertex v : vertices) if (!inTree.contains(v.id)) unreachable.add(v.id);
        setAlgoControlsEnabled(false);
        resetBtn.setEnabled(true);
        graphPanel.repaint();
    }

    private void finishAll() {
        if (mode != Mode.ALGO) return;
        stopAuto();
        if (blinkTimer != null) blinkTimer.stop();
        animating = false;
        while (!finished) nextStep(false);
    }

    private void toggleAuto() {
        if (mode != Mode.ALGO) return;
        if (autoRunning) {
            stopAuto();
        } else {
            if (finished) return;
            autoRunning = true;
            autoBtn.setText("Стоп");
            nextBtn.setEnabled(false);
            finishBtn.setEnabled(false);
            autoTimer = new Timer(currentAutoDelay(), e -> {
                if (finished) { stopAuto(); return; }
                nextStep(false);
            });
            autoTimer.start();
        }
    }

    private void stopAuto() {
        autoRunning = false;
        if (autoTimer != null) autoTimer.stop();
        autoBtn.setText("Автозапуск");
        if (!finished && mode == Mode.ALGO) {
            nextBtn.setEnabled(true);
            finishBtn.setEnabled(true);
        }
    }

    private int currentAutoDelay() {
        int val = speedSlider.getValue();
        return Math.max(80, (11 - val) * 220);
    }

    private void updateInfoLabels() {
        stepLabel.setText("Шаг: " + mstEdges.size() + " / " + Math.max(0, vertices.size() - 1));
        weightLabel.setText("Текущий вес остова: " + formatWeight(totalWeight));
    }

    // Обработчики событий мыши, вызываемые из GraphPanel
    public void deleteSelection() {
        if (mode != Mode.EDIT) return;
        if (selectedVertex != null) { removeVertex(selectedVertex); selectedVertex = null; graphPanel.repaint(); }
        else if (selectedEdge != null) { removeEdge(selectedEdge); selectedEdge = null; graphPanel.repaint(); }
    }

    public void clearSelections() {
        selectedVertex = null;
        selectedEdge = null;
        ctrlChain.clear();
        shiftEdgeStart = null;
    }

    public void onMousePressed(MouseEvent e) {
        double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());

        if (SwingUtilities.isRightMouseButton(e)) {
            if (mode != Mode.EDIT) return;
            Vertex v = vertexAtWorld(wx, wy);
            if (v != null) { showVertexContextMenu(v, e.getX(), e.getY()); return; }
            Edge edge = edgeAtWorld(wx, wy);
            if (edge != null) { showEdgeContextMenu(edge, e.getX(), e.getY()); return; }
            return;
        }

        if (!SwingUtilities.isLeftMouseButton(e)) return;
        if (mode != Mode.EDIT) return;

        Vertex v = vertexAtWorld(wx, wy);

        if (v != null) {
            if (e.isShiftDown()) {
                shiftEdgeStart = v;
                dragEdgeCurX = wx; dragEdgeCurY = wy;
                return;
            }
            if (e.isControlDown()) {
                if (ctrlChain.contains(v)) ctrlChain.remove(v); else ctrlChain.add(v);
                if (ctrlChain.size() >= 2) {
                    Vertex a = ctrlChain.get(0), b = ctrlChain.get(1);
                    ctrlChain.clear();
                    tryCreateEdge(a, b);
                }
                graphPanel.repaint();
                return;
            }
            selectedVertex = v;
            selectedEdge = null;
            draggedVertex = v;
            dragOffsetX = v.x - wx;
            dragOffsetY = v.y - wy;
            graphPanel.repaint();
            return;
        }

        if (e.isAltDown()) {
            panning = true;
            panStartScreenX = e.getX(); panStartScreenY = e.getY();
            panStartOffsetX = offsetX; panStartOffsetY = offsetY;
            return;
        }

        Edge edge = edgeAtWorld(wx, wy);
        if (edge != null) {
            selectedEdge = edge;
            selectedVertex = null;
            graphPanel.repaint();
            return;
        }

        selectedVertex = null;
        selectedEdge = null;
        addVertex(wx, wy);
        graphPanel.repaint();
    }

    public void onMouseDragged(MouseEvent e) {
        double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
        if (panning) {
            offsetX = panStartOffsetX + (e.getX() - panStartScreenX);
            offsetY = panStartOffsetY + (e.getY() - panStartScreenY);
            graphPanel.repaint();
            return;
        }
        if (draggedVertex != null) {
            draggedVertex.x = wx + dragOffsetX;
            draggedVertex.y = wy + dragOffsetY;
            graphPanel.repaint();
            return;
        }
        if (shiftEdgeStart != null) {
            dragEdgeCurX = wx; dragEdgeCurY = wy;
            graphPanel.repaint();
        }
    }

    public void onMouseReleased(MouseEvent e) {
        if (panning) { panning = false; return; }
        if (draggedVertex != null) { draggedVertex = null; return; }
        if (shiftEdgeStart != null) {
            double wx = toWorldX(e.getX()), wy = toWorldY(e.getY());
            Vertex target = vertexAtWorld(wx, wy);
            Vertex start = shiftEdgeStart;
            shiftEdgeStart = null;
            if (target != null && target != start) tryCreateEdge(start, target);
            graphPanel.repaint();
        }
    }

    private void showVertexContextMenu(Vertex v, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem del = new JMenuItem("Удалить вершину " + v.label);
        del.addActionListener(a -> { removeVertex(v); graphPanel.repaint(); });
        menu.add(del);
        menu.show(graphPanel, x, y);
    }

    private void showEdgeContextMenu(Edge edge, int x, int y) {
        Vertex a = findVertex(edge.u), b = findVertex(edge.v);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem del = new JMenuItem("Удалить ребро " + (a != null ? a.label : "?") + " — " + (b != null ? b.label : "?"));
        del.addActionListener(ev -> { removeEdge(edge); graphPanel.repaint(); });
        JMenuItem edit = new JMenuItem("Изменить вес...");
        edit.addActionListener(ev -> {
            Double res = askEdgeWeight(a != null ? a.label : "?", b != null ? b.label : "?", edge.weight);
            if (res != null) edge.weight = res;
            graphPanel.repaint();
        });
        menu.add(edit);
        menu.add(del);
        menu.show(graphPanel, x, y);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PrimAlgorithmVisualizer().setVisible(true));
    }
}