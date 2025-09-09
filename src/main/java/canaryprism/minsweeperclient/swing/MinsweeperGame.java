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
import org.apache.batik.transcoder.image.PNGTranscoder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class MinsweeperGame extends JComponent {
    
    private final BoardSize size;
    private final Solver solver;
    
    private final Minsweeper minsweeper;
    
    private volatile GameState state;
    
    private final JLabel status_label, remaining_mines_label;
    
    private boolean auto = false;
    
    private volatile Texture theme;
    
    public MinsweeperGame(BoardSize size, Solver solver, Texture theme) {
        this.size = size;
        this.solver = solver;
        this.minsweeper = new Minsweeper(size);
        this.theme = theme;
        
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        
        var menu = new JPanel(new BorderLayout());
        menu.setBorder(new EmptyBorder(5, 5, 5, 5));

        this.status_label = new JLabel("Status: Not Playing");
        menu.add(status_label, BorderLayout.WEST);
        
        var start_panel = new JPanel();
        start_panel.setLayout(new BoxLayout(start_panel, BoxLayout.X_AXIS));
        
        start_panel.add(Box.createHorizontalGlue());
        
        var start_button = new JButton("Restart");
        start_button.addActionListener((_) -> start());
        start_panel.add(start_button);
        
        start_panel.add(Box.createHorizontalGlue());
        
        menu.add(start_panel, BorderLayout.CENTER);
        
        
        this.remaining_mines_label = new JLabel("Mines: " + size.mines());
        
        menu.add(remaining_mines_label, BorderLayout.EAST);
        
        
        this.add(menu);
        
        this.state = minsweeper.start(solver);
        
        this.add(new BoardView());
    }
    
    public Texture getTheme() {
        return theme;
    }
    
    public MinsweeperGame setTheme(Texture theme) {
        this.theme = theme;
        BoardView.image_map.clear();
        repaint();
        return this;
    }
    
    private void start() {
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
            remaining_mines_label.setText("Mines: 0");
        else
            remaining_mines_label.setText("Mines: " + state.remainingMines());
        repaint();
    }
    
    class BoardView extends JComponent {
        
        record Point(int x, int y) {}
        
        private static final Map<Point, CellView> cells = new HashMap<>();
        
        private static final Map<String, BufferedImage> image_map = Collections.synchronizedMap(new HashMap<>());
        
        BoardView() {
            this.setLayout(new GridLayout(size.height(), size.width()));
            this.setFont(Font.decode("Menlo").deriveFont(32f));
            
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
                    });
                    
                    component.addActionListener((e) -> {
                        state = minsweeper.leftClick(point.x, point.y);
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
                    
                    this.add(component);
                    cells.put(point, component);
                }
        }
        
        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
        
        class CellView extends JButton {
            private final Point point;
            private BufferedImage image;
            private String last_file_name = "";
            
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
                
                var file_name = theme.asset_path_name + "/" + switch (cell) {
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
                
                if (!file_name.equals(last_file_name)) {
                    
                    image = image_map.computeIfAbsent(file_name, (_) -> {
                        var url = Objects.requireNonNull(BoardView.class.getResource("/minsweeper/cell/" + file_name + ".svg")).toString();
                        
                        BufferedImageTranscoder transcoder = new BufferedImageTranscoder();
                        
                        transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, ((float) this.getWidth() * 10));
                        transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, ((float) this.getHeight() * 10));
                        
                        TranscoderInput input = new TranscoderInput(url);
                        try {
                            transcoder.transcode(input, null);
                        } catch (TranscoderException e) {
                            throw new RuntimeException(e);
                        }
                        
                        return transcoder.getBufferedImage();
                    });

//                            g.drawString(switch (cell) {
//                                case Cell.Revealed(var number) when number == 0 -> "";
//                                case Cell.Revealed(var number) -> String.valueOf(number);
//                                case Cell.Unknown _ -> "▩";
//                                case Cell.MarkedMine _ -> "!";
//                                case Cell.Mine _ -> "*";
//                                case Cell.FalseMine _ -> "Ø";
//                                case Cell.ExplodedMine _ -> "X";
//                            }, 0, 30);
                    
                    last_file_name = file_name;
                }
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
}
