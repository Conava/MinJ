# MinJ Language Interpreter

A simple interpreter for the MinJ toy language, implemented in Java using ANTLR 4. Supports typed declarations (`var`/
`val`), arithmetic, comparisons, `print` and nested `if`/`elseif`/`else` blocks.

## 📦 Requirements

- JDK 21 (or adjust Gradle toolchain to your installed JDK)
- Gradle 8.5+
- Internet access to download ANTLR and Shadow plugin dependencies

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

Defined in `src/main/antlr/MinJ.g4`:

- **program**: `statement* EOF`
- **statement**: `varDecl | assign | printStmt | ifStmt`
- **varDecl**: `(type | VAR | VAL) ID ('=' expr)?`
- **assign**: `ID ASSIGN expr`
- **printStmt**: `'print' expr`
- **ifStmt**: `if expr then ':' block (elseif expr then ':' block)* (else ':' block)?`
- **expr**: binary operations (`*`,`/`,`+`,`-`, comparisons) with precedence
- **primary**: literals (`INT`, `FLOAT_LIT`, `DOUBLE_LIT`, `STRING`, `CHAR`, `BOOL_LIT`), `ID`, or parenthesis

Whitespace, comments, keywords, operators and literal forms are handled by lexer rules.

## 🚀 Run Binary

The interpreter is packaged as a fat-JAR in the release section, including all dependencies.  
You can run it directly from the command line:

```bash
cd Downloads/MinJ-<VERSION>
java -jar minjc-<VERSION>.jar <PROGRAM_NAME>.mj
```

## 🔍 How It Works

1. **ANTLR Generation**
    - Gradle’s `antlr` plugin reads `MinJ.g4` and generates `MinJLexer.java`, `MinJParser.java`, `MinJBaseVisitor.java`,
      etc.

2. **Parsing** (`Main.java`)
    - Reads source file via `CharStreams.fromPath()`
    - Feeds into `MinJLexer` → `CommonTokenStream` → `MinJParser.program()` → `ParseTree`

3. **Evaluation** (`EvalVisitor.java`)
    - Extends `MinJBaseVisitor<Object>`
    - Overrides:
        - `visitVarDecl` → allocate in `env` (mark `val` immutable)
        - `visitAssign` → update `env` (error if immutable)
        - `visitPrintStmt` → print values, explicitly showing `'\0'` and `""` for empty char/string
        - `visitIfStmt` → evaluate conditions in order, execute matching `block`
        - `visitExpr` → perform arithmetic & comparisons via `ctx.op.getText()`
        - `visitPrimary` → parse literals, look up variables, handle parentheses
    - Calling `visitor.visit(tree)` walks the AST and executes statements

## 🚀 Extending MinJ

1. **Add Grammar Rule**  
   Example: how to add `while` loops
   ```
   // in MinJ.g4
   whileStmt: 'while' expr 'do' ':' block ;
   statement: varDecl | assign | printStmt | ifStmt | whileStmt ;
   ```

2. **Rebuild**
   ```bash  
   ./gradlew clean generateGrammarSource compileJava shadowJar  
   ```

3. **Implement Visitor**
   ```java
   @Override
   public Object visitWhileStmt(MinJParser.WhileStmtContext ctx) {
   while ((Boolean) visit(ctx.expr())) {
   visitBlock(ctx.block());
   }
   return null;
   }
   ```

4. **Test**
    - Create a `.mj` file using `while`, run `java -jar build/libs/minjc-0.1.0.jar yourLoop.mj`, and verify the loop
      executes as expected.


## 🏠 GitHub Pages

GitHub Pages automatically serves the project documentation from the `gh-pages` branch (configured in repository
settings). A GitHub Actions workflow triggers on pushes to `main`, builds the site by copying `README.md` into a static
site, and deploys to `gh-pages`. The site is available at `https://Conava.github.io/MinJ`.

## 🚀 CI/CD Pipeline

The CI/CD pipeline is defined in `.github/workflows/ci.yml`. On each push or pull request to `main`:

1. Checkout repository
2. Set up JDK 21 via `actions/setup-java`
3. Run `./gradlew clean shadowJar` to build the fat‑JAR
4. Execute `./gradlew test` for unit tests
5. Archive the `build/libs/minjc-*.jar` artifact

All steps must pass before merging, ensuring the interpreter builds and tests succeed.
---