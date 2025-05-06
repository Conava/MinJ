package com.conava;

import java.util.*;

import org.antlr.v4.runtime.tree.TerminalNode;
import com.conava.MinJBaseVisitor;
import com.conava.MinJParser;

public class EvalVisitor extends MinJBaseVisitor<Object> {
    private Map<String, Cell> env = new HashMap<>();
    private Set<String> immutable = new HashSet<>();
    private final Map<String, MinJParser.MethodDeclContext> methodDefs = new HashMap<>();

    // === visibility tables ===
    private final Map<String, Cell> globals = new HashMap<>();
    private final Map<String, MinJParser.MethodDeclContext> globalMethods = new HashMap<>(); // public funcs


    private final Map<String, ClassDef> classTable = new HashMap<>();

    private void bindIds(List<TerminalNode> ids,
                         Object rhs,
                         boolean isReassign,
                         boolean isDynamic,
                         boolean mutable) {

        List<?> vals = ids.size() == 1 ? List.of(rhs) : (List<?>) rhs;

        if (vals.size() != ids.size())
            throw new IllegalStateException(
                    "Expected " + ids.size() + " values, got " + vals.size());

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i).getText();
            Object v = vals.get(i);
            Class<?> t = v.getClass();

            if (isReassign) {
                Cell old = resolveCell(id);
                if (!old.mutable)
                    throw new IllegalStateException("Cannot reassign val " + id);
                if (!old.dynamic && !old.declaredType.isAssignableFrom(t))
                    throw new IllegalStateException("Type mismatch for " + id +
                            ": " + old.declaredType.getSimpleName() + " vs " + t.getSimpleName());
                env.put(id, cellOf(v,
                        old.dynamic ? t : old.declaredType,
                        true,
                        old.dynamic));
            } else { // first declaration
                env.put(id, cellOf(v, t, mutable, isDynamic));
                if (!mutable) immutable.add(id);
            }
        }
    }


    private static Class<?> tokenToClass(String tok) {
        return switch (tok) {
            case "int" -> Integer.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "boolean" -> Boolean.class;
            case "char" -> Character.class;
            case "String" -> String.class;
            default -> Object.class;
        };
    }


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
        String cn = ctx.ID().getText();
        ClassDef def = new ClassDef();
        classTable.put(cn, def);

        inClass = true;
        currentClass = def;
        for (var child : ctx.classBody().children) {
            if (child instanceof MinJParser.FieldDeclContext f) {
                String name = f.varDecl().idList().ID(0).getText();
                def.fields.put(name, cellOf(null, Object.class, true, true));
            } else if (child instanceof MinJParser.MethodDeclContext m) {
                def.methods.put(m.ID().getText(), m);
            } else if (child instanceof MinJParser.StatementContext s) {
                visit(s); // static block
            }
        }
        inClass = false;
        currentClass = null;
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
        List<TerminalNode> ids = ctx.idList().ID();
        boolean isDynamic = ctx.VAR() != null;
        boolean mutable = ctx.VAL() == null;

        Object rhs;
        Class<?> baseType;

        if (ctx.type() != null) {                     // explicit type
            baseType = tokenToClass(ctx.type().getText());
            rhs = ctx.ASSIGN() != null ? visit(ctx.expr())
                    : defaultValue(baseType);
        } else {                                     // infer from expr
            rhs = visit(ctx.expr());
            baseType = rhs.getClass();
        }

        bindIds(ids, rhs, /*reassign=*/false, isDynamic, mutable);
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
        bindIds(ctx.idList().ID(),
                visit(ctx.expr()),
                /*reassign=*/true,
                /*dynamic ignored*/false,
                /*mutable*/true);
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
                    if (left instanceof String || right instanceof String ||
                            left instanceof Character || right instanceof Character) {
                        yield String.valueOf(left) + String.valueOf(right);
                    }

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
                case "/" -> {
                    // pure-integer division keeps int semantics
                    if (left instanceof Integer && right instanceof Integer) {
                        yield (Integer) left / (Integer) right;     // 23 / 5  ➜ 4
                    }
                    // anything else → floating-point
                    yield ((Number) left).doubleValue() / ((Number) right).doubleValue();
                }

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

    // === Primary-level function call  ID '(' argList? ')'  ===
    @Override
    public Object visitCallExprPrimary(MinJParser.CallExprPrimaryContext ctx) {
        // exactly the same handling as in visitCallExpr
        String name = ctx.ID().getText();
        MinJParser.MethodDeclContext decl = globalMethods.get(name);
        if (decl == null) {
            throw new IllegalStateException("Unknown function: " + name);
        }

        List<Object> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (MinJParser.ExprContext e : ctx.argList().expr()) {
                args.add(visit(e));
            }
        }
        return invokeMethod(name, decl, args);   // returns List or single value
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

    @Override
    public Object visitReturnStmt(MinJParser.ReturnStmtContext ctx) {
        List<Object> vals = new ArrayList<>();
        if (ctx.exprList() != null) {
            for (MinJParser.ExprContext e : ctx.exprList().expr()) {
                vals.add(visit(e));
            }
        }
        // no value → empty list = “void”
        throw new ReturnSignal(vals);
    }


    /**
     * primary '.' ID '(' argList? ')'
     */
    @Override
    public Object visitDotCallExpr(MinJParser.DotCallExprContext ctx) {
        /* 1. receiver object */
        Obj receiver = (Obj) visit(ctx.primary());

        /* 2. look-up method inside the receiver’s class */
        String methodName = ctx.ID().getText();
        MinJParser.MethodDeclContext mdecl =
                receiver.def.methods.get(methodName);
        if (mdecl == null)
            throw new IllegalStateException(
                    "No method " + methodName + " on that object");

        /* 3. evaluate arguments */
        List<Object> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (MinJParser.ExprContext e : ctx.argList().expr()) {
                args.add(visit(e));
            }
        }

    /* 4. delegate to the central helper.
           It returns the single value, the list of values,
           or null if the method returned nothing.            */
        return invokeMethod(methodName, mdecl, args, receiver);
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

    /* ────────────────────────────────────────────────
       for i = … to upper [step …] do: … end
       ──────────────────────────────────────────────── */
    @Override
    public Object visitForStmt(MinJParser.ForStmtContext ctx) {
        // ─── 1 · initialise the loop variable ───────────────────
        Cell cell;
        String varName;

        if (ctx.varDecl() != null) {
            // user wrote: for var i = …
            MinJParser.VarDeclContext vdc = ctx.varDecl();
            varName = vdc.idList().ID(0).getText();
            visit(vdc);  // this will call bindIds(ids, rhs, false, false, true)
            cell = resolveCell(varName);
        } else {
            // user wrote: for i = …
            MinJParser.AssignContext init = ctx.assign().get(0);
            varName = init.idList().ID(0).getText();

            List<TerminalNode> ids = init.idList().ID();
            Object initVal = visit(init.expr());

            // implicit declarations are always dynamic (no static type) and mutable
            bindIds(
                    ids,
                    initVal,
                    /* isReassign= */ false,
                    /* isDynamic=   */ true,
                    /* mutable=     */ true
            );
            cell = resolveCell(varName);
        }

        // ─── 2 · upper bound ────────────────────────────────────
        Number upper = (Number) visit(ctx.expr());

        // ─── 3 · optional STEP ──────────────────────────────────
        MinJParser.AssignContext stepCtx = null;
        if (ctx.assign().size() > 1) stepCtx = ctx.assign().get(1);
        else if (ctx.assign().size() == 1
                && ctx.varDecl() != null) stepCtx = ctx.assign().get(0);

        // ─── 4 · loop body ─────────────────────────────────────
        while (true) {
            Number cur = (Number) cell.value;
            if (cur.doubleValue() > upper.doubleValue()) break;

            visitBlock(ctx.block());

            if (stepCtx != null) {
                visit(stepCtx);  // this will call bindIds(ids, rhs, true, …)
            } else {
                // default step of +1
                if (cur instanceof Integer) {
                    cell.value = cur.intValue() + 1;
                } else {
                    cell.value = cur.doubleValue() + 1.0;
                }
            }
        }

        return null;
    }

    /* ────────────────────────────────────────────────
       foreach x in list do: … end
       ──────────────────────────────────────────────── */
    @Override
    public Object visitForeachStmt(MinJParser.ForeachStmtContext ctx) {

        String loopVar = ctx.ID().getText();
        Object colObj = visit(ctx.expr());               // evaluated container

        if (!(colObj instanceof List<?> list))
            throw new IllegalArgumentException("Cannot iterate over " + colObj);

        /* create (or shadow) a mutable dynamic cell for the loop variable */
        env.put(loopVar, cellOf(null, Object.class, true, true));

        for (Object item : list) {
            env.put(loopVar, cellOf(item, item.getClass(), true, true));
            visit(ctx.block());
        }
        return null;
    }


    private Object defaultValue(Class<?> t) {
        if (t == Integer.class) return 0;
        if (t == Float.class) return 0f;
        if (t == Double.class) return 0.0;
        if (t == Boolean.class) return false;
        if (t == Character.class) return '\0';
        return "";
    }

    /**
     * Resolve a variable name and return its *value* (not the Cell).
     */
    private Object resolve(String name) {

        /* 1 ─ local scope .......................................... */
        Cell c = env.get(name);
        if (c != null) return c.value;

        /* 2 ─ field of  this  inside a method ...................... */
        if (inMethod) {
            Cell thisCell = env.get("this");
            if (thisCell != null) {
                Obj thiz = (Obj) thisCell.value;          // <-- unwrap first
                Cell field = thiz.fields.get(name);
                if (field != null) return field.value;
            }
        }

        /* 3 ─ global ............................................... */
        Cell g = globals.get(name);
        if (g != null) return g.value;

        throw new IllegalStateException("Undefined variable: " + name);
    }


    /**
     * Get the Cell for a name (locals → this-fields → globals).
     */
    private Cell resolveCell(String name) {

        /* locals */
        if (env.containsKey(name))
            return env.get(name);

        /* inside a method: check fields of   this   */
        if (inMethod && env.containsKey("this")) {
            Object holder = env.get("this");           // <- Cell, not Obj
            Obj thiz = holder instanceof Cell c        // unwrap if needed
                    ? (Obj) c.value
                    : (Obj) holder;
            Cell field = thiz.fields.get(name);
            if (field != null) return field;
        }

        /* globals */
        Cell g = globals.get(name);
        if (g != null) return g;

        throw new IllegalStateException("Undefined variable " + name);
    }

    /* ── inside EvalVisitor ──────────────────────────────── */
    static class ClassDef {
        /**
         * private fields (name → Cell)
         */
        final Map<String, Cell> fields = new HashMap<>();
        final Map<String, MinJParser.MethodDeclContext> methods = new HashMap<>();
    }

    static class Obj {
        final ClassDef def;
        final Map<String, Cell> fields = new HashMap<>();

        Obj(ClassDef d) {
            def = d;
            d.fields.forEach((k, c) -> fields.put(k,
                    cellOf(c.value, c.declaredType, c.mutable, c.dynamic)));
        }
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
    /* ──────────────────────────────────────────────────────────────
   Invoke either a global or an instance method.
   ───────────────────────────────────────────────────────────── */
    private Object invokeMethod(String name,
                                MinJParser.MethodDeclContext decl,
                                List<Object> args,
                                Obj receiver) {

        /* ──  save caller scope  ─────────────────────────────────── */
        Map<String, Cell> oldEnv = new HashMap<>(env);
        Set<String> oldImm = new HashSet<>(immutable);
        boolean wasIn = inMethod;

        env = new HashMap<>();           // fresh local frame
        immutable = new HashSet<>();
        inMethod = true;

        /* 1 ░ implicit  this  for instance methods */
        if (receiver != null) {
            env.put("this",
                    new Cell(receiver,              // value
                            Obj.class,             // declared type
                            false,                 // mutable?  no
                            false));               // dynamic?  no
        }

        /* 2 ░ bind parameters */
        if (decl.paramList() != null) {
            List<String> params = decl.paramList().ID()
                    .stream()
                    .map(TerminalNode::getText)
                    .toList();

            for (int i = 0; i < params.size(); i++) {
                Object arg = args.get(i);
                Class<?> typ = (arg == null) ? Object.class : arg.getClass();

                env.put(params.get(i),
                        new Cell(arg, typ, true, false));   // mutable local, static type
            }
        }

        /* 3 ░ execute body + capture return */
        Object result = null;
        try {
            visitBlock(decl.block());
        } catch (ReturnSignal r) {
            result = switch (r.values.size()) {
                case 0 -> null;
                case 1 -> r.values.getFirst();
                default -> r.values;          // multi-value => List<Object>
            };
        }

        /* 4 ░ restore caller scope */
        env = oldEnv;
        immutable = oldImm;
        inMethod = wasIn;
        return result;
    }


    /**
     * Convenience wrapper for global functions.
     */
    private Object invokeMethod(String name,
                                MinJParser.MethodDeclContext decl,
                                List<Object> args) {
        return invokeMethod(name, decl, args, null);
    }


    private static class ReturnSignal extends RuntimeException {
        final List<Object> values;

        ReturnSignal(List<Object> v) {
            values = v;
        }
    }

    // at top of EvalVisitor:
    private static class Cell {
        /**
         * the current value, mutable at runtime
         */
        Object value;
        /**
         * the declared‐at‐creation type (for static vs dynamic checks)
         */
        final Class<?> declaredType;
        /**
         * true if this name may be reassigned at all
         */
        final boolean mutable;
        /**
         * true if this cell’s type was inferred rather than declared
         */
        final boolean dynamic;

        Cell(Object value, Class<?> declaredType, boolean mutable, boolean dynamic) {
            this.value = value;
            this.declaredType = declaredType;
            this.mutable = mutable;
            this.dynamic = dynamic;
        }
    }


    private static Cell cellOf(Object v, Class<?> t, boolean mutable, boolean dynamic) {
        return new Cell(v, t, mutable, dynamic);
    }


}