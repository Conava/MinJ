package com.conava;

import java.util.*;

import org.antlr.v4.runtime.tree.TerminalNode;
import com.conava.MinJBaseVisitor;
import com.conava.MinJParser;

public class EvalVisitor extends MinJBaseVisitor<Object> {
    private final Map<String, Object> env = new HashMap<>();
    private final Set<String> immutable = new HashSet<>();

    @Override
    public Object visitVarDecl(MinJParser.VarDeclContext ctx) {
        String name = ctx.ID().getText();
        boolean isVal = ctx.VAL() != null;
        if (isVal && ctx.expr() == null) {
            throw new IllegalArgumentException("val " + name + " requires an initializer");
        }
        Object value = ctx.expr() != null ? visit(ctx.expr()) : defaultValue(ctx.type().getText());
        if (isVal) immutable.add(name);
        env.put(name, value);
        return null;
    }

    @Override
    public Object visitAssign(MinJParser.AssignContext ctx) {
        String name = ctx.ID().getText();
        if (immutable.contains(name)) {
            throw new IllegalStateException("Cannot reassign val " + name);
        }
        Object value = visit(ctx.expr());
        env.put(name, value);
        return null;
    }

    @Override
    public Object visitPrintStmt(MinJParser.PrintStmtContext ctx) {
        System.out.println(visit(ctx.expr()));
        return null;
    }

    @Override
    public Object visitIfStmt(MinJParser.IfStmtContext ctx) {
        if (Boolean.TRUE.equals(visit(ctx.expr(0)))) {
            return visitBlock(ctx.block(0));
        }
        for (int i = 1; i < ctx.expr().size(); i++) {
            if (Boolean.TRUE.equals(visit(ctx.expr(i)))) {
                return visitBlock(ctx.block(i));
            }
        }
        if (ctx.block().size() > ctx.expr().size()) {
            return visitBlock(ctx.block(ctx.block().size() - 1));
        }
        return null;
    }

    public Object visitBlock(MinJParser.BlockContext ctx) {
        for (MinJParser.StatementContext s : ctx.statement()) {
            visit(s);
        }
        return null;
    }

    @Override
    public Object visitExpr(MinJParser.ExprContext ctx) {
        if (ctx.op != null) {
            Object left = visit(ctx.expr(0));
            Object right = visit(ctx.expr(1));
            return switch (ctx.op.getText()) {
                case "+" -> {
                    if (left instanceof Integer && right instanceof Integer) {
                        yield (Integer) left + (Integer) right;
                    }
                    yield ((Number) left).doubleValue() + ((Number) right).doubleValue();
                }
                case "-" -> {
                    if (left instanceof Integer && right instanceof Integer) {
                        yield (Integer) left - (Integer) right;
                    }
                    yield ((Number) left).doubleValue() - ((Number) right).doubleValue();
                }
                case "*" -> {
                    if (left instanceof Integer && right instanceof Integer) {
                        yield (Integer) left * (Integer) right;
                    }
                    yield ((Number) left).doubleValue() * ((Number) right).doubleValue();
                }
                case "/" -> ((Number) left).doubleValue() / ((Number) right).doubleValue();
                case "<" -> ((Number) left).doubleValue() < ((Number) right).doubleValue();
                case ">" -> ((Number) left).doubleValue() > ((Number) right).doubleValue();
                case "<=" -> ((Number) left).doubleValue() <= ((Number) right).doubleValue();
                case ">=" -> ((Number) left).doubleValue() >= ((Number) right).doubleValue();
                case "==" -> left.equals(right);
                case "!=" -> !left.equals(right);
                default -> null;
            };
        }
        return visit(ctx.primary());
    }

    @Override
    public Object visitPrimary(MinJParser.PrimaryContext ctx) {
        if (ctx.INT() != null) return Integer.parseInt(ctx.INT().getText());
        if (ctx.FLOAT_LIT() != null) {
            String t = ctx.FLOAT_LIT().getText();
            return Float.parseFloat(t.substring(0, t.length() - 1));
        }
        if (ctx.DOUBLE_LIT() != null) return Double.parseDouble(ctx.DOUBLE_LIT().getText());
        if (ctx.BOOL_LIT() != null) return Boolean.parseBoolean(ctx.BOOL_LIT().getText());
        if (ctx.CHAR() != null) return unquoteChar(ctx.CHAR());
        if (ctx.STRING() != null) return unquoteString(ctx.STRING());
        if (ctx.ID() != null) {
            String name = ctx.ID().getText();
            if (!env.containsKey(name)) {
                throw new IllegalStateException("Undefined variable: " + name);
            }
            return env.get(name);
        }
        // parenthesized expression
        return visit(ctx.expr());
    }

    @Override
    public Object visitListLiteral(MinJParser.ListLiteralContext ctx) {
        List<Object> list = new ArrayList<>();
        for (MinJParser.ExprContext e : ctx.expr()) {
            list.add(visit(e));
        }
        return list;
    }

    @Override
    public Object visitWhileStmt(MinJParser.WhileStmtContext ctx) {
        // evaluate condition and repeat only the block
        while ((boolean) visit(ctx.expr())) {
            visit(ctx.block());
        }
        return null;
    }

    @Override
    public Object visitForStmt(MinJParser.ForStmtContext ctx) {
        // 1. init variable
        String varName;
        if (ctx.varDecl() != null) {
            MinJParser.VarDeclContext vdc = ctx.varDecl();
            varName = vdc.ID().getText();
            visit(vdc);
        } else {
            MinJParser.AssignContext init = ctx.assign(0);
            varName = init.ID().getText();
            visit(init);
        }

        // 2. evaluate upper bound
        Number upper = (Number) visit(ctx.expr());

        // 3. identify step assignment (always last)
        MinJParser.AssignContext step = ctx.assign().get(ctx.assign().size() - 1);

        // 4. loop while var ≤ bound, then body + step
        while (true) {
            Number current = (Number) env.get(varName);  // <-- use env instead of memory
            boolean cont = (current.doubleValue() <= upper.doubleValue());
            if (!cont) break;
            visitBlock(ctx.block());
            visit(step);
        }
        return null;
    }

    @Override
    public Object visitForeachStmt(MinJParser.ForeachStmtContext ctx) {
        String varName = ctx.ID().getText();
        Object coll = visit(ctx.expr());
        if (!(coll instanceof List<?>)) {
            throw new IllegalArgumentException("Cannot iterate over " + coll);
        }
        List<?> list = (List<?>) coll;
        for (Object item : list) {
            if (immutable.contains(varName)) {
                throw new IllegalStateException("Cannot reassign val " + varName);
            }
            env.put(varName, item);
            visit(ctx.block());
        }
        return null;
    }

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
        return node.getText().charAt(1);
    }
}