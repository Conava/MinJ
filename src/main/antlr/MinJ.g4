grammar MinJ;

options { language = Java; }

@header {
    package com.conava;
}

// The only two parser rules:
program
    : stmt* EOF
    ;

stmt
    : 'print' STRING ';'?    // a print statement, semicolon optional
    ;

// --- Lexer rules ---
STRING
    : '"' (~["\\] | '\\' .)* '"'
    ;

WS
    : [ \t\r\n]+ -> skip
    ;
