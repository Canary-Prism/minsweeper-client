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

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Keybind(int modifiers) {
    
//    public boolean matches(ActionEvent e) {
//        return switch (button) {
//            case LEFT -> (object instanceof ActionEvent e) && ((e.getModifiers() == modifiers));
//            case RIGHT -> (object instanceof MouseEvent e) && SwingUtilities.isRightMouseButton(e) && ((e.getModifiersEx() == modifiers));
//        };
//    }
    
    public boolean pressed(MouseEvent e) {
        return e.getModifiersEx() == modifiers;
    }
    public boolean matches(MouseEvent e) {
        var mask = e.getModifiersEx();
        if (e.getButton() == MouseEvent.BUTTON1)
            mask |= KeyEvent.BUTTON1_DOWN_MASK;
        if (e.getButton() == MouseEvent.BUTTON2)
            mask |= KeyEvent.BUTTON2_DOWN_MASK;
        if (e.getButton() == MouseEvent.BUTTON3)
            mask |= KeyEvent.BUTTON3_DOWN_MASK;
        return mask == modifiers;
    }
    
    @SuppressWarnings("deprecation")
    private static final List<Integer> ALL_MODIFIERS = List.of(
            KeyEvent.SHIFT_MASK,
            KeyEvent.CTRL_MASK,
            KeyEvent.META_MASK,
            KeyEvent.ALT_MASK,
            KeyEvent.ALT_GRAPH_MASK
    );
    private static final List<Integer> ALL_MODIFIERS_EX = List.of(
            KeyEvent.SHIFT_DOWN_MASK,
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.META_DOWN_MASK,
            KeyEvent.ALT_DOWN_MASK,
            KeyEvent.ALT_GRAPH_DOWN_MASK,
            KeyEvent.BUTTON1_DOWN_MASK,
            KeyEvent.BUTTON2_DOWN_MASK,
            KeyEvent.BUTTON3_DOWN_MASK
    );
    @SuppressWarnings({"NullableProblems", "deprecation"})
    @Override
    public String toString() {
        return Stream.concat(
                ALL_MODIFIERS_EX.stream()
                        .filter((e) -> (modifiers & e) == e)
                        .map(KeyEvent::getModifiersExText),
                ALL_MODIFIERS.stream()
                        .filter((e) -> (modifiers & e) == e)
                        .map(KeyEvent::getKeyModifiersText)
        ).collect(Collectors.joining(" "));
    }
}
