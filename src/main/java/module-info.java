module canaryprism.minsweeperclient {
    uses canaryprism.minsweeper.solver.Solver;
    requires java.desktop;
    requires java.net.http;
    requires canaryprism.minsweeper;
    requires org.apache.xmlgraphics.batik.transcoder;
}