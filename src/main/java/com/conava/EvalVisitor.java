package com.conava;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.tree.TerminalNode;
import com.conava.MinJBaseVisitor;
import com.conava.MinJParser;

/**
 * Visitor that evaluates a MinJ parse tree by executing declarations, assignments,
 * and print statements. Supports typed literals and var/val semantics.
 */
public class EvalVisitor extends MinJBaseVisitor<Object> {
    private final Map<String, Object> env = new HashMap<>();
    private final Set<String> immutable = new HashSet<>();

    /**
     * Handle `type|var|val ID (= expr)?`
     */
    @Override
    public Object visitVarDecl(MinJParser.VarDeclContext ctx) {
        String name = ctx.ID().getText();
        boolean isVal = ctx.VAL() != null;
        if (isVal && ctx.expr() == null) {
            throw new IllegalArgumentException("val " + name + " requires an initializer");
        }

        Object value;
        if (ctx.expr() != null) {
            value = visit(ctx.expr());
        } else {
            String typeName = ctx.type().getText();
            value = defaultValue(typeName);
        }

        if (isVal) {
            immutable.add(name);
        }
        store(name, value);
        return null;
    }

    /**
     * Prevent reassigning a `val`.
     */
    @Override
    public Object visitAssign(MinJParser.AssignContext ctx) {
        String name = ctx.ID().getText();
        if (immutable.contains(name)) {
            throw new IllegalStateException("Cannot reassign val " + name);
        }
        Object value = visit(ctx.expr());
        store(name, value);
        return null;
    }

    /**
     * Print the evaluated expression.
     */
    @Override
    public Object visitPrintStmt(MinJParser.PrintStmtContext ctx) {
        System.out.println(visit(ctx.expr()));
        return null;
    }

    /**
     * Handle all literal forms and variable lookup.
     */
    @Override
    public Object visitExpr(MinJParser.ExprContext ctx) {
        if (ctx.INT() != null) {
            return Integer.parseInt(ctx.INT().getText());
        }
        if (ctx.FLOAT_LIT() != null) {
            String txt = ctx.FLOAT_LIT().getText();
            return Float.parseFloat(txt.substring(0, txt.length() - 1));
        }
        if (ctx.DOUBLE_LIT() != null) {
            return Double.parseDouble(ctx.DOUBLE_LIT().getText());
        }
        if (ctx.BOOL_LIT() != null) {
            return Boolean.parseBoolean(ctx.BOOL_LIT().getText());
        }
        if (ctx.CHAR() != null) {
            return unquoteChar(ctx.CHAR());
        }
        if (ctx.STRING() != null) {
            return unquoteString(ctx.STRING());
        }
        // Variable reference
        String name = ctx.ID().getText();
        if (!env.containsKey(name)) {
            throw new IllegalStateException("Undefined variable: " + name);
        }
        return env.get(name);
    }

    private void store(String name, Object value) {
        env.put(name, value);
    }

    /**
     * Default values per type name.
     */
    private Object defaultValue(String typeName) {
        return switch (typeName) {
            case "int" -> 0;
            case "float" -> 0.0f;
            case "double" -> 0.0;
            case "boolean" -> false;
            case "char" -> '\0';
            default -> "";
        };
    }

    private String unquoteString(TerminalNode node) {
        String t = node.getText();
        return t.substring(1, t.length() - 1);
    }

    private Character unquoteChar(TerminalNode node) {
        String t = node.getText(); // e.g. `'a'`
        return t.charAt(1);
    }
}