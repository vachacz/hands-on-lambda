package pl.vachacz.transcoder;

@FunctionalInterface
public interface LeafVisitor {

    void visit(String characters);

}

