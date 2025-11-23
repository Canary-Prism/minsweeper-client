module canaryprism.minsweeperclient {
    uses canaryprism.minsweeper.solver.Solver;
    uses javax.swing.LookAndFeel;
    
    requires java.desktop;
    requires java.net.http;
    requires canaryprism.minsweeper;
    requires org.apache.xmlgraphics.batik.transcoder;
    requires org.apache.commons.lang3;
    requires com.formdev.flatlaf;
    requires dev.dirs;
    requires tools.jackson.databind;
    
    opens canaryprism.minsweeperclient to
            tools.jackson.databind;
    opens canaryprism.minsweeperclient.swing to
            tools.jackson.databind;
}