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
import java.awt.event.ItemEvent;

public class Main {
    
    private static JFrame frame;
    
    private static MinsweeperGame game;
    
    public static void main(String[] args) {
        
        System.setProperty("apple.awt.application.name", "Minsweeper");
//        System.setProperty("apple.laf.useScreenMenuBar", "true");
        
        
        frame = new JFrame();
        
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        
        var menu_bar = makeMenuBar();
        
        frame.setJMenuBar(menu_bar);
        
        game = new MinsweeperGame(ConventionalSize.BEGINNER.size);
        
        frame.add(game);
        
        frame.pack();
//        frame.setResizable(false);
        frame.setVisible(true);
    }
    
    private static JMenuBar makeMenuBar() {
        var menu_bar = new JMenuBar();
        
        var size_menu = new JMenu("Size");
        
        var beginner_size = size_menu.add("Beginner");
        beginner_size.addActionListener((e) -> {
            changeGame(ConventionalSize.BEGINNER.size);
        });
        
        var intermediate_size = size_menu.add("Intermediate");
        intermediate_size.addActionListener((e) -> {
            changeGame(ConventionalSize.INTERMEDIATE.size);
        });
        
        var expert_size = size_menu.add("Expert");
        expert_size.addActionListener((e) -> {
            changeGame(ConventionalSize.EXPERT.size);
        });
        
        
        menu_bar.add(size_menu);
        
        var cheats_menu = new JMenu("Cheats");
        
        var auto_checkbox = new JCheckBox("Auto Mode");
        auto_checkbox.addItemListener((e) -> {
            game.setAuto(e.getStateChange() == ItemEvent.SELECTED);
        });
        
        cheats_menu.add(auto_checkbox);
        
        menu_bar.add(cheats_menu);
        
        return menu_bar;
    }
    
    private static void changeGame(BoardSize size) {
        frame.remove(game);
        game = new MinsweeperGame(size);
        frame.add(game);
        frame.pack();
    }
}