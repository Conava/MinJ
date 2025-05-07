grammar MinJ;

options { language = Java; }

@header {
    package com.conava;
}

// === Parser Rules ===

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
    : ((fieldDecl | methodDecl | statement)? NEWLINE)*
    ;

fieldDecl
    : varDecl
    ;

methodDecl
    :(METHOD | FUNC) ID LPAREN paramList? RPAREN COLON
    block
    END
    ;

paramList
    : ID (COMMA ID)*
    ;

returnStmt
    : RETURN exprList
    ;

statement
    : varDecl
    | assign
    | printStmt
    | ifStmt
    | whileStmt
    | forStmt
    | foreachStmt
    | returnStmt
    | exprStmt
    ;

exprStmt
    : expr
    ;

varDecl
    : (type | VAR | VAL) idList (ASSIGN expr)?
    ;

idList
    : ID (COMMA ID)*
    ;

type
    : INT_TYPE | FLOAT_TYPE | DOUBLE_TYPE | BOOLEAN_TYPE | CHAR_TYPE | STRING_TYPE
    ;

assign
    : idList ASSIGN expr
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

exprList
    : expr (COMMA expr)*
    ;

// Expression with precedence:
// 1. *, /
// 2. +, -
// 3. <, >, <=, >=, ==, !=
// 4. primary (literals, IDs, parenthesis)
expr
    : NOT expr
    | SUB expr
    | expr op=( MUL | DIV | MOD) expr
    | expr op=( ADD | SUB ) expr
    | expr op=( LT | GT | LE | GE | EQ | NE ) expr
    | expr op=( AND | OR | XOR) expr
    | primary
    ;

listLiteral
    : '[' (expr (COMMA expr)*)? ']'
    ;

argList
    : expr (COMMA expr)*
    ;

primary
    : NEW ID LPAREN RPAREN                                  # NewExpr
    | ID LPAREN argList? RPAREN                             # CallExprPrimary
    | primary DOT ID LPAREN argList? RPAREN                 # DotCallExpr
    | INT                                                   # IntLiteral
    | FLOAT_LIT                                             # FloatLiteral
    | DOUBLE_LIT                                            # DoubleLiteral
    | STRING                                                # StringLiteral
    | CHAR                                                  # CharLiteral
    | BOOL_LIT                                              # BoolLiteral
    | ID                                                    # VarReference
    | LPAREN expr RPAREN                                    # ParenExpr
    | listLiteral                                           # ListExpr
    ;

// === Lexer Rules ===

NEWLINE      : '\r'? '\n' ;
WS           : [ \t]+ -> skip ;

// Comments
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
HASH_COMMENT : '#'  ~[\r\n]* -> skip ;
BLOCK_COMMENT: '/*' .*? '*/' -> skip ;

RETURN      : 'return' ;
CLASS       : 'class' ;
METHOD      : 'method' ;
FUNC        : 'func' ;
PRINT       : 'print' ;
IF          : 'if' ;
THEN        : 'then' ;
ELSEIF      : 'elseif' ;
ELSE        : 'else' ;
WHILE       : 'while' ;
FOR         : 'for' ;
FOREACH     : 'foreach' ;
IN          : 'in' ;
TO          : 'to' ;
STEP        : 'step' ;
DO          : 'do' ;
END         : 'end' ;
VAR         : 'var' ;
VAL         : 'val' ;

// Types
INT_TYPE        : 'int' | 'integer' | 'Int' | 'Integer' ;
FLOAT_TYPE      : 'float' | 'Float' ;
DOUBLE_TYPE     : 'double' | 'Double' ;
BOOLEAN_TYPE    : 'boolean' | 'Boolean' | 'bool' | 'Bool' ;
CHAR_TYPE       : 'char' | 'Char' ;
STRING_TYPE     : 'String' | 'string' ;

// Operators & Punctuation
ASSIGN    : '=' ;
LT        : '<' ;
GT        : '>' ;
LE        : '<=' ;
GE        : '>=' ;
EQ        : '==' ;
NE        : '!=' ;
MOD       : '%' ;
ADD       : '+' ;
SUB       : '-' ;
MUL       : '*' ;
DIV       : '/' ;
AND       : '&&' | 'AND'| 'and' ;
OR        : '||' | 'OR' | 'or' ;
XOR       : '^' | 'XOR' | 'xor' ;
NOT       : '!' | 'NOT' | 'not' ;
LPAREN    : '(' ;
RPAREN    : ')' ;
LBRACK    : '[' ;
RBRACK    : ']' ;
COMMA     : ',' ;
COLON     : ':' ;
NEW       : 'new' ;
DOT       : '.' ;

// Literals & Identifier
BOOL_LIT  : 'true' | 'false' ;
CHAR      : '\'' (~['\r\n\\] | '\\' .) '\'' ;
STRING    : '"' (~["\r\n\\] | '\\' .)* '"' ;
FLOAT_LIT : [0-9]+ '.' [0-9]+ [fF] ;
DOUBLE_LIT: [0-9]+ '.' [0-9]+ ([eE][+-]?[0-9]+)? ;
INT       : [0-9]+ ;
ID        : [a-zA-Z_] [a-zA-Z_0-9]* ;