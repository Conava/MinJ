package com.conava;

import java.util.*;

import org.antlr.v4.runtime.tree.TerminalNode;
import com.conava.MinJBaseVisitor;
import com.conava.MinJParser;

public class EvalVisitor extends MinJBaseVisitor<Object> {
    private final Map<String, Object> env = new HashMap<>();
    private final Set<String> immutable = new HashSet<>();
    private final Map<String, MinJParser.MethodDeclContext> methodDefs = new HashMap<>();

    // === visibility tables ===
    private final Map<String, Object> globals = new HashMap<>();                      // public vars
    private final Map<String, MinJParser.MethodDeclContext> globalMethods = new HashMap<>(); // public funcs

    static class ClassDef {
        final Map<String, Object> fields = new HashMap<>();  // private
        final Map<String, MinJParser.MethodDeclContext> methods = new HashMap<>(); // private
    }

    private final Map<String, ClassDef> classTable = new HashMap<>();

    private boolean inClass = false;
    private boolean inMethod = false;
    private ClassDef currentClass;


    @Override
    public Object visitProgram(MinJParser.ProgramContext ctx) {
        // ctx.topLevelDecl() is a List<TopLevelDeclContext>
        for (MinJParser.TopLevelDeclContext tld : ctx.topLevelDecl()) {
            visit(tld);
        }
        return null;
    }

    @Override
    public Object visitTopLevelDecl(MinJParser.TopLevelDeclContext ctx) {
        if (ctx.statement() != null) {
            return visit(ctx.statement());
        } else if (ctx.classDecl() != null) {
            return visit(ctx.classDecl());
        } else if (ctx.methodDecl() != null) {
            return visit(ctx.methodDecl());
        }
        return null;
    }

    @Override
    public Object visitClassDecl(MinJParser.ClassDeclContext ctx) {
        String className = ctx.ID().getText();
        ClassDef def = new ClassDef();
        classTable.put(className, def);

        for (var child : ctx.classBody().children) {
            if (child instanceof MinJParser.FieldDeclContext f) {
                String name = f.varDecl().ID().getText();
                def.fields.put(name, /* default null for now */ null);
            } else if (child instanceof MinJParser.MethodDeclContext m) {
                String name = m.ID().getText();
                def.methods.put(name, m);
            } else if (child instanceof MinJParser.StatementContext s) {
                visit(s);
            }
        }
        return null;
    }


    @Override
    public Object visitMethodDecl(MinJParser.MethodDeclContext ctx) {
        String name = ctx.ID().getText();

        if (!inClass) {                       // public global method
            globalMethods.put(name, ctx);
        } else {
            currentClass.methods.put(name, ctx); // private to class
        }
        return null;
    }


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

        if (!inClass && !inMethod) {          // global scope
            globals.put(name, value);
        } else if (inClass && !inMethod) {     // a field in class body
            currentClass.fields.put(name, value);
        } else {                               // local inside a method
            env.put(name, value);
        }

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
                case "%" -> {
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

    // === Evaluate “new” expressions ===
    @Override
    public Object visitNewExpr(MinJParser.NewExprContext ctx) {
        String className = ctx.ID().getText();
        ClassDef def = classTable.get(className);
        if (def == null) throw new IllegalStateException("Unknown class: " + className);
        return new Obj(def);
    }

    // === Literals ===
    @Override
    public Object visitIntLiteral(MinJParser.IntLiteralContext ctx) {
        return Integer.parseInt(ctx.INT().getText());
    }

    @Override
    public Object visitFloatLiteral(MinJParser.FloatLiteralContext ctx) {
        String text = ctx.FLOAT_LIT().getText();
        // drop the trailing f/F
        return Float.parseFloat(text.substring(0, text.length() - 1));
    }

    @Override
    public Object visitDoubleLiteral(MinJParser.DoubleLiteralContext ctx) {
        return Double.parseDouble(ctx.DOUBLE_LIT().getText());
    }

    @Override
    public Object visitStringLiteral(MinJParser.StringLiteralContext ctx) {
        return unquoteString(ctx.STRING());
    }

    @Override
    public Object visitCharLiteral(MinJParser.CharLiteralContext ctx) {
        return unquoteChar(ctx.CHAR());
    }

    @Override
    public Object visitBoolLiteral(MinJParser.BoolLiteralContext ctx) {
        return Boolean.parseBoolean(ctx.BOOL_LIT().getText());
    }

    // === Variable reference ===
    @Override
    public Object visitVarReference(MinJParser.VarReferenceContext ctx) {
        return resolve(ctx.ID().getText());
    }

    @Override
    public Object visitCallExpr(MinJParser.CallExprContext ctx) {
        String name = ctx.ID().getText();
        MinJParser.MethodDeclContext decl = globalMethods.get(name);
        if (decl == null) {
            throw new IllegalStateException("Unknown function: " + name);
        }

        // evaluate arguments…
        List<Object> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (var e : ctx.argList().expr()) {
                args.add(visit(e));
            }
        }

        // **Pass name, decl, args** in the right order**
        return invokeMethod(name, decl, args);
    }

    // 1) Global function calls:     callStmt → callExpr  #GlobalCall
    @Override
    public Object visitGlobalCall(MinJParser.GlobalCallContext ctx) {
        // delegate to your existing visitCallExpr logic
        return visitCallExpr(ctx.callExpr());
    }

    @Override
    public Object visitInstanceCall(MinJParser.InstanceCallContext ctx) {
        // receiver is the primary
        Obj receiver = (Obj) visit(ctx.primary());

        // unpack the sub‐callExpr
        MinJParser.CallExprContext call = ctx.callExpr();

        // method name and args live there:
        String methodName = call.ID().getText();            // or .ID(0).getText() if there's ambiguity
        MinJParser.MethodDeclContext mdecl = receiver.def.methods.get(methodName);
        if (mdecl == null) throw new IllegalStateException("No method " + methodName);

        // collect arguments
        List<Object> args = new ArrayList<>();
        if (call.argList() != null) {
            for (var e : call.argList().expr()) {
                args.add(visit(e));
            }
        }

        return invokeMethod(methodName, mdecl, args, receiver);
    }


    // === Method calls on an object: primary DOT callExpr ===
    @Override
    public Object visitDotCallExpr(MinJParser.DotCallExprContext ctx) {
        // 1) Evaluate the receiver object
        Obj receiver = (Obj) visit(ctx.primary());

        // 2) Get the method name from the ID in this alternative
        String methodName = ctx.ID().getText();
        MinJParser.MethodDeclContext mdecl = receiver.def.methods.get(methodName);
        if (mdecl == null) {
            throw new IllegalStateException("No method " + methodName + " on " + receiver.def);
        }

        // 3) Collect arguments from ctx.argList()
        List<Object> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (MinJParser.ExprContext e : ctx.argList().expr()) {
                args.add(visit(e));
            }
        }

        // 4) Invoke the method with 'this' bound to the instance
        Map<String, Object> oldEnv = new HashMap<>(env);
        boolean oldInMethod = inMethod;
        inMethod = true;

        env.clear();
        env.put("this", receiver);

        // bind parameters
        if (mdecl.paramList() != null) {
            List<String> params = mdecl.paramList().ID().stream()
                    .map(TerminalNode::getText)
                    .toList();
            for (int i = 0; i < params.size(); i++) {
                env.put(params.get(i), args.get(i));
            }
        }

        // execute the method body
        visitBlock(mdecl.block());

        // 5) Restore the previous environment
        env.clear();
        env.putAll(oldEnv);
        inMethod = oldInMethod;

        return null;
    }


    // === Parentheses & lists ===
    @Override
    public Object visitParenExpr(MinJParser.ParenExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public Object visitListExpr(MinJParser.ListExprContext ctx) {
        return visitListLiteral(ctx.listLiteral());
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
        // 1. Initialize the loop variable (either varDecl or assign)
        String varName;
        if (ctx.varDecl() != null) {
            MinJParser.VarDeclContext vdc = ctx.varDecl();
            varName = vdc.ID().getText();
            visit(vdc);
        } else {
            MinJParser.AssignContext init = ctx.assign().get(0);
            varName = init.ID().getText();
            visit(init);
        }

        // 2. Evaluate the loop upper bound
        Number upper = (Number) visit(ctx.expr());

        // 3. Determine if the user supplied an explicit STEP assignment
        MinJParser.AssignContext stepCtx = null;
        if (ctx.assign().size() > 1) {
            // if we used assign(0) for init, then step is assign(1)
            stepCtx = ctx.assign().get(1);
        } else if (ctx.assign().size() == 1 && ctx.varDecl() != null) {
            // init was varDecl, so the sole assign is the STEP
            stepCtx = ctx.assign().getFirst();
        }
        boolean hasStep = stepCtx != null;

        // 4. Loop
        while (true) {
            Number current = (Number) env.get(varName);
            if (current.doubleValue() > upper.doubleValue()) {
                break;
            }

            // body
            visitBlock(ctx.block());

            // advance: either user step or default +1
            if (hasStep) {
                visit(stepCtx);
            } else {
                // default increment by 1
                if (current instanceof Integer) {
                    env.put(varName, current.intValue() + 1);
                } else {
                    env.put(varName, current.doubleValue() + 1.0);
                }
            }
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

    static class Obj {
        final ClassDef def;
        final Map<String, Object> fields = new HashMap<>();

        Obj(ClassDef d) {
            def = d;
            fields.putAll(d.fields);
        }
    }


    private Object resolve(String name) {
        if (env.containsKey(name)) return env.get(name);          // local
        if (inMethod && env.containsKey("this")) {   // inside a method → check fields
            Obj thiz = (Obj) env.get("this");
            if (thiz.fields.containsKey(name)) return thiz.fields.get(name);
        }
        if (globals.containsKey(name)) return globals.get(name);      // public global
        throw new IllegalStateException("Undefined variable: " + name);
    }


    private String unquoteString(TerminalNode node) {
        String t = node.getText();
        return t.substring(1, t.length() - 1);
    }

    private Character unquoteChar(TerminalNode node) {
        return node.getText().charAt(1);
    }

    /**
     * Invoke either a global or instance method.
     *
     * @param name     method name
     * @param decl     its MethodDeclContext
     * @param args     evaluated arguments
     * @param receiver the Obj on which to call it, or null for a global method
     */
    private Object invokeMethod(String name,
                                MinJParser.MethodDeclContext decl,
                                List<Object> args,
                                Obj receiver) {
        // save/clear old state
        Map<String, Object> oldEnv = new HashMap<>(env);
        Set<String> oldImm = new HashSet<>(immutable);
        boolean wasInMethod = inMethod;

        env.clear();
        immutable.clear();
        inMethod = true;

        // bind 'this' for instance, skip for globals
        if (receiver != null) {
            env.put("this", receiver);
        }

        // bind parameters
        if (decl.paramList() != null) {
            List<String> params = decl.paramList().ID().stream()
                    .map(TerminalNode::getText)
                    .toList();
            for (int i = 0; i < params.size(); i++) {
                env.put(params.get(i), args.get(i));
            }
        }

        // execute the method body
        visitBlock(decl.block());

        // restore
        env.clear();
        env.putAll(oldEnv);
        immutable.clear();
        immutable.addAll(oldImm);
        inMethod = wasInMethod;

        return null;
    }

    private Object invokeMethod(String name,
                                MinJParser.MethodDeclContext decl,
                                List<Object> args) {
        return invokeMethod(name, decl, args, null);
    }

}