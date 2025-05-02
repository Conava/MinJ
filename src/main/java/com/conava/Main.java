package com.conava;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import com.conava.MinJLexer;
import com.conava.MinJParser;

public class Main {
    public static void main(String[] args) throws Exception {
        CharStream in = CharStreams.fromPath(java.nio.file.Path.of(args[0]));
        MinJLexer lexer = new MinJLexer(in);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MinJParser parser = new MinJParser(tokens);
        ParseTree tree = parser.program();
        EvalVisitor visitor = new EvalVisitor();
        visitor.visit(tree);
    }
}
