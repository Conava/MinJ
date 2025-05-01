grammar MinJ;

options { language = Java; }

@header {
    package com.conava;
}

// --- Parser rules ---
program
    : stmt* EOF
    ;

stmt
    : 'print' STRING
    ;

// --- Lexer rules ---
NUMBER
    : [0-9]+
    ;

ID
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

STRING
    : '"' (~["\\] | '\\' .)* '"'
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
