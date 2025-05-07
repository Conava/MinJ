package com.conava;

import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import com.conava.MinJBaseVisitor;
import com.conava.MinJParser;

/**
 * {@code EvalVisitor} walks the parse tree of a MinJ program and
 * executes it immediately, maintaining environments for variables,
 * functions, and classes.
 */
public class EvalVisitor extends MinJBaseVisitor<Object> {

    // === Runtime Environments ===

    /**
     * Local variables and parameters (name → Cell).
     */
    private Map<String, Cell> env = new HashMap<>();

    /**
     * Names that are declared as 'val' (immutable).
     */
    private Set<String> immutable = new HashSet<>();

    /**
     * Global variables (outside any function or class).
     */
    private final Map<String, Cell> globals = new HashMap<>();

    /**
     * Global (public) functions (name → MethodDeclContext).
     */
    private final Map<String, MinJParser.MethodDeclContext> globalMethods = new HashMap<>();

    /**
     * Class definitions (name → ClassDef).
     */
    private final Map<String, ClassDef> classTable = new HashMap<>();

    /**
     * True when traversing inside a class declaration.
     */
    private boolean inClass = false;

    /**
     * True when traversing inside a method body.
     */
    private boolean inMethod = false;

    /**
     * The ClassDef currently being populated.
     */
    private ClassDef currentClass;


    // === Visitor Entry Points ===

    /**
     * Executes the entire program.
     */
    @Override
    public Object visitProgram(MinJParser.ProgramContext ctx) {
        // ctx.topLevelDecl() is a List<TopLevelDeclContext>
        for (MinJParser.TopLevelDeclContext tld : ctx.topLevelDecl()) {
            visit(tld);
        }
        return null;
    }

    /**
     * Dispatches to classDecl, methodDecl, or statement depending on the context.
     */
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

    // === Class and Method Declarations ===

    /**
     * Registers a new class and initializes its fields/methods.
     */
    @Override
    public Object visitClassDecl(MinJParser.ClassDeclContext ctx) {
        String className = ctx.ID().getText();
        ClassDef def = new ClassDef();
        classTable.put(className, def);

        // enter class‐context
        boolean oldInClass = inClass;
        ClassDef oldCurrent = currentClass;
        inClass = true;
        currentClass = def;

        for (ParseTree child : ctx.classBody().children) {
            if (child instanceof MinJParser.FieldDeclContext f) {
                // instead of binding into env, bind directly into def.fields
                MinJParser.VarDeclContext vdc = f.varDecl();
                List<TerminalNode> ids = vdc.idList().ID();
                // compute the initializer value or default
                Object initVal = vdc.ASSIGN() != null
                        ? visit(vdc.expr())
                        : defaultValue(
                        vdc.type() != null
                                ? tokenToClass(vdc.type().getText())
                                : Object.class
                );
                // declared type and mutability/dynamic flags
                Class<?> declared = vdc.type() != null
                        ? tokenToClass(vdc.type().getText())
                        : (initVal == null ? Object.class : initVal.getClass());
                boolean mutable = vdc.VAL() == null;
                boolean dynamic = vdc.VAR() != null;

                for (TerminalNode id : ids) {
                    def.fields.put(
                            id.getText(),
                            cellOf(initVal, declared, mutable, dynamic)
                    );
                }
            } else if (child instanceof MinJParser.MethodDeclContext m) {
                def.methods.put(m.ID().getText(), m);
            } else if (child instanceof MinJParser.StatementContext s) {
                // static initializer in a class
                visit(s);
            }
        }

        // restore
        inClass = oldInClass;
        currentClass = oldCurrent;
        return null;
    }


    @Override
    public Object visitMethodDecl(MinJParser.MethodDeclContext ctx) {
        String name = ctx.ID().getText();
        if (inClass) { // inside a class => bind to the class
            currentClass.methods.put(name, ctx);
        } else { // global method
            globalMethods.put(name, ctx);
        }
        return null;
    }

    // === Statement Visitors ===

    public Object visitVarDecl(MinJParser.VarDeclContext ctx) {
        List<TerminalNode> ids = ctx.idList().ID();
        boolean dynamic = ctx.VAR() != null;
        boolean mutable = ctx.VAL() == null;

        Object initial = (ctx.ASSIGN() != null)
                ? visit(ctx.expr())
                : defaultValue(ctx.type() != null
                ? tokenToClass(ctx.type().getText())
                : Object.class);

        bindIds(ids, initial, false, dynamic, mutable);
        return null;
    }

    @Override
    public Object visitAssign(MinJParser.AssignContext ctx) {
        String name = ctx.idList().ID(0).getText();
        Object value = visit(ctx.expr());

        if (inMethod && ctx.idList().ID().size() == 1) {
            try {
                Cell cell = resolveCell(name);
                if ((cell.declaredType.isAssignableFrom(value.getClass()) || cell.dynamic)
                        && cell.mutable) {
                    cell.value = value;
                    return null;
                }
            } catch (IllegalStateException ignored) {
                // fall through to rebind below
            }
        }

        bindIds(ctx.idList().ID(), value, true, false, true);
        return null;
    }

    @Override
    public Object visitPrintStmt(MinJParser.PrintStmtContext ctx) {
        System.out.println(visit(ctx.expr()));
        return null;
    }

    @Override
    public Object visitIfStmt(MinJParser.IfStmtContext ctx) {
        for (int i = 0; i < ctx.expr().size(); i++) {
            if (Boolean.TRUE.equals(visit(ctx.expr(i)))) {
                return visit(ctx.block(i));
            }
        }
        if (ctx.block().size() > ctx.expr().size()) {
            return visit(ctx.block(ctx.block().size() - 1));
        }
        return null;
    }

    @Override
    public Object visitWhileStmt(MinJParser.WhileStmtContext ctx) {
        while ((boolean) visit(ctx.expr())) {
            visit(ctx.block());
        }
        return null;
    }

    @Override
    public Object visitForStmt(MinJParser.ForStmtContext ctx) {
        Cell cell = initForLoop(ctx);
        Number upper = (Number) visit(ctx.expr());
        MinJParser.AssignContext step = extractStep(ctx);

        while (((Number) cell.value).doubleValue() <= upper.doubleValue()) {
            visit(ctx.block());
            if (step != null) {
                cell.value = visit(step.expr());
            } else {
                cell.value = increment(cell.value);
            }
        }
        return null;
    }

    @Override
    public Object visitForeachStmt(MinJParser.ForeachStmtContext ctx) {
        String var = ctx.ID().getText();
        Object collection = visit(ctx.expr());
        if (!(collection instanceof List<?> list)) {
            throw new IllegalArgumentException("Cannot iterate over: " + collection);
        }
        env.put(var, cellOf(null, Object.class, true, true));
        for (Object item : list) {
            env.get(var).value = item;
            visit(ctx.block());
        }
        return null;
    }

    @Override
    public Object visitReturnStmt(MinJParser.ReturnStmtContext ctx) {
        LinkedList<Object> values = new LinkedList<>();
        if (ctx.exprList() != null) {
            for (MinJParser.ExprContext e : ctx.exprList().expr()) {
                values.add(visit(e));
            }
        }
        throw new ReturnSignal(values);
    }

    @Override
    public Object visitExprStmt(MinJParser.ExprStmtContext ctx) {
        return visit(ctx.expr());
    }

    // === Expression Visitors ===

    @Override
    public Object visitExpr(MinJParser.ExprContext ctx) {
        // Unary
        if (ctx.op == null && ctx.expr().size() == 1) {
            String op = ctx.getChild(0).getText();
            Object v = visit(ctx.expr(0));
            return switch (op) {
                case "!" -> !(Boolean) v;
                case "-" -> negate(v);
                default -> throw new IllegalArgumentException("Unknown unary: " + op);
            };
        }

        // Binary
        if (ctx.op != null) {
            Object l = visit(ctx.expr(0));
            Object r = visit(ctx.expr(1));
            return evaluateBinary(l, r, ctx.op.getText());
        }

        // Primary
        return visit(ctx.primary());
    }

    @Override
    public Object visitNewExpr(MinJParser.NewExprContext ctx) {
        ClassDef def = classTable.get(ctx.ID().getText());
        if (def == null) throw new IllegalStateException("Unknown class");
        return new Obj(def);
    }

    @Override
    public Object visitCallExprPrimary(MinJParser.CallExprPrimaryContext ctx) {
        String name = ctx.ID().getText();
        if ("input".equals(name)) {
            String prompt = ctx.argList() != null && !ctx.argList().expr().isEmpty()
                    ? String.valueOf(visit(ctx.argList().expr(0)))
                    : null;
            return readLine(prompt);
        }

        MinJParser.MethodDeclContext decl = globalMethods.get(name);
        if (decl == null) throw new IllegalStateException("Unknown function: " + name);

        List<Object> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (var e : ctx.argList().expr()) {
                args.add(visit(e));
            }
        }
        return invokeMethod(name, decl, args);
    }

    @Override
    public Object visitDotCallExpr(MinJParser.DotCallExprContext ctx) {
        Obj obj = (Obj) visit(ctx.primary());
        String method = ctx.ID().getText();
        MinJParser.MethodDeclContext decl = obj.def.methods.get(method);
        if (decl == null) throw new IllegalStateException("No method: " + method);

        List<Object> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (var e : ctx.argList().expr()) {
                args.add(visit(e));
            }
        }
        return invokeMethod(method, decl, args, obj);
    }

    @Override
    public Object visitBlock(MinJParser.BlockContext ctx) {
        for (MinJParser.StatementContext s : ctx.statement()) {
            visit(s);
        }
        return null;
    }

    // === Literal Visitors ===

    @Override
    public Object visitIntLiteral(MinJParser.IntLiteralContext ctx) {
        return Integer.parseInt(ctx.INT().getText());
    }

    @Override
    public Object visitFloatLiteral(MinJParser.FloatLiteralContext ctx) {
        return Float.parseFloat(ctx.FLOAT_LIT().getText().replaceAll("[fF]$", ""));
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
        return ctx.CHAR().getText().charAt(1);
    }

    @Override
    public Object visitBoolLiteral(MinJParser.BoolLiteralContext ctx) {
        return Boolean.parseBoolean(ctx.BOOL_LIT().getText());
    }

    @Override
    public Object visitVarReference(MinJParser.VarReferenceContext ctx) {
        return resolve(ctx.ID().getText());
    }

    @Override
    public Object visitParenExpr(MinJParser.ParenExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public Object visitListExpr(MinJParser.ListExprContext ctx) {
        return visitListLiteral(ctx.listLiteral());
    }

    @Override
    public Object visitListLiteral(MinJParser.ListLiteralContext ctx) {
        List<Object> result = new ArrayList<>();
        for (var e : ctx.expr()) {
            result.add(visit(e));
        }
        return result;
    }

    // === Utilities & Helpers ===

    /**
     * Binds new or reassigned identifiers in the current env.
     */
    private void bindIds(List<TerminalNode> ids,
                         Object rhs,
                         boolean reassign,
                         boolean dynamic,
                         boolean mutable) {
        List<?> vals = ids.size() == 1
                ? List.of(rhs)
                : (List<?>) rhs;
        if (vals.size() != ids.size()) {
            throw new IllegalStateException("Bind arity mismatch");
        }
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i).getText();
            Object v = vals.get(i);
            Class<?> t = v.getClass();

            if (reassign) {
                Cell old = resolveCell(id);
                if (!old.mutable) throw new IllegalStateException("Cannot reassign val " + id);
                if (!old.dynamic && !old.declaredType.isAssignableFrom(t)) {
                    throw new IllegalStateException("Type mismatch for " + id);
                }
                env.put(id, cellOf(v, old.dynamic ? t : old.declaredType, true, old.dynamic));
            } else {
                env.put(id, cellOf(v, t, mutable, dynamic));
                if (!mutable) immutable.add(id);
            }
        }
    }

    /**
     * Maps MinJ type names to Java classes.
     */
    private static Class<?> tokenToClass(String tok) {
        return switch (tok) {
            case "int", "integer", "Int", "Integer" -> Integer.class;
            case "float", "Float" -> Float.class;
            case "double", "Double" -> Double.class;
            case "boolean", "bool", "Boolean", "Bool" -> Boolean.class;
            case "char", "Char" -> Character.class;
            case "String", "string" -> String.class;
            default -> Object.class;
        };
    }

    /**
     * Looks up a variable name and returns its stored value.
     */
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
        Cell c = env.get(name);
        if (c != null) return c.value;
        if (inMethod) {
            Cell thizCell = env.get("this");
            if (thizCell != null) {
                Obj obj = (Obj) thizCell.value;
                Cell f = obj.fields.get(name);
                if (f != null) return f.value;
            }
        }
        Cell g = globals.get(name);
        if (g != null) return g.value;
        throw new IllegalStateException("Undefined: " + name);
    }

    /**
     * Get the Cell for a name (locals → this.fields → globals).
     */
    private Cell resolveCell(String name) {
        if (env.containsKey(name)) return env.get(name);
        if (inMethod && env.containsKey("this")) {
            Obj obj = (Obj) env.get("this").value;
            Cell f = obj.fields.get(name);
            if (f != null) return f;
        }
        Cell g = globals.get(name);
        if (g != null) return g;
        throw new IllegalStateException("Undefined: " + name);
    }

    /**
     * Reads one line of user input, prompting with {@code prompt} and a “> ” marker.
     */
    private String readLine(String prompt) {
        if (prompt != null && !prompt.isEmpty()) {
            System.out.print(prompt + " ");
        }
        System.out.print("Input > ");
        System.out.flush();
        try {
            return new BufferedReader(new InputStreamReader(System.in)).readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // === Invocation of Methods ===

    /**
     * Invoke either a global or instance method.
     *
     * @param name method name
     * @param decl its MethodDeclContext
     * @param args evaluated arguments
     */
    private Object invokeMethod(String name,
                                MinJParser.MethodDeclContext decl,
                                List<Object> args) {
        return invokeMethod(name, decl, args, null);
    }

    /**
     * Invoke either a global or instance method.
     * Sets up a fresh local frame, binds 'this' (if non-null), parameters,
     * runs the method body, captures any return, and restores the caller frame.
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
        var oldEnv = env;
        var oldImm = immutable;
        var wasIn = inMethod;
        env = new HashMap<>();
        immutable = new HashSet<>();
        inMethod = true;

        if (receiver != null) {
            env.put("this", cellOf(receiver, Obj.class, false, false));
        }
        if (decl.paramList() != null) {
            var params = decl.paramList().ID().stream()
                    .map(TerminalNode::getText)
                    .toList();
            for (int i = 0; i < params.size(); i++) {
                Object arg = args.get(i);
                env.put(params.get(i), cellOf(arg,
                        arg == null ? Object.class : arg.getClass(),
                        true, false));
            }
        }

        Object result = null;
        try {
            visit(decl.block());
        } catch (ReturnSignal rs) {
            result = switch (rs.values.size()) {
                case 0 -> null;
                case 1 -> rs.values.getFirst();
                default -> rs.values;
            };
        }

        env = oldEnv;
        immutable = oldImm;
        inMethod = wasIn;
        return result;
    }


    private Cell initForLoop(MinJParser.ForStmtContext ctx) {
        if (ctx.varDecl() != null) {
            visit(ctx.varDecl());
            return resolveCell(ctx.varDecl().idList().ID(0).getText());
        } else {
            var assign = ctx.assign().getFirst();
            Object initial = visit(assign.expr());
            bindIds(assign.idList().ID(), initial, false, true, true);
            return resolveCell(assign.idList().ID(0).getText());
        }
    }

    private MinJParser.AssignContext extractStep(MinJParser.ForStmtContext ctx) {
        if (ctx.assign().size() > 1) return ctx.assign().get(1);
        if (ctx.assign().size() == 1 && ctx.varDecl() != null) return ctx.assign().get(0);
        return null;
    }

    private Object increment(Object v) {
        if (v instanceof Integer i) return i + 1;
        return ((Number) v).doubleValue() + 1.0;
    }

    private Object negate(Object v) {
        if (v instanceof Integer i) return -i;
        if (v instanceof Number n) return -n.doubleValue();
        throw new IllegalArgumentException("Cannot negate: " + v);
    }

    private String unquoteString(TerminalNode str) {
        String s = str.getText();
        if (s.length() < 2) return s;
        return s.substring(1, s.length() - 1);
    }

    private Object evaluateBinary(Object l, Object r, String op) {
        switch (op) {
            case "+":
                if (l instanceof String || r instanceof String) return String.valueOf(l) + r;
                if (l instanceof Integer && r instanceof Integer) return (Integer) l + (Integer) r;
                return ((Number) l).doubleValue() + ((Number) r).doubleValue();
            case "-":
                if (l instanceof Integer && r instanceof Integer) return (Integer) l - (Integer) r;
                return ((Number) l).doubleValue() - ((Number) r).doubleValue();
            case "*":
                if (l instanceof Integer && r instanceof Integer) return (Integer) l * (Integer) r;
                return ((Number) l).doubleValue() * ((Number) r).doubleValue();
            case "/":
                if (l instanceof Integer && r instanceof Integer) return (Integer) l / (Integer) r;
                return ((Number) l).doubleValue() / ((Number) r).doubleValue();
            case "%":
                if (l instanceof Integer && r instanceof Integer) return (Integer) l % (Integer) r;
                return ((Number) l).doubleValue() % ((Number) r).doubleValue();
            case "<":
                return ((Number) l).doubleValue() < ((Number) r).doubleValue();
            case "<=":
                return ((Number) l).doubleValue() <= ((Number) r).doubleValue();
            case ">":
                return ((Number) l).doubleValue() > ((Number) r).doubleValue();
            case ">=":
                return ((Number) l).doubleValue() >= ((Number) r).doubleValue();
            case "==":
                return l.equals(r);
            case "!=":
                return !l.equals(r);
            case "&&":
            case "and":
            case "AND":
                return (Boolean) l && (Boolean) r;
            case "||":
            case "or":
            case "OR":
                return (Boolean) l || (Boolean) r;
            case "^":
            case "xor":
            case "XOR":
                return (Boolean) l ^ (Boolean) r;
            default:
                throw new IllegalArgumentException("Unknown operator: " + op);
        }
    }

    // === Nested Types ===

    /**
     * Represents a class: its fields and methods.
     */
    private static class ClassDef {
        final Map<String, Cell> fields = new HashMap<>();
        final Map<String, MinJParser.MethodDeclContext> methods = new HashMap<>();
    }

    /**
     * Runtime instance of a class, holding its own field cells.
     */
    private static class Obj {
        final ClassDef def;
        final Map<String, Cell> fields = new HashMap<>();

        Obj(ClassDef d) {
            this.def = d;
            d.fields.forEach((k, c) ->
                    fields.put(k, cellOf(c.value, c.declaredType, c.mutable, c.dynamic))
            );
        }
    }

    /**
     * Thrown to unwind the stack when a return statement is encountered.
     */
    private static class ReturnSignal extends RuntimeException {
        final LinkedList<Object> values;

        ReturnSignal(LinkedList<Object> v) {
            this.values = v;
        }
    }

    /**
     * A storage cell for one variable: its value, declared type, mutability, and dynamic flag.
     */
    private static class Cell {
        Object value;
        final Class<?> declaredType;
        final boolean mutable;
        final boolean dynamic;

        Cell(Object v, Class<?> t, boolean m, boolean d) {
            this.value = v;
            this.declaredType = t;
            this.mutable = m;
            this.dynamic = d;
        }
    }

    /**
     * Factory for creating a Cell.
     */
    private static Cell cellOf(Object v, Class<?> t, boolean m, boolean d) {
        return new Cell(v, t, m, d);
    }
}