grammar MinJ;

options { language = Java; }

@header {
    package com.conava;
}

// --- Parser rules ---
program
    : (statement NEWLINE*)* EOF
    ;

statement
    : varDecl
    | assign
    | printStmt
    ;

varDecl
    : type ID ('=' expr)?
    ;

type
    : 'int'
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
    | STRING
    | ID
    ;

// --- Lexer rules ---
NEWLINE
    : [\r\n]+
    ;

WS
    : [ \t]+ -> skip
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
