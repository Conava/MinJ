package com.conava;

import java.util.HashMap;
import java.util.Map;
import com.conava.MinJBaseVisitor;
import com.conava.MinJParser;

public class EvalVisitor extends MinJBaseVisitor<Object> {
    private final Map<String, Object> memory = new HashMap<>();

    @Override
    public Object visitVarDecl(MinJParser.VarDeclContext ctx) {
        String name = ctx.ID().getText();
        Object value = ctx.expr() != null ? visit(ctx.expr()) : defaultValue(ctx.type().getText());
        memory.put(name, value);
        return null;
    }

    @Override
    public Object visitAssign(MinJParser.AssignContext ctx) {
        String name = ctx.ID().getText();
        Object value = visit(ctx.expr());
        memory.put(name, value);
        return null;
    }

    @Override
    public Object visitPrintStmt(MinJParser.PrintStmtContext ctx) {
        System.out.println(visit(ctx.expr()));
        return null;
    }

    @Override
    public Object visitExpr(MinJParser.ExprContext ctx) {
        if (ctx.INT() != null) {
            return Integer.parseInt(ctx.INT().getText());
        }
        if (ctx.STRING() != null) {
            String t = ctx.STRING().getText();
            return t.substring(1, t.length()-1);
        }
        return memory.get(ctx.ID().getText());
    }

    private Object defaultValue(String type) {
        return "int".equals(type) ? 0 : "";
    }
}