package com.conava;

import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.tree.TerminalNode;
import com.conava.MinJBaseVisitor;
import com.conava.MinJParser;

/**
 * Visitor that evaluates a MinJ parse tree by executing declarations, assignments,
 * and print statements. Maintains a simple environment mapping variable names to values.
 */
public class EvalVisitor extends MinJBaseVisitor<Object> {
    private final Map<String, Object> env = new HashMap<>();

    /**
     * Handles variable declaration: assigns default or initialized value.
     */
    @Override
    public Object visitVarDecl(MinJParser.VarDeclContext ctx) {
        String name = ctx.ID().getText();
        Object value = (ctx.expr() != null)
                ? visit(ctx.expr())
                : defaultValue(ctx.type().getText());
        store(name, value);
        return null;
    }

    /**
     * Handles assignment to an existing or new variable.
     */
    @Override
    public Object visitAssign(MinJParser.AssignContext ctx) {
        String name = ctx.ID().getText();
        Object value = visit(ctx.expr());
        store(name, value);
        return null;
    }

    /**
     * Evaluates the expression and prints its result to standard output.
     */
    @Override
    public Object visitPrintStmt(MinJParser.PrintStmtContext ctx) {
        Object result = visit(ctx.expr());
        System.out.println(result);
        return null;
    }

    /**
     * Evaluates an expression: integer literal, string literal, or variable lookup.
     */
    @Override
    public Object visitExpr(MinJParser.ExprContext ctx) {
        if (ctx.INT() != null) {
            return Integer.parseInt(ctx.INT().getText());
        }
        if (ctx.STRING() != null) {
            return unquote(ctx.STRING());
        }
        // Variable reference
        String name = ctx.ID().getText();
        if (!env.containsKey(name)) {
            throw new IllegalStateException("Undefined variable: " + name);
        }
        return env.get(name);
    }

    /**
     * Stores a value in the environment.
     */
    private void store(String name, Object value) {
        env.put(name, value);
    }

    /**
     * Returns the default value for a type: 0 for int, empty string otherwise.
     */
    private Object defaultValue(String typeName) {
        return "int".equals(typeName) ? 0 : "";
    }

    /**
     * Removes surrounding quotes from a string token.
     */
    private String unquote(TerminalNode stringToken) {
        String text = stringToken.getText();
        return text.substring(1, text.length() - 1);
    }
}