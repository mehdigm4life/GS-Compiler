#ifndef PAWN_H_INCLUDED
#define PAWN_H_INCLUDED

#include <stdint.h>
#include <stddef.h>

#define MAX_OPERANDS 3
#define MAX_PARAMS 8
#define MAX_LINELEN 512
#define MAX_FUNCTIONS 4096
#define MAX_VARIABLES 65536
#define MAX_STRINGS 4096
#define MAX_TAGS 256
#define MAX_INCLUDE_DEPTH 32
#define MAX_ERRORS 128
#define MAX_MACROS 4096
#define MAX_ENUMS 512
#define MAX_DEFINES 4096

#define PAWN_COMPILER_VERSION "3.10.10"
#define PAWN_COMPILER_NAME "GS Pawn Compiler"

/* Token types */
enum {
    TOK_SYMBOL,
    TOK_NUMBER,
    TOK_STRING,
    TOK_KEYWORD,
    TOK_OPERATOR,
    TOK_NEWLINE,
    TOK_EOF,
    TOK_PREPROC,
};

/* Symbol kinds */
enum {
    KIND_VARIABLE,
    KIND_FUNCTION,
    KIND_TAG,
    KIND_CONSTANT,
    KIND_ENUM,
    KIND_DEFINE,
    KIND_ARRAY,
    KIND_REFERENCE,
};

/* Symbol flags */
#define SYMBOL_PUBLIC   (1<<0)
#define SYMBOL_STOCK    (1<<1)
#define SYMBOL_STATIC   (1<<2)
#define SYMBOL_NATIVE   (1<<3)
#define SYMBOL_CONST    (1<<4)
#define SYMBOL_NEW      (1<<5)

#endif
