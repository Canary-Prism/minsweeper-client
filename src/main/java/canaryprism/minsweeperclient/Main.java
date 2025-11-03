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
import canaryprism.minsweeper.solver.Solver;
import canaryprism.minsweeperclient.swing.MinsweeperGame;
import canaryprism.minsweeperclient.swing.Texture;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import dev.dirs.ProjectDirectories;
import org.apache.commons.lang3.SystemUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class Main {
    
    private static final ProjectDirectories DIRS = ProjectDirectories.from("", "canaryprism", "minsweeper-client");
    private static final ObjectMapper mapper = JsonMapper.shared();
    
    private static JFrame frame;
    
    private static final Map<Class<? extends Solver>, Solver> solver_map = new HashMap<>();
    
    private static class Settings {
        
        public volatile boolean auto = false;
        public volatile boolean flag_chord = false;
        public volatile boolean hover_chord = false;
        public volatile BoardSize size = ConventionalSize.BEGINNER.size;
        public volatile Class<? extends Solver> solver = Solver.getDefault().getClass();
        public volatile Texture texture = Texture.LIGHT;
        @SuppressWarnings("unchecked")
        public volatile Class<? extends LookAndFeel> laf = (Class<? extends LookAndFeel>) Class.forName(UIManager.getSystemLookAndFeelClassName());
        public volatile Optional<Dimension> frame_size;
        
        private Settings() throws ClassNotFoundException {
        }
    }
    private static Settings settings;
    private static Path settings_path;
    
    private static void loadSettings(Path path) {
        settings = mapper.readValue(path, Settings.class);
    }
    private static void saveSettings(Path path) {
        mapper.writeValue(path, settings);
    }
    
    private static MinsweeperGame game;
    
    
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        
        System.setProperty("apple.awt.application.name", "Minsweeper");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("apple.awt.application.appearance", "system");
        
        FlatDarkLaf.installLafInfo();
        FlatDarculaLaf.installLafInfo();
        FlatIntelliJLaf.installLafInfo();
        FlatLightLaf.installLafInfo();
        FlatMacDarkLaf.installLafInfo();
        FlatMacLightLaf.installLafInfo();
        
        final var icon = ImageIO.read(Objects.requireNonNull(Main.class.getResource("/minsweeper/icon.png")));
        if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE))
            Taskbar.getTaskbar().setIconImage(icon);
        
        var provider = ServiceLoader.load(Solver.class);
        for (var solver : provider) {
            solver_map.put(solver.getClass(), solver);
        }
        
        var save_path = Path.of(DIRS.dataLocalDir);
        if (!Files.isDirectory(save_path)) {
            Files.createDirectories(save_path);
        }
        
        settings_path = save_path.resolve("settings.json");
        
        try {
            loadSettings(settings_path);
        } catch (JacksonException e) {
            // i'll fix it later :p
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            settings = new Settings();
        }
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> saveSettings(settings_path)));
        
        
        frame = new JFrame();
        
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        
        var menu_bar = makeMenuBar();
        
        frame.setJMenuBar(menu_bar);
        
        changeLaf(settings.laf);
        
        game = new MinsweeperGame(ConventionalSize.BEGINNER.size, Solver.getDefault(), settings.texture);
        
        frame.add(game);
        
        changeGame();
        refreshTexture();
        
        if (settings.frame_size.orElse(null) instanceof Dimension size)
            frame.setSize(size);
        else
            frame.pack();
        
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                settings.frame_size = Optional.of(frame.getSize());
            }
        });
        
        frame.setVisible(true);
    }
    
    private static JMenuBar makeMenuBar() {
        var menu_bar = new JMenuBar();
        
        var modifier_key = KeyEvent.ALT_DOWN_MASK;
        
        if (SystemUtils.IS_OS_MAC)
            modifier_key = KeyEvent.META_DOWN_MASK;
        else if (SystemUtils.IS_OS_WINDOWS)
            modifier_key = KeyEvent.CTRL_DOWN_MASK;
        
        var size_menu = new JMenu("Size");
        size_menu.setMnemonic(KeyEvent.VK_S);
        
        var beginner_size = size_menu.add("Beginner");
        beginner_size.setMnemonic(KeyEvent.VK_B);
        beginner_size.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, modifier_key));
        beginner_size.addActionListener((_) -> {
            settings.size = ConventionalSize.BEGINNER.size;
            changeGame();
        });
        
        var intermediate_size = size_menu.add("Intermediate");
        intermediate_size.setMnemonic(KeyEvent.VK_I);
        intermediate_size.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, modifier_key));
        intermediate_size.addActionListener((_) -> {
            settings.size = ConventionalSize.INTERMEDIATE.size;
            changeGame();
        });
        
        var expert_size = size_menu.add("Expert");
        expert_size.setMnemonic(KeyEvent.VK_E);
        expert_size.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, modifier_key));
        expert_size.addActionListener((_) -> {
            settings.size = ConventionalSize.EXPERT.size;
            changeGame();
        });
        
        var custom_size = size_menu.add("Custom");
        custom_size.setMnemonic(KeyEvent.VK_C);
        custom_size.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, modifier_key));
        custom_size.addActionListener((_) -> Thread.ofVirtual().start(() -> {
            settings.size = promptBoardSize();
            changeGame();
        }));
        
        
        menu_bar.add(size_menu);
        
        var theme_menu = new JMenu("Theme");
        theme_menu.setMnemonic(KeyEvent.VK_T);
        var laf_menu = new JMenu("Look and Feel");
        var laf_button_group = new ButtonGroup();
        record LafEntry(String name, Class<? extends LookAndFeel> type) {
            
            @Override
            public boolean equals(Object o) {
                return (o instanceof LafEntry other)
                        && Objects.equals(type, other.type);
            }
            
            @Override
            public int hashCode() {
                return Objects.hashCode(type);
            }
        }
        var loader = ServiceLoader.load(LookAndFeel.class);
        Stream.concat(
                        Stream.of(UIManager.getInstalledLookAndFeels())
                                .map((e) -> {
                                    try {
                                        //noinspection unchecked
                                        return new LafEntry(e.getName(), (Class<? extends LookAndFeel>) Class.forName(e.getClassName()));
                                    } catch (ClassNotFoundException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                }),
                        loader.stream()
                                .map(ServiceLoader.Provider::get)
                                .map((e) -> new LafEntry(e.getName(), e.getClass()))
                )
                .distinct()
                .forEach((entry) -> {
                    var button = new JRadioButtonMenuItem(entry.name);
                    if (entry.type == settings.laf)
                        button.setSelected(true);
                    button.addItemListener((e) -> {
                        if (e.getStateChange() == ItemEvent.SELECTED) {
                            changeLaf(entry.type);
                        }
                    });
                    laf_button_group.add(button);
                    laf_menu.add(button);
                });
        theme_menu.add(laf_menu);

        var texture_menu = new JMenu("Texture");
        var texture_button_group = new ButtonGroup();
        for (var texture : Texture.values()) {
            var button = new JRadioButtonMenuItem(texture.readable_name);
            if (texture == settings.texture)
                button.setSelected(true);
            button.addItemListener((e) -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    settings.texture = texture;
                    refreshTexture();
                }
            });
            texture_button_group.add(button);
            texture_menu.add(button);
        }
        theme_menu.add(texture_menu);
        
        menu_bar.add(theme_menu);
        
        var solver_menu = new JMenu("Solver");
        solver_menu.setMnemonic(KeyEvent.VK_S);
        var solver_loader = ServiceLoader.load(Solver.class);
        var solver_button_group = new ButtonGroup();
        for (var solver : solver_loader) {
            var button = new JRadioButtonMenuItem(solver.getName());
            if (settings.solver == solver.getClass())
                button.setSelected(true);
            button.addItemListener((e) -> {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    Main.settings.solver = solver.getClass();
                    changeGame();
                }
            });
            solver_button_group.add(button);
            solver_menu.add(button);
        }
        
        menu_bar.add(solver_menu);
        
        
        var cheats_menu = new JMenu("Cheats");
        cheats_menu.setMnemonic(KeyEvent.VK_C);
        
        var auto_checkbox = new JCheckBoxMenuItem("Auto Mode");
        auto_checkbox.setMnemonic(KeyEvent.VK_A);
        auto_checkbox.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, modifier_key));
        auto_checkbox.setSelected(settings.auto);
        auto_checkbox.addItemListener((e) -> {
            settings.auto = (e.getStateChange() == ItemEvent.SELECTED);
            game.setAuto(settings.auto);
        });
        
        cheats_menu.add(auto_checkbox);
        
        var flag_chord_checkbox = new JCheckBoxMenuItem("Flag Chord");
        flag_chord_checkbox.setMnemonic(KeyEvent.VK_F);
        flag_chord_checkbox.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, modifier_key));
        flag_chord_checkbox.setSelected(settings.flag_chord);
        flag_chord_checkbox.addItemListener((e) -> {
            settings.flag_chord = (e.getStateChange() == ItemEvent.SELECTED);
            game.setFlagChord(settings.flag_chord);
        });
        
        cheats_menu.add(flag_chord_checkbox);
        
        var hover_chord_checkbox = new JCheckBoxMenuItem("Hover Chord");
        hover_chord_checkbox.setMnemonic(KeyEvent.VK_G);
        hover_chord_checkbox.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_G, modifier_key));
        hover_chord_checkbox.setSelected(settings.hover_chord);
        hover_chord_checkbox.addItemListener((e) -> {
            settings.hover_chord = (e.getStateChange() == ItemEvent.SELECTED);
            game.setHoverChord(settings.hover_chord);
        });
        
        cheats_menu.add(hover_chord_checkbox);
        
        var hint_button = cheats_menu.add("Hint");
        hint_button.setMnemonic(KeyEvent.VK_H);
        hint_button.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, modifier_key));
        hint_button.addActionListener((_) -> game.hint());
        
        
        menu_bar.add(cheats_menu);
        
        return menu_bar;
    }
    
    private static void changeLaf(Class<? extends LookAndFeel> type) {
        settings.laf = type;
        try {
            UIManager.setLookAndFeel(type.getName());
            SwingUtilities.updateComponentTreeUI(frame);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 UnsupportedLookAndFeelException e) {
            throw new RuntimeException(e);
        }
        saveSettings(settings_path);
    }
    
    private static void changeGame() {
        var pack = (frame.getWidth() <= frame.getPreferredSize().width && frame.getHeight() <= frame.getPreferredSize().height);
        frame.remove(game);
        game = new MinsweeperGame(settings.size, solver_map.get(settings.solver), settings.texture);
        game.setAuto(settings.auto);
        game.setFlagChord(settings.flag_chord);
        game.setHoverChord(settings.hover_chord);
        frame.add(game);
        frame.setMinimumSize(new Dimension());
        pack |= (frame.getWidth() <= frame.getPreferredSize().width && frame.getHeight() <= frame.getPreferredSize().height);
        if (pack) {
            frame.pack();
        }
        frame.setMinimumSize(game.getMinimumSize());
        frame.revalidate();
        saveSettings(settings_path);
        System.gc();
    }
    
    private static void refreshTexture() {
        game.setTheme(settings.texture);
        saveSettings(settings_path);
    }
    
    
    
    private static BoardSize promptBoardSize() {
        
        class SizeDialig {
            static final JDialog dialog;
            
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
                
                future = new CompletableFuture<>();
                
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