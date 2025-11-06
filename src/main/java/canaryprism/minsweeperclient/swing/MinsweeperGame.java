/*
 *    Copyright 2025 Canary Prism <canaryprsn@gmail.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package canaryprism.minsweeperclient.swing;

import canaryprism.minsweeper.*;
import canaryprism.minsweeper.solver.Logic;
import canaryprism.minsweeper.solver.Move;
import canaryprism.minsweeper.solver.Reason;
import canaryprism.minsweeper.solver.Solver;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class MinsweeperGame extends JComponent implements AutoCloseable {
    
    private final BoardSize size;
    private final Solver solver;
    
    private final canaryprism.minsweeper.MinsweeperGame minsweeper;
    
    private volatile GameState state;
    
    
    private final CounterView remaining_mines_counter, time_counter;
    
    private final BoardView board;
    
    private boolean auto = false;
    private boolean flag_chord = false;
    private boolean hover_chord = false;
    
    private volatile Texture theme;
    
    private static final Map<String, BufferedImage> image_map = Collections.synchronizedMap(new HashMap<>());
    
    private BufferedImage getAsset(String path) {
        return image_map.computeIfAbsent(path, (_) -> {
            var url = Objects.requireNonNull(BoardView.class.getResource("/minsweeper/" + theme.asset_path_name + "/" + path + ".svg")).toString();
            
            BufferedImageTranscoder transcoder = new BufferedImageTranscoder();
            
            TranscoderInput input = new TranscoderInput(url);
            try {
                transcoder.transcode(input, null);
            } catch (TranscoderException e) {
                throw new RuntimeException(e);
            }
            
            return transcoder.getBufferedImage();
        });
    }
    
    private Color getBackgroundColor() {
        try {
            return Color.decode(new String(Objects.requireNonNull(BoardView.class.getResourceAsStream("/minsweeper/" + theme.asset_path_name + "/background")).readAllBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public MinsweeperGame(BoardSize size, Solver solver, Texture theme) {
        this.size = size;
        this.solver = solver;
        this.minsweeper = new canaryprism.minsweeper.MinsweeperGame(size, this::endPlaying, this::endPlaying);
        this.theme = theme;
        
        {
            var cleaner = Cleaner.create();
            var ex = this.ex;
            cleaner.register(this, ex::shutdownNow);
        }
        
        this.setOpaque(true);
        reloadBackground();
        
        
        this.setLayout(new GridBagLayout());
//        var panel = new JPanel(new GridBagLayout());
        var c = new GridBagConstraints();
        
        // first the top border and whatever
        // lets also fill in the side borders while we're at it
        c.gridx = c.gridy = 0;
        c.gridwidth = c.gridheight = 1;
        
        this.add(new BorderView(BorderView.Type.TOP_LEFT), c);
        
        c.gridy++;
        c.fill = GridBagConstraints.VERTICAL;
        this.add(new BorderView(BorderView.Type.LEFT_RIGHT), c);
        
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        this.add(new BorderView(BorderView.Type.MIDDLE_LEFT), c);
        
        c.gridy++;
        c.fill = GridBagConstraints.VERTICAL;
        this.add(new BorderView(BorderView.Type.LEFT_RIGHT), c);
        
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        this.add(new BorderView(BorderView.Type.BOTTOM_LEFT), c);
        
        c.gridy = 0;
        c.gridx++;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        
        this.add(new BorderView(BorderView.Type.TOP_BOTTOM), c);
        
        c.gridy++;
        // nothing bc here the buttons
        
        c.gridy++;
        this.add(new BorderView(BorderView.Type.TOP_BOTTOM), c);
        
        c.gridy++;
        // nothing bc here the grid
        
        c.gridy++;
        this.add(new BorderView(BorderView.Type.TOP_BOTTOM), c);
        
        c.gridy = 0;
        c.gridx += c.gridwidth;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        
        this.add(new BorderView(BorderView.Type.TOP_RIGHT), c);
        
        c.gridy++;
        c.fill = GridBagConstraints.VERTICAL;
        this.add(new BorderView(BorderView.Type.LEFT_RIGHT), c);
        
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        this.add(new BorderView(BorderView.Type.MIDDLE_RIGHT), c);
        
        c.gridy++;
        c.fill = GridBagConstraints.VERTICAL;
        this.add(new BorderView(BorderView.Type.LEFT_RIGHT), c);
        
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        this.add(new BorderView(BorderView.Type.BOTTOM_RIGHT), c);
        
        c.gridy = 0;
        
        // now for the buttons and whatever
        c.gridy++;
        
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.NONE;
        c.weightx = 1;

        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        this.remaining_mines_counter = new CounterView(max(
                String.valueOf(size.mines()).length(),
                String.valueOf(size.mines() - size.width() * size.height()).length()));
        remaining_mines_counter.setValue(size.mines());
        
        this.add(remaining_mines_counter, c);
        
        c.gridx++;
        c.weightx = 0;
        c.anchor = GridBagConstraints.CENTER;
        var start_button = new RestartButton();
        this.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                start_button.requestFocusInWindow();
            }
            
            @Override
            public void ancestorRemoved(AncestorEvent event) {
            
            }
            
            @Override
            public void ancestorMoved(AncestorEvent event) {
            
            }
        });
        start_button.addActionListener((_) -> start());
        this.add(start_button, c);
        
        c.gridx++;
        c.weightx = 1;
        c.anchor = GridBagConstraints.EAST;
        this.time_counter = new CounterView(3);
        time_counter.setValue(0);
        this.add(time_counter, c);
        
        c.gridy++;
        
        this.state = minsweeper.start(solver);
        
        c.gridy++;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(0, 0, 0, 0);
        c.gridx = 1;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = c.weighty = 1;
        
        var center_panel = new JPanel(new BorderLayout());
        center_panel.setBackground(null);
        
        this.board = new BoardView();
        
        center_panel.add(board, BorderLayout.CENTER);
        
        hint_component.setLayout(new BorderLayout());
        hint_component.setBackground(null);
//        hint_component.setOpaque(true);
        
        center_panel.add(hint_component, BorderLayout.SOUTH);
        
        this.add(center_panel, c);
    }
    
    private void reloadBackground() {
        this.setBackground(getBackgroundColor());
    }
    
    public MinsweeperGame setTheme(Texture theme) {
        this.theme = theme;
        image_map.clear();
        reloadBackground();
        repaint();
        return this;
    }
    
    private volatile boolean playing;
    private final AtomicReference<ScheduledFuture<?>> play_timer = new AtomicReference<>();
    private final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
    
    private void triggerPlaying() {
        if (!playing) {
            playing = true;
            var play_timer = this.play_timer;
            var time_counter_ref = new WeakReference<>(this.time_counter);
            play_timer.set(ex.scheduleAtFixedRate(() -> {
                if (time_counter_ref.get() instanceof CounterView counter
                        && counter.getValue() != 1000 - 1) {
                    counter.setValue(counter.getValue() + 1);
                } else {
                    if (play_timer.get() instanceof ScheduledFuture<?> timer) {
                        timer.cancel(true);
                        play_timer.set(null);
                    }
                }
            }, 1, 1, TimeUnit.SECONDS));
        }
    }
    
    private void endPlaying() {
        if (playing) {
            playing = false;
            if (play_timer.get() instanceof ScheduledFuture<?> timer) {
                timer.cancel(true);
                play_timer.set(null);
            }
        }
    }
    
    private volatile ExecutorService clicker = Executors.newSingleThreadExecutor();
    private volatile Point clickdown;
    
    private void start() {
        endPlaying();
        time_counter.setValue(0);
        clicker.shutdownNow();
        clicker = Executors.newSingleThreadExecutor();
        clickdown = null;
        state = minsweeper.start(solver);
        this.revalidate();
    }
    
    public void setAuto(boolean auto) {
        this.auto = auto;
    }
    
    public MinsweeperGame setFlagChord(boolean flag_chord) {
        this.flag_chord = flag_chord;
        return this;
    }
    
    public MinsweeperGame setHoverChord(boolean hover_chord) {
        this.hover_chord = hover_chord;
        return this;
    }
    
    private final JComponent hint_component = new JPanel();
    private volatile boolean hinting = false;
    
    public void hint() {
        if (hinting)
            return;
        hinting = true;
        if (solver.solve(state) instanceof Move(var clicks, var opt_reason)) {
//            var target = board.cells.get(new BoardView.Point(x, y));
            var related = opt_reason
                    .map(Reason::related)
                    .orElse(Set.of())
                    .stream()
                    .map((e) -> new Point(e.x(), e.y()))
                    .map(board.cells::get)
                    .collect(Collectors.toSet());
            var logic = opt_reason
                    .map(Reason::logic)
                    .map(Logic::getDescription)
                    .orElse("no logic provided");
            
            related.forEach((e) -> e.setOverlay(new Color(0x8000FFFF, true)));
            clicks.forEach((e) ->
                    board.cells.get(
                            new Point(e.point().x(), e.point().y()))
                            .setOverlay((e.action() == Move.Action.LEFT) ?
                                    new Color(0x8000FF00, true) : new Color(0x80FF0000, true)));
//            target.setOverlay((action == Move.Click.LEFT) ? new Color(0x8000FF00, true) : new Color(0x80FF0000, true));

//                this.add(hint_component, hint_constraints);
            
            var component = new JComponent() {
//                    @Override
//                    public Dimension getMinimumSize() {
//                        return this.getPreferredSize();
//                    }
            };
            
            hint_component.add(component);
            
            JOptionPane.showInternalMessageDialog(component, logic);
            
            hint_component.remove(component);

//                this.remove(hint_component);
            
            related.forEach((e) -> e.setOverlay(null));
            clicks.forEach((e) ->
                    board.cells.get(
                                    new Point(e.point().x(), e.point().y()))
                            .setOverlay(null));
//            target.setOverlay(null);
            revalidate();
            
        }
        hinting = false;
    }
    
    private final ForkJoinPool pool = new ForkJoinPool(1);
    private void auto() {
        if (!auto) {
            return;
        }
        pool.submit(ForkJoinTask.adapt(() -> {
            while (state.status() == GameStatus.PLAYING) {
                var move = solver.solve(state);
                synchronized (this) {
                    if (move instanceof Move(var clicks, var _)) {
                        for (var click : clicks)
                            switch (click.action()) {
                                case LEFT -> this.state = minsweeper.leftClick(click.point().x(), click.point().y());
                                case RIGHT -> this.state = minsweeper.rightClick(click.point().x(), click.point().y());
                            }
                        revalidate();
                        try {
                            //noinspection BusyWait
                            Thread.sleep(10);
                        } catch (InterruptedException _) {
                        }
                    } else break;
                }
            }
        }));
        
    }
    
    @Override
    public void doLayout() {
        super.doLayout();
        
        
        if (state.status() == GameStatus.WON)
            remaining_mines_counter.setValue(0);
        else
            remaining_mines_counter.setValue(state.remainingMines());
        
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        g.setColor(getBackground());
        g.fillRect(0, 0, this.getWidth(), this.getHeight());
    }
    
    @Override
    public void close() {
        ex.shutdownNow();
        clicker.shutdownNow();
    }
    record Point(int x, int y) {}
    
    class BoardView extends JComponent {
        
        public static final int CELL_SIZE = 30;
        
        private final Map<MinsweeperGame.Point, CellView> cells = new HashMap<>();
        
        private Stream<MinsweeperGame.Point> neighbours(MinsweeperGame.Point point) {
            var builder = Stream.<MinsweeperGame.Point>builder();
            for (int y3 = max(0, point.y - 1); y3 <= min(size.height() - 1, point.y + 1); y3++) {
                for (int x3 = max(0, point.x - 1); x3 <= min(size.width() - 1, point.x + 1); x3++) {
                    if (!(x3 == point.x && y3 == point.y))
                        builder.add(new MinsweeperGame.Point(x3, y3));
                }
            }
            return builder.build();
        }
        
        BoardView() {
//            var constraints = new GridBagConstraints();
            
            for (int y = 0; y < size.height(); y++)
                for (int x = 0; x < size.width(); x++) {
                    var point = new MinsweeperGame.Point(x, y);
                    
                    var component = new CellView(point);
                    
                    Consumer<Object> click_action = (_) -> {
                        clicker.submit(() -> {
                            clickdown = point;
                            if (flag_chord
                                    && state.board().get(point.x, point.y).type() instanceof CellType.Safe(var n)
                                    && neighbours(point)
                                    .map((e) -> state.board().get(e.x, e.y))
                                    .filter((cell) -> cell.type() instanceof CellType.Unknown)
                                    .count() == n) {
                                neighbours(point)
                                        .filter((e) -> state.board().get(e.x, e.y).type() instanceof CellType.Unknown)
                                        .forEach((e) -> state = minsweeper.setFlagged(e.x, e.y, true));
                            }
                            
                            state = minsweeper.leftClick(point.x, point.y);
                            if (state.status() == GameStatus.PLAYING)
                                triggerPlaying();
                            clickdown = null;
                            BoardView.this.revalidate();
                            Thread.ofVirtual().start(MinsweeperGame.this::auto);
                        });
                    };
                    
//                    component.setModel(new DefaultButtonModel());

                    component.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mousePressed(MouseEvent e) {
                            update();
                        }
                        @Override
                        public void mouseReleased(MouseEvent e) {
                            update();
                        }
                        
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            update();
                            if (hover_chord && state.status() == GameStatus.PLAYING
                                    && state.board().get(point.x, point.y).type() instanceof CellType.Safe) {
                                click_action.accept(null);
                            }
                        }
                        
                        @Override
                        public void mouseExited(MouseEvent e) {
                            update();
                        }
                        
                        void update() {
                            if (state.board().get(point.x, point.y).state() == CellState.UNKNOWN && state.status() == GameStatus.PLAYING)
                                cells.get(point).setDown(component.getModel().isArmed());
                            if (state.board().get(point.x, point.y).state() == CellState.REVEALED) {
                                for (int y3 = max(0, point.y - 1); y3 <= min(size.height() - 1, point.y + 1); y3++) {
                                    for (int x3 = max(0, point.x - 1); x3 <= min(size.width() - 1, point.x + 1); x3++) {
                                        if ((state.board().get(x3, y3).state() == CellState.UNKNOWN && state.status() == GameStatus.PLAYING) || !component.getModel().isArmed()) {
                                            cells.get(new MinsweeperGame.Point(x3, y3)).setDown(component.getModel().isArmed());
                                        }
                                    }
                                }
                            }
                            MinsweeperGame.this.repaint();
                        }
                    });
                    
                    component.addActionListener(click_action::accept);
                    
                    component.addMouseListener(new MouseAdapter() {

                        @Override
                        public void mousePressed(MouseEvent e) {
                            
                            if (SwingUtilities.isRightMouseButton(e)) {
                                state = minsweeper.rightClick(point.x, point.y);
                                BoardView.this.revalidate();
                                Thread.ofVirtual().start(MinsweeperGame.this::auto);
                            }
                        }
                    });
                    
                    this.add(component);
                    cells.put(point, component);
                }
            
            this.setPreferredSize(new Dimension(CELL_SIZE * size.width(), CELL_SIZE * size.height()));
//            this.add(grid);
        }
        
        @Override
        public void doLayout() {
            var cell_size = min((float)this.getWidth() / size.width(), (float)this.getHeight() / size.height());
            var y_pos = 0f;
            for (int y = 0; y < size.height(); y++) {
                var x_pos = 0f;
                for (int x = 0; x < size.width(); x++) {
                    cells.get(new MinsweeperGame.Point(x, y))
                            .setPreferredSize(new Dimension(
                                    Math.round(x_pos + cell_size) - Math.round(x_pos),
                                    Math.round(y_pos + cell_size) - Math.round(y_pos)));
                    x_pos += cell_size;
                }
                y_pos += cell_size;
            }
            
            var xstart = ((int) ((this.getWidth() - cell_size * size.width()) / 2));
            var xpos = xstart;
            var ypos = ((int) ((this.getHeight() - cell_size * size.height()) / 2));
            for (int y = 0; y < size.height(); y++) {
                for (int x = 0; x < size.width(); x++) {
                    var point = new MinsweeperGame.Point(x, y);
                    var cell = cells.get(point);
                    var preferred_size = cell.getPreferredSize();
                    cell.setBounds(new Rectangle(xpos, ypos, preferred_size.width, preferred_size.height));
                    xpos += preferred_size.width;
                    if (x + 1 == size.width()) {
                        xpos = xstart;
                        ypos += preferred_size.height;
                    }
                }
            }
            super.doLayout();
        }
        
        class CellView extends JButton {
            private final MinsweeperGame.Point point;
            
            private volatile boolean down;
            
            private volatile Color overlay;
            
            public boolean isDown() {
                return down || point.equals(clickdown);
            }
            
            public CellView setDown(boolean down) {
                this.down = down;
                return this;
            }
            
            public CellView setOverlay(Color overlay) {
                this.overlay = overlay;
                this.repaint();
                return this;
            }
            
            CellView(MinsweeperGame.Point point) {
                this.point = point;
                
                this.setFocusable(false);
                this.setBorderPainted(false);
                this.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
            }
            
            @Override
            protected void paintComponent(Graphics g1) {
                var g = ((Graphics2D) g1);
                var cell = state.board().get(point.x, point.y);
                
                var file_name = "cell/" + switch (cell.state()) {
                    case REVEALED -> switch (cell.type()) {
                        case CellType.Safe(var number) when number == 0 -> "celldown";
                        case CellType.Safe(var number) -> "cell" + number;
                        case CellType.Mine _ -> "blast";
                        case CellType.Unknown _ -> (isDown()) ? "celldown" : "cellup";
                    };
                    case UNKNOWN -> switch (cell.type()) {
                        case CellType.Mine _ -> "cellmine";
                        default -> (isDown()) ? "celldown" : "cellup";
                    };
                    case FLAGGED -> (cell.type() instanceof CellType.Safe) ? "falsemine" : "cellflag";
                };
                
                var image = getAsset(file_name);
                
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), null);
                
                if (overlay instanceof Color color) {
                    g.setColor(color);
                    g.fillRect(0, 0, this.getWidth(), this.getHeight());
                }
            }
        }
    }
    static class BufferedImageTranscoder extends ImageTranscoder {
        @Override
        public BufferedImage createImage(int w, int h) {
            return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        
        @Override
        public void writeImage(BufferedImage img, TranscoderOutput output) {
            this.img = img;
        }
        
        public BufferedImage getBufferedImage() {
            return img;
        }
        private BufferedImage img = null;
    }
    
    class BorderView extends JComponent {
        enum Type {
            BOTTOM_LEFT("bottomleft"),
            BOTTOM_RIGHT("bottomright"),
            COUNTER_LEFT("counterleft"),
            COUNTER_TOP("countertop"),
            COUNTER_BOTTOM("counterbottom"),
            COUNTER_RIGHT("counterright"),
            LEFT_RIGHT("leftright"),
            MIDDLE_LEFT("middleleft"),
            MIDDLE_RIGHT("middleright"),
            TOP_BOTTOM("topbottom"),
            TOP_LEFT("topleft"),
            TOP_RIGHT("topright"),
            ;
            
            final String asset_name;
            
            Type(String asset_name) {
                this.asset_name = asset_name;
            }
        }
        
        private final String asset_path;
        BorderView(Type type) {
            this.asset_path = "border/" + type.asset_name;
            
            var image = getAsset(asset_path);
            this.setPreferredSize(new Dimension(image.getWidth() / 5, image.getHeight() / 5));
            this.setMinimumSize(this.getPreferredSize());
        }
        
        
        @Override
        protected void paintComponent(Graphics g1) {
            super.paintComponent(g1);
            var g = ((Graphics2D) g1);
            
            var image = getAsset(asset_path);
            g.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), null);
        }
    }
    
    class CounterView extends JComponent {
        
        public static final int DIGIT_WIDTH = 26, DIGIT_HEIGHT = 50;
        
        private final int digits;
        
        private volatile int value;
        
        private final List<DigitView> images = new ArrayList<>();
        
        CounterView(int digits) {
            this.setLayout(new GridBagLayout());
            var c = new GridBagConstraints();
            
            c.gridx = c.gridy = 0;
            c.gridheight = 3;
            c.fill = GridBagConstraints.VERTICAL;
            this.add(new BorderView(BorderView.Type.COUNTER_LEFT), c);
            
            c.gridx++;
            c.gridheight = 1;
            c.gridwidth = digits;
            c.fill = GridBagConstraints.HORIZONTAL;
            this.add(new BorderView(BorderView.Type.COUNTER_TOP), c);
            
            c.gridy = 2;
            this.add(new BorderView(BorderView.Type.COUNTER_BOTTOM), c);
            
            c.fill = GridBagConstraints.NONE;
            c.gridy = 1;
            c.gridwidth = 1;
            this.digits = digits;
            for (int i = 0; i < digits; i++) {
                var view = new DigitView();
                this.add(view, c);
                images.add(view);
                c.gridx++;
            }
            
            c.gridy = 0;
            c.gridheight = 3;
            c.fill = GridBagConstraints.VERTICAL;
            this.add(new BorderView(BorderView.Type.COUNTER_RIGHT), c);
        }
        
        public CounterView setValue(int value) {
//            images.clear();
            this.value = value;
            var str = ("%0" + digits + "d").formatted(value);
            if (str.length() != digits)
                throw new IllegalArgumentException();
            var it = images.iterator();
            for (var c : str.toCharArray()) {
                it.next().setValue(c);
            }
            repaint();
            return this;
        }
        
        public int getValue() {
            return value;
        }
        
        class DigitView extends JComponent {
            
            private Image image;
            
            DigitView() {
                this.setPreferredSize(new Dimension(DIGIT_WIDTH, DIGIT_HEIGHT));
                this.setMinimumSize(this.getPreferredSize());
            }
            
            public DigitView setValue(char value) {
                this.image = getAsset("counter/counter" + value);
                repaint();
                return this;
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(image, 0, 0, DIGIT_WIDTH, DIGIT_HEIGHT, null);
            }
        }
    }
    
    class RestartButton extends JButton {
        
        public static final int SIZE = 50;
        
        RestartButton() {
            
            this.setFocusable(true);
            this.setBorderPainted(false);
            this.setPreferredSize(new Dimension(SIZE, SIZE));
            this.setMinimumSize(this.getPreferredSize());
        }
        
        @Override
        protected void paintComponent(Graphics g1) {
            var g = ((Graphics2D) g1);
            var file_name = "faces/";
            if (getModel().isArmed())
                file_name += "smilefacedown";
            else if (board.cells.values().stream().anyMatch(BoardView.CellView::isDown))
                file_name += "clickface";
            else
                file_name += switch (state.status()) {
                    case LOST -> "lostface";
                    case PLAYING -> "smileface";
                    case WON -> "winface";
                    default -> throw new IllegalArgumentException();
                };
            
            var image = getAsset(file_name);
            
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), null);
        }
    }
}
