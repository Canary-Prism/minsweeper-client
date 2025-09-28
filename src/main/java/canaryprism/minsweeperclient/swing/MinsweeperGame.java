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
import canaryprism.minsweeper.solver.Move;
import canaryprism.minsweeper.solver.Solver;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class MinsweeperGame extends JComponent {
    
    private final BoardSize size;
    private final Solver solver;
    
    private final Minsweeper minsweeper;
    
    private volatile GameState state;
    
    
    private final CounterView remaining_mines_counter, time_counter;
    
    private final JLabel status_label, remaining_mines_label;
    
    private final BoardView board;
    
    private boolean auto = false;
    
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
        this.minsweeper = new Minsweeper(size, () -> endPlaying(), () -> endPlaying());
        this.theme = theme;
        
        this.setOpaque(true);
        reloadBackground();
        
//        this.setLayout(new BorderLayout());
//        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
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
        this.status_label = new JLabel("Status: Not Playing");
        this.remaining_mines_counter = new CounterView(max(
                String.valueOf(size.mines()).length(),
                String.valueOf(size.mines() - size.width() * size.height()).length()));
        remaining_mines_counter.setValue(size.mines());
        
        this.add(remaining_mines_counter, c);
        
        c.gridx++;
        c.weightx = 0;
        c.anchor = GridBagConstraints.CENTER;
        var start_button = new RestartButton();
        start_button.addActionListener((_) -> start());
        this.add(start_button, c);
        
        c.gridx++;
        c.weightx = 1;
        c.anchor = GridBagConstraints.EAST;
        this.remaining_mines_label = new JLabel("Mines: " + size.mines());
        remaining_mines_label.setHorizontalAlignment(SwingConstants.RIGHT);
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
        this.board = new BoardView();
        this.add(board, c);
    }
    
    private void reloadBackground() {
        this.setBackground(getBackgroundColor());
    }
    
    public Texture getTheme() {
        return theme;
    }
    
    public MinsweeperGame setTheme(Texture theme) {
        this.theme = theme;
        image_map.clear();
        reloadBackground();
        repaint();
        return this;
    }
    
    private volatile boolean playing;
    private volatile ScheduledFuture<?> play_timer;
    private static final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
    
    private void triggerPlaying() {
        if (!playing) {
            playing = true;
            play_timer = ex.scheduleAtFixedRate(() -> {
                if (time_counter.getValue() != 1000 - 1) {
                    time_counter.setValue(time_counter.getValue() + 1);
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }
    
    private void endPlaying() {
        if (playing) {
            playing = false;
            if (play_timer != null) {
                play_timer.cancel(true);
                play_timer = null;
            }
            time_counter.setValue(0);
        }
    }
    
    private void start() {
        endPlaying();
        state = minsweeper.start(solver);
        this.revalidate();
    }
    
    public void setAuto(boolean auto) {
        this.auto = auto;
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
                    if (move instanceof Move(Move.Point(var x, var y), var action)) {
                        switch (action) {
                            case LEFT -> this.state = minsweeper.leftClick(x, y);
                            case RIGHT -> this.state = minsweeper.rightClick(x, y);
                        }
                        revalidate();
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                        }
                    } else break;
                }
            }
        }));
        
    }
    
    @Override
    public void doLayout() {
        super.doLayout();
        
        var status_str = switch (state.status()) {
            case PLAYING -> "Playing";
            case NEVER -> "Not Playing";
            case LOST -> "Lost";
            case WON -> "Won";
        };
        status_label.setText("Status: " + status_str);
        
        if (state.status() == GameStatus.WON)
            remaining_mines_counter.setValue(0);
        else
            remaining_mines_counter.setValue(state.remainingMines());
        
        if (state.status() == GameStatus.WON)
            remaining_mines_label.setText("Mines: 0");
        else
            remaining_mines_label.setText("Mines: " + state.remainingMines());
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        g.setColor(getBackground());
        g.fillRect(0, 0, this.getWidth(), this.getHeight());
    }
    
    class BoardView extends JComponent {
        
        record Point(int x, int y) {}
        
        private final Map<Point, CellView> cells = new HashMap<>();
        
        BoardView() {
            this.setFont(Font.decode("Menlo").deriveFont(32f));
            
//            var constraints = new GridBagConstraints();
            
            for (int y = 0; y < size.height(); y++)
                for (int x = 0; x < size.width(); x++) {
                    var point = new Point(x, y);
                    
                    var component = new CellView(point);
                    
//                    component.setModel(new DefaultButtonModel());

                    component.addChangeListener((_) -> {
                        if (state.board().get(point.x, point.y) instanceof Cell.Revealed) {
                            for (int y3 = max(0, point.y - 1); y3 <= min(size.height() - 1, point.y + 1); y3++) {
                                for (int x3 = max(0, point.x - 1); x3 <= min(size.width() - 1, point.x + 1); x3++) {
                                    if (state.board().get(x3, y3) instanceof Cell.Unknown || !component.getModel().isArmed()) {
                                        cells.get(new Point(x3, y3)).getModel().setArmed(component.getModel().isArmed());
                                    }
                                }
                            }
                        }
                        MinsweeperGame.this.repaint();
                    });
                    
                    component.addActionListener((e) -> {
                        state = minsweeper.leftClick(point.x, point.y);
                        if (state.status() == GameStatus.PLAYING)
                            triggerPlaying();
                        BoardView.this.revalidate();
                        Thread.ofVirtual().start(MinsweeperGame.this::auto);
                    });
                    
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
                    
//                    component.setBorder(new LineBorder(Color.BLACK));
                    
//                    constraints.gridx = point.x;
//                    constraints.gridy = point.y;
                    
                    this.add(component);
                    cells.put(point, component);
                }
            
            this.setMinimumSize(new Dimension(30 * size.width(), 30 * size.height()));
            this.setPreferredSize(this.getMinimumSize());
//            this.add(grid);
        }
        
        @Override
        public void doLayout() {
            var cell_size = min((float)this.getWidth() / size.width(), (float)this.getHeight() / size.height());
            var y_pos = 0f;
            for (int y = 0; y < size.height(); y++) {
                var x_pos = 0f;
                for (int x = 0; x < size.width(); x++) {
                    cells.get(new Point(x, y))
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
                    var point = new Point(x, y);
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
            private final Point point;
            
            CellView(Point point) {
                this.point = point;
                
                this.setFocusable(false);
                this.setBorderPainted(false);
                this.setPreferredSize(new Dimension(30, 30));
            }
            
            @Override
            protected void paintComponent(Graphics g1) {
                var g = ((Graphics2D) g1);
                var cell = state.board().get(point.x, point.y);
                
                var file_name = "cell/" + switch (cell) {
                    case Cell.Revealed(var number) when number == 0 -> "celldown";
                    case Cell.Revealed(var number) -> "cell" + number;
                    case Cell.Unknown _ -> {
//                                    System.out.println("unknown cell repaint with armed: " + getModel().isArmed());
                        if (getModel().isArmed())
                            yield "celldown";
                        else
                            yield "cellup";
                    }
                    case Cell.MarkedMine _ -> "cellflag";
                    case Cell.Mine _ -> "cellmine";
                    case Cell.FalseMine _ -> "falsemine";
                    case Cell.ExplodedMine _ -> "blast";
                };
                
                var image = getAsset(file_name);
                
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), null);
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
        
        //        @Override
//        protected void paintComponent(Graphics g) {
//            super.paintComponent(g);
//
//            var x = 0;
//            for (var image : images) {
//                g.drawImage(image, x, 0, DIGIT_WIDTH, DIGIT_HEIGHT, null);
//                x += DIGIT_WIDTH;
//            }
//        }
        
        class DigitView extends JComponent {
            private char value;
            private Image image;
            
            DigitView() {
                this.setPreferredSize(new Dimension(DIGIT_WIDTH, DIGIT_HEIGHT));
                this.setMinimumSize(this.getPreferredSize());
            }
            
            public char getValue() {
                return value;
            }
            
            public DigitView setValue(char value) {
                this.value = value;
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
        
        RestartButton() {
            
            this.setFocusable(true);
            this.setBorderPainted(false);
            this.setPreferredSize(new Dimension(50, 50));
            this.setMinimumSize(this.getPreferredSize());
        }
        
        @Override
        protected void paintComponent(Graphics g1) {
            var g = ((Graphics2D) g1);
            var file_name = "faces/";
            if (getModel().isArmed())
                file_name += "smilefacedown";
            else if (board.cells.values().stream().anyMatch((e) -> e.getModel().isArmed()))
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
