# MinJ Language

MinJ, short for minimalistic Java, is a lightweight interpreter designed to bring a concise, statically typed scripting language to the JVM. Built from the ground up in Java with ANTLRv4 for lexical and syntactic analysis, MinJ provides an accessible platform for experimenting with language design and interpreter implementation. Its clean grammar and modular visitor‑based execution model ensure fast parsing and straightforward extensibility.

MinJ currently supports variable declarations (var and val), arithmetic and comparison operations, conditional statements (if/elseif/else), loops (while, for, foreach), list manipulation, and printing literals or variables, enabling you to write programs like FizzBuzz, iterate over collections, and perform calculations with ease.

MinJ is built using Gradle with the ANTLR plugin and ShadowJar for packaging a standalone fat‑JAR. To use the language yourself: simply download the release and execute your scripts via `java -jar minjc-<VERSION>.jar <yourfile>.mj`. A comprehensive CI/CD pipeline on GitHub Actions ensures that every change is validated, tested, and packaged automatically for reliable releases.

**Key Features**
- **Statically‑typed declarations** with `int`, `String`, `boolean`, etc., plus **dynamic** variables via `var` and **immutable** constants via `val`.
- **Rich expression syntax**: arithmetic, comparisons, boolean operators (`&&`/`and`, `||`/`or`, `^`/`xor`, `!`/`not`).
- **Control flow**: `if`/`elseif`/`else`, `while`, `for … to … [step …]`, `foreach … in …`.
- **First‑class lists**: literal syntax `[1,2,3]` and iteration with `foreach`.
- **Functions & methods**: global `func`/`method`, instance methods, `new`‑based object creation, `return`.
- **Built‑in I/O**: `print(...)` and `input(...)` for interactive prompts.
- **Error checking**: static vs. dynamic type enforcement, immutability (`val`) violations, undefined‑name errors.
- **Extensible grammar**: modular ANTLR4 `.g4` grammar with clearly layered rules (declarations, statements, expressions, primary).

MinJ ships as a standalone “fat‑JAR” built with Gradle and the ANTLR plugin. Every commit runs through a CI/CD pipeline (GitHub Actions) that regenerates the parser, runs the full test suite (unit tests, self‑test scripts), and publishes versioned releases.

## Quickstart

1. **Download** the latest release: https://github.com/Conava/MinJ/releases
2. **Unzip** the archive.
3. **Run** the interpreter:

   ```bash 
    java -jar minjc-<VERSION>.jar <yourfile>.mj
    ```
    Replace `<VERSION>` with the actual version number and `<yourfile>` with your MinJ script.

4. **Explore** the examples in the `examples` directory to see MinJ in action.

5. **Write** your own MinJ scripts using the provided grammar as a reference.

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

Below is an in-depth look at the MinJ grammar defined in `src/main/antlr/MinJ.g4`, annotated with extensive explanations. Wherever a code‑block would normally begin or end with triple backticks (```), you'll see the marker **```** instead.

---

### 1. Top‑Level Structure

**Grammar snippet**  

```antlrv4
program  
: (topLevelDecl? NEWLINE)*  
topLevelDecl?  
EOF  
;  
```

- **program**: a sequence of zero or more lines, each optionally containing a top‑level declaration or statement, followed by EOF.
- Blank lines are allowed because `topLevelDecl?` can be empty.
- Every physical line break is matched by **NEWLINE**, so line orientation is significant.

---

### 2. Declarations & Statements

#### 2.1 Top‑Level Declarations

**Grammar snippet**  

```antlrv4
topLevelDecl  
: classDecl  
| methodDecl  
| statement  
;  
```

- **classDecl**: defines a class, its fields and methods.
- **methodDecl**: defines a standalone (global) function or a method inside a class.
- **statement**: any executable line (variable declaration, assignment, control flow, etc.).

#### 2.2 Variable Declarations

**Grammar snippet**  

```antlrv4
varDecl  
: (type | VAR | VAL) idList (ASSIGN expr)?  
;  
idList  
: ID (COMMA ID)*  
;  
type  
: INT_TYPE | FLOAT_TYPE | DOUBLE_TYPE | BOOLEAN_TYPE | CHAR_TYPE | STRING_TYPE  
;  
```

- **type** may be any built‑in type keyword (e.g. `int`, `String`, `bool`).
- **VAR** introduces a dynamically‑typed mutable variable.
- **VAL** introduces an immutable constant (single assignment).
- You may declare multiple names at once: `var x, y = 3`.

#### 2.3 Assignment & Print

**Grammar snippet**  

```antlrv4
assign  
: idList ASSIGN expr  
;

printStmt
: PRINT expr  
;  
```

- **assign** covers both re‑assignment and initial assignment of existing names.
- **printStmt** is a built‑in for console output.

---

### 3. Control‑Flow Constructs

#### 3.1 Conditional

**Grammar snippet**  

```antlrv4
ifStmt  
: IF expr THEN COLON block  
(ELSEIF expr THEN COLON block)*  
(ELSE COLON block)?  
END  
;  
block  
: (statement? NEWLINE)*  
;  
```

- **IF … THEN** opens a branch; **ELSEIF** and **ELSE** are optional.
- Terminated by **END**.
- **block** represents an indented group of statements (you may leave lines blank).

#### 3.2 Loops

**While**  

```antlrv4
whileStmt  
: WHILE expr DO COLON block END  
;  
```

**For**  

```antlrv4 
forStmt  
: FOR (varDecl | assign) TO expr (STEP assign)? DO COLON block END  
;  
```

**Foreach**  

```antlrv4
foreachStmt  
: FOREACH ID IN expr DO COLON block END  
;  
```

- **while** loops test before each iteration.
- **for** loops support an initial declaration or assignment, an upper bound, an optional `step`, then a body.
- **foreach** iterates over lists.

---

### 4. Expressions

Operator precedence (highest → lowest):

1. **Unary**: `!` (NOT), `-` (negation)
2. **Multiplicative**: `*` `/` `%`
3. **Additive**: `+` `-`
4. **Relational**: `<` `>` `<=` `>=` `==` `!=`
5. **Logical**: `&&`/`and`, `||`/`or`, `^`/`xor`
6. **Primary** values

**Grammar snippet**  
```antlrv4
expr  
: NOT expr                      // unary NOT  
| SUB expr                      // unary minus  
| expr op=(MUL|DIV|MOD) expr    // *,/, %  
| expr op=(ADD|SUB) expr        // +, -  
| expr op=(LT|GT|LE|GE|EQ|NE) expr  // comparisons  
| expr op=(AND|OR|XOR) expr     // boolean ops  
| primary                       // literals, names, calls  
;  
```

---

### 5. Primary & Literals

**Grammar snippet**  
```antlrv4
primary  
: NEW ID LPAREN RPAREN             # NewExpr  
| ID LPAREN argList? RPAREN        # CallExprPrimary  
| primary DOT ID LPAREN argList? RPAREN  # DotCallExpr  
| INT                              # IntLiteral  
| FLOAT_LIT                        # FloatLiteral  
| DOUBLE_LIT                       # DoubleLiteral  
| STRING                           # StringLiteral  
| CHAR                             # CharLiteral  
| BOOL_LIT                         # BoolLiteral  
| ID                               # VarReference  
| LPAREN expr RPAREN               # ParenExpr  
| listLiteral                      # ListExpr  
;

listLiteral
: '[' (expr (COMMA expr)*)? ']'  
;  
argList
: expr (COMMA expr)*  
;  
```

- **NEW ID()** constructs a new object.
- **CallExprPrimary** handles global function calls.
- **DotCallExpr** handles method calls on instances.
- **listLiteral** builds a `List<Object>`.

---

### 6. Lexer Rules Highlights

**Grammar snippet**  
```antlrv4
NEWLINE      : '\r'? '\n' ;  
WS           : [ \t]+ -> skip ;  
LINE_COMMENT : '//' ~[\r\n]* -> skip ;  
BLOCK_COMMENT: '/*' .*? '*/' -> skip ;

// Keywords  
IF     : 'if' ;  FOR    : 'for' ;  VAR : 'var' ;  VAL : 'val' ;  …

// Operators/punctuation  
ASSIGN : '=' ;  LT : '<' ;  EQ : '==' ;  AND : '&&' | 'and' ;  OR : '||' | 'or' ;  …

// Literals  
INT       : [0-9]+ ;  
FLOAT_LIT : [0-9]+ '.' [0-9]+ [fF] ;  
STRING    : '"' (~["\\\r\n] | '\\' .)* '"' ;  
BOOL_LIT  : 'true' | 'false' ;  
ID        : [a-zA-Z_] [a-zA-Z_0-9]* ;  
```

---

## 📚 Examples

#### Variable Mutability & Type‑Safety

```js
var any = 10
any = "now a String!"    // OK: var is dynamic
int n = 5
// n = "oops"          // ERROR: type mismatch
val PI = 3.14
// PI = 3.0            // ERROR: reassign val
```

#### Boolean Operators

```js
Copy
Edit
var a = true and false
var b = true xor true
var c = a or b
var d = !a && !b
print a  // false
print b  // false
print c  // false
print d  // true
```

#### Classes and Methods

```js
class Counter:
    var count = 0
    method inc(): 
        count = count + 1 
    end
    method get(): 
        return count 
    end
end

var c = new Counter()
c.inc()
print c.get()   // 1
```

#### Recursive and Iterative Factorial 

```js
func factorial(n):
    if n <= 1 then: 
        return 1 
    end
    return n * factorial(n - 1)
end

func factLoop(n):
    var r = 1
    for i = 1 to n do: 
        r = r * i 
    end
    return r
end

print factorial(5)  // 120
print factLoop(5)   // 120
```

### Lists & foreach

```js
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

```js
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
