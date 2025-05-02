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

        // If it’s a val, make sure they provided an initializer
        if (isVal && ctx.expr() == null) {
            throw new IllegalArgumentException("val " + name + " requires an initializer");
        }

        // If they wrote “= expr” then visit that one expr; otherwise give a default
        Object value = (ctx.expr() != null)
                ? visit(ctx.expr())
                : defaultValue(ctx.type().getText());

        if (isVal) immutable.add(name);
        env.put(name, value);
        return null;
    }

    @Override
    public Object visitListLiteral(MinJParser.ListLiteralContext ctx) {
        List<Object> list = new ArrayList<>();
        // ctx.expr() here is the list of elements inside [ ... ]
        for (MinJParser.ExprContext e : ctx.expr()) {
            list.add(visit(e));
        }
        return list;
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
                case "%"  -> {
                    if (left instanceof Integer && right instanceof Integer) {
                        yield (Integer) left % (Integer) right;
                    }
                    yield ((Number) left).doubleValue() % ((Number) right).doubleValue();
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
        // 1) Primitive literals
        if (ctx.INT() != null) return Integer.parseInt(ctx.INT().getText());
        if (ctx.FLOAT_LIT() != null) {
            String t = ctx.FLOAT_LIT().getText();
            return Float.parseFloat(t.substring(0, t.length() - 1));
        }
        if (ctx.DOUBLE_LIT() != null) return Double.parseDouble(ctx.DOUBLE_LIT().getText());
        if (ctx.BOOL_LIT() != null) return Boolean.parseBoolean(ctx.BOOL_LIT().getText());
        if (ctx.CHAR() != null) return unquoteChar(ctx.CHAR());
        if (ctx.STRING() != null) return unquoteString(ctx.STRING());

        // 2) Variable lookup
        if (ctx.ID() != null) {
            String name = ctx.ID().getText();
            if (!env.containsKey(name)) {
                throw new IllegalStateException("Undefined variable: " + name);
            }
            return env.get(name);
        }

        // 3) List literal ([...])
        if (ctx.listLiteral() != null) {
            return visitListLiteral(ctx.listLiteral());
        }

        // 4) Parenthesized expression
        if (ctx.LPAREN() != null) {
            // primary → LPAREN expr RPAREN
            return visit(ctx.expr());
        }

        // Should never happen
        throw new IllegalStateException("Unknown primary: " + ctx.getText());
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
        String loopVar = ctx.ID().getText();

        // Instead of visit(ctx.expr()), grab the raw text of the `in`-expression:
        String collectionName = ctx.expr().getText();
        Object coll = env.get(collectionName);

        if (!(coll instanceof List<?>)) {
            throw new IllegalArgumentException("Cannot iterate over " + coll);
        }

        for (Object item : (List<?>) coll) {
            if (immutable.contains(loopVar)) {
                throw new IllegalStateException("Cannot reassign val " + loopVar);
            }
            env.put(loopVar, item);
            // visit the block under the `do:`
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