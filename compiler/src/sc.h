#ifndef SC_H_INCLUDED
#define SC_H_INCLUDED

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <ctype.h>
#include <setjmp.h>
#include "amx.h"
#include "pawn.h"

#define MAX_ERR_MSG 255
#define MAX_LEXRET 512

/* Error severity */
#define SEV_NONE     0
#define SEV_WARNING  1
#define SEV_ERROR    2
#define SEV_FATAL    3

/* Operand types */
#define OP_NONE   0
#define OP_CONST  1
#define OP_STACK  2
#define OP_GLOBAL 3
#define OP_LOCAL  4
#define OP_REF    5

/* Instruction set */
enum opcodes {
    OP_NOP, OP_LOAD_PRI, OP_LOAD_ALT, OP_LOAD_S_PRI, OP_LOAD_S_ALT,
    OP_LREF_PRI, OP_LREF_ALT, OP_LREF_S_PRI, OP_LREF_S_ALT,
    OP_LOAD_I, OP_LOAD_BOTH, OP_CONST_PRI, OP_CONST_ALT,
    OP_ADDR_PRI, OP_ADDR_ALT, OP_STOR_PRI, OP_STOR_ALT,
    OP_STOR_S_PRI, OP_STOR_S_ALT, OP_SREF_PRI, OP_SREF_ALT,
    OP_SREF_S_PRI, OP_SREF_S_ALT, OP_STOR_I, OP_STOR_BOTH,
    OP_ADD_C, OP_SMUL, OP_ZERO_PRI, OP_ZERO_ALT, OP_NEG,
    OP_PUSH, OP_PUSH_C, OP_PUSH_S, OP_POP, OP_STACK,
    OP_HEAP, OP_PROC, OP_RETN, OP_RETN_PROC, OP_LOAD_S_BOTH,
    OP_SHR, OP_SHL, OP_JSLEEP, OP_JUMP, OP_JREL, OP_JZER,
    OP_JNZ, OP_CALL, OP_SWITCH, OP_SWAP_PRI, OP_SWAP_ALT,
    OP_SWAP_STACK, OP_FILE, OP_LINE, OP_SYMBOL, OP_SCOPE,
    OP_BREAK, OP_FFIND, OP_FSUB, OP_FLINE, OP_SYSREQ_D,
    OP_SYSREQ_N, OP_PUSH_PRI, OP_PUSH_ALT, OP_PUSH_R,
    OP_PUSH_PRI_C, OP_PUSH_ALT_C, OP_PUSH_R_C, OP_POP_PRI,
    OP_POP_ALT, OP_POP_R, OP_ADD, OP_SUB, OP_SUB_ALT,
    OP_AND, OP_OR, OP_XOR, OP_NOT, OP_NEG_PRI,
    OP_EQ, OP_NEQ, OP_LESS, OP_LEQ, OP_GRTR, OP_GEQ,
    OP_EQ_C, OP_NEQ_C, OP_LESS_C, OP_LEQ_C, OP_GRTR_C,
    OP_GEQ_C, OP_INC, OP_DEC, OP_INC_PRI, OP_DEC_PRI,
    OP_MOVS, OP_FILL, OP_SCASE, OP_TCASE, OP_OCASE,
    OP_SWAP, OP_BOUNDS, OP_ADDRESS, OP_CONST, OP_PUSH_S_PRI,
    OP_PUSH_S_ALT, OP_PUSH_C_PRI, OP_PUSH_C_ALT,
    OP_DECI, OP_CTRL, OP_CALLDIRECT,
    NUM_OPCODES
};

/* Expression types */
#define EXPR_NONE     0
#define EXPR_PRI      1
#define EXPR_ALT      2
#define EXPR_STACK    3
#define EXPR_CONST    4
#define EXPR_LVALUE   5

/* Stages */
enum {
    STAGE_NONE,
    STAGE_PARSE,
    STAGE_CODE,
    STAGE_EMIT,
};

/* Forward declarations */
typedef struct symbol_struct {
    char name[sNAMEMAX + 1];
    int kind;
    int flags;
    int tag;
    int addr;
    int size;
    int dim_count;
    int dims[8];
    int vclass;
    int functag;
    int userdata;
    struct symbol_struct *next;
    struct symbol_struct *parent;
} symbol;

typedef struct value_tag {
    int tag;
    cell constval;
    int ident;
    int idx;
    int addr;
} value;

typedef struct {
    char *code;
    int maxlength;
    int curlength;
    int stage;
    long *lfile;
    long *lline;
    int num_file;
    int num_line;
    int lastfile;
    int lastline;
} codeblock;

/* Global variables (declared extern in .h, defined in scvars.c) */
extern char g_infile[sFNAME + 1];
extern char g_outfile[sFNAME + 1];
extern char g_error_msg[MAX_ERR_MSG + 1];
extern int g_errors;
extern int g_warnings;
extern int g_total_errors;
extern int g_total_warnings;
extern int g_quiet;
extern int g_debug;
extern int g_verbose;
extern int g_showstats;
extern int g_compact;
extern int g_require_semicolon;
extern int g_tabsize;
extern char *g_inclist[MAX_INCLUDE_DEPTH];
extern int g_numincludes;
extern char g_includedir[512];
extern jmp_buf g_errbuf;

/* Function prototypes */
int pc_compile(const char *infile, const char *outfile, const char *includes[], int num_includes, char *error_buf, int error_buf_size);
void pc_error(int severity, const char *fmt, ...);
void pc_warning(const char *fmt, ...);
void pc_printf(const char *fmt, ...);
void pc_printf_err(const char *fmt, ...);

/* Lexer */
int pc_lex(char *dest, int maxlen);
void pc_lexinit(FILE *fp, const char *filename);
int pc_lexread(char *dest, int maxlen, int *toktype);
void pc_lexpush(int tok, const char *str);
int pc_lexpeek(void);

/* Parser */
int pc_parse(void);
int pc_parse_expr(value *val);
int pc_parse_statement(void);
int pc_parse_block(void);
int pc_parse_ifdef(void);

/* Code generator */
void pc_emit(int opcode, ...);
void pc_emit_block(int opcode);
void pc_emit_const(cell val);
void pc_emit_addr(int vclass, int addr);
void pc_emit_stack(int offset);
void pc_write_amx(const char *filename);

/* Symbol table */
symbol *pc_find_symbol(const char *name);
symbol *pc_add_symbol(const char *name, int kind, int tag, int vclass);
symbol *pc_add_function(const char *name, int tag, int functag);
int pc_find_tag(const char *name);
int pc_add_tag(const char *name, int parent);
int pc_tagof(const char *name);

/* String utilities */
char *pc_strlwr(char *str);
char *pc_strupr(char *str);
int pc_strnicmp(const char *s1, const char *s2, int n);
char *pc_strmid(char *dest, const char *src, int start, int len);

/* List utilities */
typedef struct list_node {
    void *data;
    struct list_node *next;
} list_node;

list_node *pc_list_add(list_node **head, void *data);
void pc_list_free(list_node *head);
int pc_list_count(list_node *head);

/* Memory file */
typedef struct {
    char *buffer;
    long length;
    long pos;
    char name[sFNAME + 1];
} memfile;

memfile *pc_memfile_open(const char *name, const char *source);
int pc_memfile_read(memfile *mf, char *dest, int maxlen);
void pc_memfile_close(memfile *mf);

/* Language file */
int pc_lang_load(const char *filename);
const char *pc_lang_msg(int number);

#endif
