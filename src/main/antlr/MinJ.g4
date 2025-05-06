grammar MinJ;

options { language = Java; }

@header {
    package com.conava;
}

// === Parser Rules (newline-separated, blank lines allowed) ===
program
    : (topLevelDecl? NEWLINE)*
    topLevelDecl?
    EOF
    ;

topLevelDecl
    : classDecl
    | methodDecl
    | statement
    ;

classDecl
    : CLASS ID COLON NEWLINE
    classBody
    END
    ;

classBody
    : ((statement | methodDecl)? NEWLINE)*
    ;

methodDecl
    :(METHOD | FUNC) ID LPAREN paramList? RPAREN COLON
    block
    END
    ;

paramList
    : ID (COMMA ID)*
    ;

statement
    : varDecl
    | assign
    | printStmt
    | ifStmt
    | whileStmt
    | forStmt
    | foreachStmt
    | callExpr
    ;

varDecl
    : (type | VAR | VAL) ID (ASSIGN expr)?
    ;

type
    : INT_TYPE | FLOAT_TYPE | DOUBLE_TYPE | BOOLEAN_TYPE | CHAR_TYPE | STRING_TYPE
    ;

assign
    : ID ASSIGN expr
    ;

printStmt
    : PRINT expr
    ;


ifStmt
    : IF expr THEN COLON block
    (ELSEIF expr THEN COLON block)*
    (ELSE COLON block)? END
    ;

whileStmt
    : WHILE expr DO COLON block END
    ;

forStmt
    : FOR  (varDecl | assign) TO expr (STEP assign)? DO COLON block END
    ;

foreachStmt
    : FOREACH ID IN expr DO COLON block END
    ;

block
    : (statement? NEWLINE)*
    ;

// Expression with precedence:
// 1. *, /
// 2. +, -
// 3. <, >, <=, >=, ==, !=
// 4. primary (literals, IDs, parenthesis)
expr
    : expr op=('*'|'/') expr
    | expr op=MOD expr
    | expr op=('+'|'-') expr
    | expr op=(LT | GT | LE | GE | EQ | NE) expr
    | primary
    ;

listLiteral
    : '[' (expr (',' expr)*)? ']'
    ;

argList
    : expr (COMMA expr)*
    ;

callExpr
    : ID LPAREN argList? RPAREN
    ;

primary
    : callExpr
    | INT
    | FLOAT_LIT
    | DOUBLE_LIT
    | STRING
    | CHAR
    | BOOL_LIT
    | ID
    | LPAREN expr RPAREN
    | listLiteral
    ;

// === Lexer Rules ===

NEWLINE      : '\r'? '\n' ;
WS           : [ \t]+ -> skip ;

// Comments
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
HASH_COMMENT : '#'  ~[\r\n]* -> skip ;
BLOCK_COMMENT: '/*' .*? '*/' -> skip ;

CLASS     : 'class' ;
METHOD    : 'method' ;
FUNC      : 'func' ;
PRINT     : 'print' ;
IF        : 'if' ;
THEN      : 'then' ;
ELSEIF    : 'elseif' ;
ELSE      : 'else' ;
WHILE     : 'while' ;
FOR       : 'for' ;
FOREACH   : 'foreach' ;
IN        : 'in' ;
TO        : 'to' ;
STEP      : 'step' ;
DO        : 'do' ;
END       : 'end' ;
VAR       : 'var' ;
VAL       : 'val' ;

// Types
INT_TYPE     : 'int' ;
FLOAT_TYPE   : 'float' ;
DOUBLE_TYPE  : 'double' ;
BOOLEAN_TYPE : 'boolean' ;
CHAR_TYPE    : 'char' ;
STRING_TYPE  : 'String' ;

// Operators & Punctuation
ASSIGN    : '=' ;
LT        : '<' ;
GT        : '>' ;
LE        : '<=' ;
GE        : '>=' ;
EQ        : '==' ;
NE        : '!=' ;
MOD       : '%' ;
LPAREN    : '(' ;
RPAREN    : ')' ;
LBRACK    : '[' ;
RBRACK    : ']' ;
COMMA     : ',' ;
COLON     : ':' ;

// Literals & Identifier
BOOL_LIT  : 'true' | 'false' ;
CHAR      : '\'' (~['\r\n\\] | '\\' .) '\'' ;
STRING    : '"' (~["\r\n\\] | '\\' .)* '"' ;
FLOAT_LIT : [0-9]+ '.' [0-9]+ [fF] ;
DOUBLE_LIT: [0-9]+ '.' [0-9]+ ([eE][+-]?[0-9]+)? ;
INT       : [0-9]+ ;
ID        : [a-zA-Z_] [a-zA-Z_0-9]* ;