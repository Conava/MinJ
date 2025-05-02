grammar MinJ;

options { language = Java; }

@header {
    package com.conava;
}

// --- Parser rules ---

program
    : statement* EOF
    ;

statement
    : varDecl
    | assign
    | printStmt
    ;

varDecl
    : (type | VAR | VAL) ID ('=' expr)?
    ;

type
    : 'int'
    | 'float'
    | 'double'
    | 'boolean'
    | 'char'
    | 'String'
    ;

assign
    : ID '=' expr
    ;

printStmt
    : 'print' expr
    ;

expr
    : INT
    | FLOAT_LIT
    | DOUBLE_LIT
    | STRING
    | CHAR
    | BOOL_LIT
    | ID
    ;

// --- Lexer rules ---

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

HASH_COMMENT
    : '#' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

VAR
    : 'var'
    ;

VAL
    : 'val'
    ;

BOOL_LIT
    : 'true'
    | 'false'
    ;

CHAR
    : '\'' (~['\r\n])* '\''
    ;

FLOAT_LIT
    : [0-9]+ '.' [0-9]+ [fF]
    ;

DOUBLE_LIT
    : [0-9]+ '.' [0-9]+
    ;

INT
    : [0-9]+
    ;

STRING
    : '"' (~["\r\n])* '"'
    ;

ID
    : [a-zA-Z_][a-zA-Z_0-9]*
    ;
