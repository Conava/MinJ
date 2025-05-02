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
    | ifStmt
    | whileStmt
    | forStmt
    ;

varDecl
    : (type | VAR | VAL) ID ('=' expr)?
    ;

type
    : 'int' | 'float' | 'double' | 'boolean' | 'char' | 'String'
    ;

assign
    : ID ASSIGN expr
    ;

printStmt
    : 'print' expr
    ;

ifStmt
    : 'if' expr 'then' ':' block
      ( 'elseif' expr 'then' ':' block )*
      ( 'else' ':' block )?
      END
    ;

whileStmt
    : 'while' expr 'do' ':' block END
    ;

forStmt
    : 'for'  (varDecl | assign) 'to' expr 'step' assign 'do' ':' block END
    ;

block
    : statement*
    ;

// Expression with precedence:
// 1. *, /
// 2. +, -
// 3. <, >, <=, >=, ==, !=
// 4. primary (literals, IDs, parenthesis)
expr
    : expr op=('*'|'/') expr
    | expr op=('+'|'-') expr
    | expr op=(LT | GT | LE | GE | EQ | NE) expr
    | primary
    ;

primary
    : INT
    | FLOAT_LIT
    | DOUBLE_LIT
    | STRING
    | CHAR
    | BOOL_LIT
    | ID
    | LPAREN expr RPAREN
    ;

// --- Lexer rules ---

// skip whitespace & comments
WS             : [ \t\r\n]+           -> skip ;
LINE_COMMENT   : '//' ~[\r\n]*        -> skip ;
HASH_COMMENT   : '#'  ~[\r\n]*        -> skip ;
BLOCK_COMMENT  : '/*' .*? '*/'        -> skip ;

// keywords
END            : 'end' ;
VAR            : 'var' ;
VAL            : 'val' ;

// operators & punctuation
ASSIGN         : '=' ;
LT             : '<' ;
GT             : '>' ;
LE             : '<=' ;
GE             : '>=' ;
EQ             : '==' ;
NE             : '!=' ;
LPAREN         : '(' ;
RPAREN         : ')' ;
COLON          : ':' ;

// literals & identifiers
BOOL_LIT       : 'true' | 'false' ;
CHAR           : '\'' (~['\r\n])* '\'' ;
FLOAT_LIT      : [0-9]+ '.' [0-9]+ [fF] ;
DOUBLE_LIT     : [0-9]+ '.' [0-9]+ ;
INT            : [0-9]+ ;
STRING         : '"' (~["\r\n])* '"' ;
ID             : [a-zA-Z_][a-zA-Z_0-9]* ;