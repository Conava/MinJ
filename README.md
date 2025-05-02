# MinJ Language

MinJ, short for minimalistic Java, is a lightweight interpreter designed to bring a concise, statically typed scripting language to the JVM. Built from the ground up in Java with ANTLRv4 for lexical and syntactic analysis, MinJ provides an accessible platform for experimenting with language design and interpreter implementation. Its clean grammar and modular visitor‑based execution model ensure fast parsing and straightforward extensibility.

MinJ currently supports variable declarations (var and val), arithmetic and comparison operations, conditional statements (if/elseif/else), loops (while, for, foreach), list manipulation, and printing literals or variables, enabling you to write programs like FizzBuzz, iterate over collections, and perform calculations with ease.

MinJ is built using Gradle with the ANTLR plugin and ShadowJar for packaging a standalone fat‑JAR. To use the language yourself: simply download the release and execute your scripts via `java -jar minjc-<VERSION>.jar <yourfile>.mj`. A comprehensive CI/CD pipeline on GitHub Actions ensures that every change is validated, tested, and packaged automatically for reliable releases.

## 📦 Development Requirements

* JDK 21 (or adjust Gradle toolchain to your installed JDK)
* Gradle 8.5+
* ANTLR 4.13.0 (included via Gradle plugin)

## 🖥️ IDE Support

For grammar development and live parse tree visualization, it's recommended to use IntelliJ IDEA Ultimate with the ANTLR v4 plugin. This setup provides:

* Real-time grammar validation and error highlighting
* Automatic generation of lexer/parser code
* Live parse tree preview for rapid debugging
* Syntax-aware editing and refactoring

## ⚙️ Build & Run

1. **Build fat-JAR** (includes ANTLR runtime):

   ```bash
   ./gradlew clean shadowJar  
   ```

2. **Run interpreter**:

   ```bash
   java -jar build/libs/minjc-<VERSION>.jar <PROGRAM_NAME>.mj  
   ```

## 📝 Grammar Overview

Below is an in-depth look at the MinJ grammar defined in `src/main/antlr/MinJ.g4`, followed by illustrative code snippets. This grammar is processed by ANTLR to generate a rich parse tree and a visitor API; we then implement a custom `EvalVisitor` to traverse the AST-like structure for execution. The modular grammar ensures clear separation of syntax, semantics, and evaluation logic, making MinJ highly extensible and easy to debug.

---

### 1. Top-Level Structure

```antlr
program
: (statement? NEWLINE)*    // zero or more lines (statements or blanks)
statement?               // optional last statement without trailing newline
EOF
;
```

* **program**: one statement per line (allows blank lines), plus an optional final line.
* **NEWLINE** is `('\r'? '\n')+`, so every physical line break is significant.

---

### 2. Statements

```antlr
statement
: varDecl
| assign
| printStmt
| ifStmt
| whileStmt
| forStmt
| foreachStmt
;
```

| Rule            | Syntax                                                              | Example                       |
|-----------------|---------------------------------------------------------------------|-------------------------------|
| **varDecl**     | `(type \| VAR \| VAL) ID (ASSIGN expr)?`                            | `var x = 10``int i`           |
| **assign**      | `ID ASSIGN expr`                                                    | `x = x + 1`                   |
| **printStmt**   | `PRINT expr`                                                        | `print "Hello"`               |
| **ifStmt**      | `IF expr THEN COLON block (ELSEIF…)* (ELSE…)? END`                  | see below                     |
| **whileStmt**   | `WHILE expr DO COLON block END`                                     | `while n < 5 do: … end`       |
| **forStmt**     | `FOR (varDecl \| assign) TO expr (STEP assign)? DO COLON block END` | see below                     |
| **foreachStmt** | `FOREACH ID IN expr DO COLON block END`                             | `foreach n in nums do: … end` |

---

### 3. Expression Grammar

```antlr
expr
: expr op=('*'|'/''|'%') expr
| expr op=('+'|'-') expr
| expr op=(LT|GT|LE|GE|EQ|NE) expr
| primary
;
```

Operator precedence (high → low):

1. `*` `/` `%`
2. `+` `-`
3. `<` `>` `<=` `>=` `==` `!=`
4. **primary**

---

### 4. Primary Values & Literals

```antlr
primary
: INT
| FLOAT_LIT
| DOUBLE_LIT
| STRING
| CHAR
| BOOL_LIT
| ID
| LPAREN expr RPAREN
| listLiteral
;
```

* **INT**: `[0-9]+`
* **FLOAT\_LIT**: `[0-9]+ '.' [0-9]+ [fF]`
* **DOUBLE\_LIT**: `[0-9]+ '.' [0-9]+ ([eE][+-]?[0-9]+)?`
* **STRING**: `" ... "` with `\"` escapes
* **CHAR**: `'\0'`, `'\n'`, etc.
* **BOOL\_LIT**: `true` | `false`
* **ID**: `[a-zA-Z_][a-zA-Z_0-9]*`
* **listLiteral**: `'[' (expr (',' expr)*)? ']'`

---

### 5. Lexer Highlights

```antlr
NEWLINE      : '\r'? '\n' ;
WS           : [ \t]+               -> skip ;
LINE_COMMENT : '//' ~[\r\n]*        -> skip ;
HASH_COMMENT : '#'  ~[\r\n]*        -> skip ;
BLOCK_COMMENT: '/*' .*? '*/'        -> skip ;

COMMA        : ',' ;
MOD          : '%'  ;

// Keywords
PRINT  : 'print' ;
IF     : 'if' ;
...            // then/elseif/else/while/for/etc.

// Operators
ASSIGN : '=' ;
LT     : '<' ;
...            // <=, >=, ==, !=
COLON  : ':' ;
LPAREN : '(' ; RPAREN : ')' ;
LBRACK : '[' ; RBRACK : ']' ;
```

---

## 📚 Examples

### Variable Declaration & Printing

```minj
var x = 42
int y         // default 0
print x       // 42
print y       // 0
```

### Conditional: FizzBuzz

```minj
for i = 1 to 20 step i = i + 1 do:
    if i % 15 == 0 then:
        print "FizzBuzz"
    elseif i % 3 == 0 then:
        print "Fizz"
    elseif i % 5 == 0 then:
        print "Buzz"
    else:
        print i
    end
end
```

### Lists & foreach

```minj
var nums = [1, 2, 3, 4, 5]
print "Numbers:"
foreach n in nums do:
    print n
end

var names = ["Alice", "Bob", "Charlie"]
print "Names:"
foreach name in names do:
    print name
end
```

### Nested Blocks

```minj
var sum = 0
while sum < 10 do:
    if sum % 2 == 0 then:
        print sum
    end
    sum = sum + 1
end
```

## 🚀 Run Binary

The interpreter is packaged as a fat-JAR in the release section, including all dependencies.
You can run it directly from the command line:

```bash
cd Downloads/MinJ-<VERSION>
java -jar minjc-<VERSION>.jar <PROGRAM_NAME>.mj
```

## 🔍 How It Works

1. **ANTLR Generation**

   * Gradle’s `antlr` plugin reads `MinJ.g4` and generates `MinJLexer.java`, `MinJParser.java`, `MinJBaseVisitor.java`, etc.

2. **Parsing** (`Main.java`)

   * Reads source file via `CharStreams.fromPath()`
   * Feeds into `MinJLexer` → `CommonTokenStream` → `MinJParser.program()` → `ParseTree`

3. **Evaluation** (`EvalVisitor.java`)

   * Extends `MinJBaseVisitor<Object>`
   * Overrides:

      * `visitVarDecl` → allocate in `env` (mark `val` immutable)
      * `visitAssign` → update `env` (error if immutable)
      * `visitPrintStmt` → print values, explicitly showing `'\0'` and `""` for empty char/string
      * `visitIfStmt` → evaluate conditions in order, execute matching `block`
      * `visitExpr` → perform arithmetic & comparisons via `ctx.op.getText()`
      * `visitPrimary` → parse literals, look up variables, handle parentheses
   * Calling `visitor.visit(tree)` walks the AST and executes statements

## 🚀 Extending MinJ

1. **Add Grammar Rule**
   Example: how the while loop has been added to the language:
   
   in MinJ.g4:
   * Add the `whileStmt` rule:
     
   ```antlr
   whileStmt: 'while' expr 'do' ':' block ; 
   ```
   * `expr` is the condition, `block` is the body.
   
   * Add `whileStmt` to the `statement` rule:
   
   ```antlr
   statement: varDecl | assign | printStmt | ifStmt | whileStmt ;
   ```
   * Add `WHILE`, `DO`, and `END` keywords to the lexer rules:
    
   ```antlr
   WHILE: 'while' ;
   DO: 'do' ;
   END: 'end' ;
   ```

2. **Implement Visitor Logic**
    * In `EvalVisitor.java`, implement the `visitWhileStmt` method:
    
    ```java
    @Override
    public Object visitWhileStmt(MinJParser.WhileStmtContext ctx) {
         while ((Boolean) visit(ctx.expr())) {
              visitBlock(ctx.block());
         }
         return null;
    }
    ```
    * This method evaluates the condition and executes the block repeatedly until the condition is false.

3**Rebuild**

   ```bash
   ./gradlew clean generateGrammarSource compileJava shadowJar  
   ```

4**Implement Visitor** java @Override public Object visitWhileStmt(MinJParser.WhileStmtContext ctx) { while ((Boolean) visit(ctx.expr())) { visitBlock(ctx.block()); } return null; }

5**Test**

   * Create a `.mj` file using `while`, run `java -jar build/libs/minjc-0.1.0.jar yourLoop.mj`, and verify the loop executes as expected.

## 🏠 GitHub Pages

GitHub Pages automatically serves the project documentation from the `master` branch (configured in repository settings). A GitHub Actions workflow triggers on pushes to `main`, builds the site by copying `README.md` into a static site, and deploys to `gh-pages`. The site is available at `https://Conava.github.io/MinJ`.

## 🚀 CI/CD Pipeline

The CI/CD pipeline is defined in `.github/workflows/ci.yml`. On each push or pull request to `release`:

1. Checkout repository
2. Set up JDK 21 via `actions/setup-java`
3. Run `./gradlew clean generateGrammerSource shadowJar` to build the fat‑JAR including all dependencies
4. Execute `./gradlew test` for unit tests
5. Archive the `build/libs/minjc-*.jar` artifact
6. Package the minjc jar with the examples and this README to a zip file
7. Upload the zip file as a release asset
8. Finish and Tag the Release on GitHub

---

## 🛠️ Tools & Dependencies

MinJ leverages several tools to automate and streamline language development, parsing, and distribution:

### ANTLR 4.13.0

* **What it is**: A powerful parser generator that reads a grammar file and produces a lexer and parser in Java.
* **How it works**: ANTLR builds a *parse tree* based on your grammar. We extend the generated `MinJBaseVisitor` to walk this tree, interpreting each node.
* **Usage**: Configured via Gradle’s ANTLR plugin; source generation runs in the `generateGrammarSource` phase. The generated Java files reside in `build/generated-src/antlr/main`.
* **Why use it**: ANTLR supports complex grammars (left recursion, precedence), robust error recovery, and a visitor/listener API for easy AST traversal and custom logic.

### Gradle & ShadowJar

* **Gradle**: Provides dependency management, task orchestration, and a plugin ecosystem. We declare the ANTLR and ShadowJar plugins in `build.gradle`, automating grammar generation, compilation, testing, and packaging.
* **ShadowJar**: Bundles all runtime dependencies (ANTLR runtime, etc.) into a single “fat” JAR, simplifying distribution and execution.

### Java & JDK 21

* **Java**: The host platform for MinJ; we target Java 21 to leverage modern language features.
* **Toolchain**: Gradle’s toolchain settings ensure consistent builds across environments.

### GitHub Actions

* Automates builds, tests, and releases on every commit.
* Publishes artifacts and deploys documentation to GitHub Pages.

These tools together create a robust, reproducible development workflow, from grammar changes to production-ready interpreters.
