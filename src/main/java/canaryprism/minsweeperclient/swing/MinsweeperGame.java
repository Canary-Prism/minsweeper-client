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
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class MinsweeperGame extends JComponent {
    
    private final BoardSize size;
    
    private final Minsweeper minsweeper;
    
    private volatile GameState state;
    
    private boolean auto = false;
    
    public MinsweeperGame(BoardSize size) {
        this.size = size;
        this.minsweeper = new Minsweeper(size);
        
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        
        var start_button = new JButton();
        start_button.addActionListener((_) -> start());
        
        this.add(start_button);
        
        this.state = minsweeper.start();
        
        this.add(new BoardView());
    }
    
    private void start() {
        state = minsweeper.start();
        this.repaint();
    }
    
    public void setAuto(boolean auto) {
        this.auto = auto;
    }
    
    private void auto() {
        if (!auto) {
            return;
        }
        
        class Performed {
            static volatile boolean performed = true;
        }
        
        Performed.performed = true;
        
        while (Performed.performed && state.status() == GameStatus.PLAYING) {
            Performed.performed = false;
            for (int y2 = 0; y2 < size.height(); y2++) {
                for (int x2 = 0; x2 < size.width(); x2++) {
                    
                    if (!(state.board().get(x2, y2) instanceof Cell.Revealed(var number)))
                        continue;
                    
                    var marked_mines = 0;
                    var empty_spaces = 0;
                    
                    for (int y3 = max(0, y2 - 1); y3 <= min(size.height() - 1, y2 + 1); y3++) {
                        for (int x3 = max(0, x2 - 1); x3 <= min(size.width() - 1, x2 + 1); x3++) {
                            if (state.board().get(x3, y3) instanceof Cell.MarkedMine) {
                                marked_mines++;
                                empty_spaces++;
                            } else if (state.board().get(x3, y3) instanceof Cell.Unknown) {
                                empty_spaces++;
                            }
                        }
                    }
                    
                    if (number == marked_mines && empty_spaces > marked_mines) {
//                            try? await Task.sleep(nanoseconds: 50_000_000)
                        state = minsweeper.leftClick(x2, y2);
                        repaint();
                        try {
                            Thread.sleep(3);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        Performed.performed = true;
                    } else if (number == empty_spaces) {
                        for (int y3 = max(0, y2 - 1); y3 <= min(size.height() - 1, y2 + 1); y3++) {
                            for (int x3 = max(0, x2 - 1); x3 <= min(size.width() - 1, x2 + 1); x3++) {
                                if (state.board().get(x3, y3) instanceof Cell.Unknown) {
                                    state = minsweeper.rightClick(x3, y3);
                                    repaint();
                                    try {
                                        Thread.sleep(3);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                    Performed.performed = true;
                                }
                            }
                        }
                    } else if (number < marked_mines) {
                        for (int y3 = max(0, y2 - 1); y3 <= min(size.height() - 1, y2 + 1); y3++) {
                            for (int x3 = max(0, x2 - 1); x3 <= min(size.width() - 1, x2 + 1); x3++) {
                                if (state.board().get(x3, y3) instanceof Cell.MarkedMine) {
                                    state = minsweeper.rightClick(x3, y3);
                                    repaint();
                                    try {
                                        Thread.sleep(3);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                    Performed.performed = true;
                                }
                            }
                        }
                    }
                    
                    
                }
            }
            
            //logical deduction time :c
            if (!Performed.performed) {
                record Point(int x, int y) {}
                
                class Logic {
                    
                    void logic(int x2, int y2, int de, ArrayList<Point> grid) {
                        if (!(state.board().get(x2, y2) instanceof Cell.Revealed(var this_num)))
                            return;
                        
                        var index = 0;
                        var claimed_surroundings = new ArrayList<Integer>();
                        for (int y3 = max(0, y2 - 1); y3 <= min(size.height() - 1, y2 + 1); y3++) {
                            for (int x3 = max(0, x2 - 1); x3 <= min(size.width() - 1, x2 + 1); x3++) {
                                if (x3 == x2 && y3 == y2) {
                                    index += 1;
                                    continue;
                                }
                                for (var item : grid) {
                                    if (item.x == x3 && item.y == y3) {
                                        claimed_surroundings.add(index);
                                    }
                                }
                                index += 1;
                            }
                        }
                        var strong_match = claimed_surroundings.size() == grid.size();
                        var flagged = 0;
                        var empty = 0;
                        index = 0;
                        for (int y3 = max(0, y2 - 1); y3 <= min(size.height() - 1, y2 + 1); y3++) {
                            for (int x3 = max(0, x2 - 1); x3 <= min(size.width() - 1, x2 + 1); x3++) {
                                if (x3 == x2 && y3 == y2) {
                                    index += 1;
                                    continue;
                                }
                                if (claimed_surroundings.contains(index)) {
                                    index += 1;
                                    continue;
                                }
                                switch (state.board().get(x3, y3)) {
                                    case Cell.MarkedMine _ -> flagged++;
                                    case Cell.Unknown _ -> empty++;
                                    default -> {
                                    }
                                }
                                index++;
                            }
                        }
                        
                        if (strong_match && flagged + de == this_num && empty > 0) {
                            index = 0;
                            for (int y3 = max(0, y2 - 1); y3 <= min(size.height() - 1, y2 + 1); y3++) {
                                for (int x3 = max(0, x2 - 1); x3 <= min(size.width() - 1, x2 + 1); x3++) {
                                    if (x3 == x2 && y3 == y2) {
                                        index++;
                                        continue;
                                    }
                                    if (claimed_surroundings.contains(index)) {
                                        index++;
                                        continue;
                                    }
                                    if (state.board().get(x3, y3) instanceof Cell.Unknown) {
//                                        try? await Task.sleep(nanoseconds: 50_000_000)
                                        state = minsweeper.leftClick(x3, y3);
                                        repaint();
                                        try {
                                            Thread.sleep(3);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                        Performed.performed = true;
                                    }
                                    index += 1;
                                }
                            }
                        } else if (flagged + de + empty == this_num) {
                            index = 0;
                            for (int y3 = max(0, y2 - 1); y3 <= min(size.height() - 1, y2 + 1); y3++) {
                                for (int x3 = max(0, x2 - 1); x3 <= min(size.width() - 1, x2 + 1); x3++) {
                                    if (x3 == x2 && y3 == y2) {
                                        index += 1;
                                        continue;
                                    }
                                    if (claimed_surroundings.contains(index)) {
                                        index += 1;
                                        continue;
                                    }
                                    if (state.board().get(x3, y3) instanceof Cell.Unknown) {
                                        state = minsweeper.rightClick(x3, y3);
                                        repaint();
                                        try {
                                            Thread.sleep(3);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                        Performed.performed = true;
                                    }
                                    index += 1;
                                }
                            }
                        }
                    }
                }
                
                var logic = new Logic();
                
                for (int y2 = 0; y2 < size.height(); y2++) {
                    for (int x2 = 0; x2 < size.width(); x2++) {
                        if (state.board().get(x2, y2) instanceof Cell.Revealed(var this_num)) {
                            if (this_num <= 0)
                                continue;
                            
                            var flagged = 0;
                            var empty = 0;
                            var grid = new ArrayList<Point>();
                            
                            for (int y3 = max(0, y2 - 1); y3 <= min(size.height() - 1, y2 + 1); y3++) {
                                for (int x3 = max(0, x2 - 1); x3 <= min(size.width() - 1, x2 + 1); x3++) {
                                    if (state.board().get(x3, y3) instanceof Cell.MarkedMine) {
                                        flagged += 1;
                                    } else if (state.board().get(x3, y3) instanceof Cell.Unknown) {
                                        grid.add(new Point(x3, y3));
                                        empty += 1;
                                    }
                                }
                            }
                            if (!(flagged < this_num && empty > 0))
                                continue;
                            
                            final var de = this_num - flagged;
                            
                            
                            for (int y3 = max(0, y2 - 2); y3 <= min(size.height() - 1, y2 + 2); y3++) {
                                for (int x3 = max(0, x2 - 2); x3 <= min(size.width() - 1, x2 + 2); x3++) {
                                    if (state.board().get(x3, y3) instanceof Cell.Revealed) {
                                        logic.logic(x3, y3, de, grid);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            
            if (Performed.performed) {
                // try? await Task.sleep(nanoseconds: 100_000_000)

                Thread.ofVirtual().start(this::auto);
                try {
                    Thread.sleep(3);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Performed.performed = false;
            }
        }
        
        
        if (state.remainingMines() == 0) {
            for (int y2 = 0; y2 < size.height(); y2++) {
                for (int x2 = 0; x2 < size.width(); x2++) {
                    if (state.board().get(x2, y2) instanceof Cell.Unknown) {
                        state = minsweeper.leftClick(x2, y2);
                        repaint();
                        try {
                            Thread.sleep(3);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        
        repaint();
    }
    
    class BoardView extends JComponent {
        
        private static final Map<String, BufferedImage> image_map = new HashMap<>();
        
        BoardView() {
            this.setLayout(new GridLayout(size.height(), size.width()));
            this.setFont(Font.decode("Menlo").deriveFont(32f));
            
            for (int y = 0; y < size.height(); y++)
                for (int x = 0; x < size.width(); x++) {
                    int final_x = x;
                    int final_y = y;
                    
                    var component = new JButton() {
                        private BufferedImage image;
                        private String last_file_name = "";
                        @Override
                        protected void paintComponent(Graphics g1) {
                            var g = ((Graphics2D) g1);
                            var cell = state.board().get(final_x, final_y);
                            
                            var file_name = switch (cell) {
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
                    };
                    
//                    component.setModel(new DefaultButtonModel());
                    
                    component.setLayout(new BorderLayout());
                    
                    component.addActionListener((e) -> {
                        state = minsweeper.leftClick(final_x, final_y);
                        BoardView.this.repaint();
                        Thread.ofVirtual().start(MinsweeperGame.this::auto);
                    });
                    
                    component.addMouseListener(new MouseAdapter() {

                        @Override
                        public void mousePressed(MouseEvent e) {
                            
                            if (SwingUtilities.isRightMouseButton(e)) {
                                state = minsweeper.rightClick(final_x, final_y);
                                BoardView.this.repaint();
                                Thread.ofVirtual().start(MinsweeperGame.this::auto);
                            }
                        }
                    });
                    
                    component.setPreferredSize(new Dimension(30, 30));
//                    component.setBorder(new LineBorder(Color.BLACK));
                    
                    this.add(component);
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
