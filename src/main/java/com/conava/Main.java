package com.conava;

import java.nio.file.Path;
import java.nio.file.Files;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import com.conava.MinJLexer;
import com.conava.MinJParser;

/**
 * Entrypoint for the MinJ interpreter.
 * <p>
 * Reads a source file, lexes and parses it with ANTLR,
 * then applies EvalVisitor to execute the program.
 * </p>
 */
public class Main {
    private static final String USAGE = "Usage: java -jar minjc.jar <source-file.mj>";

    /**
     * Validates arguments and dispatches parsing + execution.
     *
     * @param args single argument pointing to a MinJ source file
     * @throws Exception if file I/O or parsing errors occur
     */
    public static void main(String[] args) throws Exception {
        // Ensure exactly one argument is provided
        if (args.length != 1) {
            System.err.println(USAGE);
            System.exit(1);
        }

        Path sourcePath = Path.of(args[0]);

        // Check that the file exists and is readable
        if (!Files.isReadable(sourcePath)) {
            System.err.printf("Error: cannot read file %s%n", sourcePath);
            System.exit(2);
        }

        // Perform lexing, parsing and visiting in a helper method
        parseAndExecute(sourcePath);
    }

    /**
     * Reads the file into a CharStream, generates parse tree,
     * and invokes EvalVisitor to evaluate it.
     *
     * @param sourcePath path to the MinJ source file
     * @throws Exception if file I/O or ANTLR errors occur
     */
    private static void parseAndExecute(Path sourcePath) throws Exception {
        // Read entire file into ANTLR CharStream, preserving Unicode correctly
        CharStream input = CharStreams.fromPath(sourcePath);

        // Convert characters into tokens according to grammar rules
        MinJLexer lexer = new MinJLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        // Build a parse tree starting at the 'program' rule
        MinJParser parser = new MinJParser(tokens);
        ParseTree tree = parser.program();

        // Walk the parse tree to evaluate statements and expressions
        EvalVisitor visitor = new EvalVisitor();
        visitor.visit(tree);
    }
}