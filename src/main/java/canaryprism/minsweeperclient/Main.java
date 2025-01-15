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

package canaryprism.minsweeperclient;

import canaryprism.minsweeper.BoardSize;
import canaryprism.minsweeper.ConventionalSize;
import canaryprism.minsweeperclient.swing.MinsweeperGame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CompletableFuture;

public class Main {
    
    private static JFrame frame;
    
    private static MinsweeperGame game;
    
    private static boolean auto;
    
    public static void main(String[] args) {
        
        System.setProperty("apple.awt.application.name", "Minsweeper");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        
        
        frame = new JFrame();
        
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        
        var menu_bar = makeMenuBar();
        
        frame.setJMenuBar(menu_bar);
        
        game = new MinsweeperGame(ConventionalSize.BEGINNER.size);
        
        frame.add(game);
        
        frame.pack();
        frame.setResizable(false);
        frame.setVisible(true);
    }
    
    private static JMenuBar makeMenuBar() {
        var menu_bar = new JMenuBar();
        
        var size_menu = new JMenu("Size");
        size_menu.setMnemonic(KeyEvent.VK_S);
        
        var beginner_size = size_menu.add("Beginner");
        beginner_size.setMnemonic(KeyEvent.VK_B);
        beginner_size.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, KeyEvent.ALT_DOWN_MASK));
        beginner_size.addActionListener((e) -> {
            changeGame(ConventionalSize.BEGINNER.size);
        });
        
        var intermediate_size = size_menu.add("Intermediate");
        intermediate_size.setMnemonic(KeyEvent.VK_I);
        intermediate_size.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.ALT_DOWN_MASK));
        intermediate_size.addActionListener((e) -> {
            changeGame(ConventionalSize.INTERMEDIATE.size);
        });
        
        var expert_size = size_menu.add("Expert");
        expert_size.setMnemonic(KeyEvent.VK_E);
        expert_size.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.ALT_DOWN_MASK));
        expert_size.addActionListener((e) -> {
            changeGame(ConventionalSize.EXPERT.size);
        });
        
        var custom_size = size_menu.add("Custom");
        custom_size.setMnemonic(KeyEvent.VK_C);
        custom_size.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.ALT_DOWN_MASK));
        custom_size.addActionListener((_) -> Thread.ofVirtual().start(() -> changeGame(promptBoardSize())));
        
        
        menu_bar.add(size_menu);
        
        var cheats_menu = new JMenu("Cheats");
        cheats_menu.setMnemonic(KeyEvent.VK_C);
        
        var auto_checkbox = new JCheckBoxMenuItem("Auto Mode");
        auto_checkbox.setMnemonic(KeyEvent.VK_A);
        auto_checkbox.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.ALT_DOWN_MASK));
        auto_checkbox.addItemListener((e) -> {
            auto = (e.getStateChange() == ItemEvent.SELECTED);
            game.setAuto(auto);
        });
        
        cheats_menu.add(auto_checkbox);
        
        menu_bar.add(cheats_menu);
        
        return menu_bar;
    }
    
    private static void changeGame(BoardSize size) {
        frame.remove(game);
        game = new MinsweeperGame(size);
        game.setAuto(auto);
        frame.add(game);
        frame.pack();
    }
    
    
    
    private static BoardSize promptBoardSize() {
        
        class SizeDialig {
            static JDialog dialog;
            
            static CompletableFuture<BoardSize> future;
            
            static {
                dialog = new JDialog(frame);
                
                var panel = new JPanel();
                
                dialog.setContentPane(panel);
                
                panel.setLayout(new GridBagLayout());
                
                var constraints = new GridBagConstraints();
                
                constraints.gridheight = 1;
                
                constraints.anchor = GridBagConstraints.EAST;
                
                var width_label = new JLabel("Width: ");
                var width_spinner = new JSpinner(new SpinnerNumberModel(10, 1, Integer.MAX_VALUE, 1));
                
                constraints.gridx = 0;
                constraints.gridwidth = 1;
                panel.add(width_label, constraints);
                constraints.gridx = 1;
                constraints.gridwidth = 2;
                panel.add(width_spinner, constraints);
                
                var height_label = new JLabel("Height: ");
                var height_spinner = new JSpinner(new SpinnerNumberModel(10, 1, Integer.MAX_VALUE, 1));
                
                constraints.gridx = 0;
                constraints.gridwidth = 1;
                panel.add(height_label, constraints);
                constraints.gridx = 1;
                constraints.gridwidth = 2;
                panel.add(height_spinner, constraints);
                
                var mines_label = new JLabel("Mines: ");
                var mines_spinner = new JSpinner(new SpinnerNumberModel(10, 1, Integer.MAX_VALUE, 1));
                
                constraints.gridx = 0;
                constraints.gridwidth = 1;
                panel.add(mines_label, constraints);
                constraints.gridx = 1;
                constraints.gridwidth = 2;
                panel.add(mines_spinner, constraints);
                
                var done_button = new JButton("Done");
                
                constraints.gridx = 2;
                constraints.gridwidth = 1;
                constraints.anchor = GridBagConstraints.EAST;
                panel.add(done_button, constraints);
                
                panel.setBorder(new EmptyBorder(5, 5, 5, 5));
                
                future = new CompletableFuture<BoardSize>();
                
                done_button.addActionListener((_) -> {
                    try {
                        var size = new BoardSize(
                                ((int) width_spinner.getValue()),
                                ((int) height_spinner.getValue()),
                                ((int) mines_spinner.getValue())
                        );
                        
                        future.complete(size);
                        dialog.setVisible(false);
                    } catch (IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(dialog, e.getMessage(), "Invalid Size", JOptionPane.ERROR_MESSAGE);
                    }
                });
                
                dialog.pack();
                dialog.setResizable(false);
            }
        }
        
        SizeDialig.future = new CompletableFuture<>();
        SizeDialig.dialog.setVisible(true);
        
        return SizeDialig.future.join();
    }
}