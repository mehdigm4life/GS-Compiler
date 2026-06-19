#include "sc.h"
#include <string.h>
#include <ctype.h>
#include <stdlib.h>
#include <setjmp.h>
#include <errno.h>
#include <limits.h>

/* Forward declarations of internal functions */
static int pc_compile_file(const char *infile, const char *outfile);
static int pc_parse_file(void);
static void pc_emit_amx_header(FILE *fp);
static void pc_emit_code(FILE *fp);

/* Include file handling */
static FILE *pc_open_include(const char *name, char *fullpath, int maxpath);

/* Canonicalize a path: resolve . and .. components in-place */
static void pc_canonicalize_path(char *path) {
    if (path == NULL || path[0] == '\0') return;
    char *parts[256];
    int np = 0;
    char *p = path;
    char *start = path;
    /* If the path is absolute, preserve the leading / */
    int absolute = 0;
    if (path[0] == '/') {
        absolute = 1;
        p++;
    }
    /* Split on '/' manually (avoid strtok reentrancy issues) */
    while (*p) {
        if (*p == '/') {
            *p = '\0';
            char *seg = start;
            if (seg[0] == '\0' || (seg[0] == '.' && seg[1] == '\0')) {
                /* skip empty or . */
            } else if (seg[0] == '.' && seg[1] == '.' && seg[2] == '\0') {
                if (np > 0) np--;
            } else {
                if (np < 256) parts[np++] = seg;
            }
            p++;
            start = p;
        } else {
            p++;
        }
    }
    /* Last segment */
    if (start[0] != '\0') {
        if (start[0] == '.' && start[1] == '\0') {
            /* skip */
        } else if (start[0] == '.' && start[1] == '.' && start[2] == '\0') {
            if (np > 0) np--;
        } else {
            if (np < 256) parts[np++] = start;
        }
    }
    /* Rebuild */
    char *dst = path;
    if (absolute) *dst++ = '/';
    for (int i = 0; i < np; i++) {
        if (i > 0) *dst++ = '/';
        int len = strlen(parts[i]);
        memmove(dst, parts[i], len);
        dst += len;
    }
    *dst = '\0';
}

/* Lexer state */
typedef struct {
    FILE *fp;
    memfile *mf;
    char filename[sFNAME + 1];
    int line;
    int col;
    int saved_token;
    char saved_str[MAX_LEXRET + 1];
    int ifdef_active;
    int ifdef_skip;
    int include_depth;
} lex_state;

#define MAX_LEX_STACK 32
static lex_state lex_stack[MAX_LEX_STACK];
static int lex_stack_ptr = -1;
static lex_state g_lex;

/* Token string buffer */
static char g_token[MAX_LEXRET + 1];

/* Duplicate include tracking — files already included (by full path) */
#define MAX_INCLUDED_FILES 128
static char g_included_files[MAX_INCLUDED_FILES][512];
static int g_num_included_files = 0;

/* Preprocessor condition stack for #if/#ifdef/#ifndef/#else/#endif */
#define MAX_COND_DEPTH 64
static int g_cond_stack[MAX_COND_DEPTH];  /* 0 = active, 1 = skipping */
static int g_cond_depth = 0;

/* Keyword table */
typedef struct {
    const char *name;
    int token;
} keyword;

static const keyword keywords[] = {
    {"assert",     TOK_KEYWORD},
    {"break",      TOK_KEYWORD},
    {"case",       TOK_KEYWORD},
    {"const",      TOK_KEYWORD},
    {"continue",   TOK_KEYWORD},
    {"default",    TOK_KEYWORD},
    {"do",         TOK_KEYWORD},
    {"else",       TOK_KEYWORD},
    {"enum",       TOK_KEYWORD},
    {"for",        TOK_KEYWORD},
    {"forward",    TOK_KEYWORD},
    {"goto",       TOK_KEYWORD},
    {"if",         TOK_KEYWORD},
    {"native",     TOK_KEYWORD},
    {"new",        TOK_KEYWORD},
    {"operator",   TOK_KEYWORD},
    {"public",     TOK_KEYWORD},
    {"return",     TOK_KEYWORD},
    {"sizeof",     TOK_KEYWORD},
    {"state",      TOK_KEYWORD},
    {"static",     TOK_KEYWORD},
    {"stock",      TOK_KEYWORD},
    {"switch",     TOK_KEYWORD},
    {"tagof",      TOK_KEYWORD},
    {"while",      TOK_KEYWORD},
    {"defined",    TOK_KEYWORD},
    {"elseif",     TOK_KEYWORD},
    {"endif",      TOK_KEYWORD},
    {"ifdef",      TOK_KEYWORD},
    {"ifndef",     TOK_KEYWORD},
    {"include",    TOK_KEYWORD},
    {"tryinclude", TOK_KEYWORD},
    {"pragma",     TOK_KEYWORD},
    {"emit",       TOK_KEYWORD},
    {"exit",       TOK_KEYWORD},
    {"sleep",      TOK_KEYWORD},
    {NULL, 0}
};

static int is_keyword(const char *s) {
    int i;
    for (i = 0; keywords[i].name != NULL; i++) {
        if (strcmp(s, keywords[i].name) == 0)
            return keywords[i].token;
    }
    return 0;
}

/* Symbol management */
typedef struct tag_entry {
    char name[sNAMEMAX + 1];
    int id;
    int parent;
    struct tag_entry *next;
} tag_entry;

static tag_entry *tag_table = NULL;
static int next_tag_id = 1;

static symbol *sym_table_hash[256];
static int sym_count = 0;

static unsigned int hash_str(const char *str) {
    unsigned int h = 0;
    while (*str) {
        h = h * 31 + (unsigned char)*str++;
    }
    return h & 0xFF;
}

symbol *pc_find_symbol(const char *name) {
    unsigned int h = hash_str(name);
    symbol *sym = sym_table_hash[h];
    while (sym != NULL) {
        if (strcmp(sym->name, name) == 0)
            return sym;
        sym = sym->next;
    }
    return NULL;
}

symbol *pc_add_symbol(const char *name, int kind, int tag, int vclass) {
    if (pc_find_symbol(name)) {
        pc_error(SEV_ERROR, "Symbol is already defined: %s", name);
        return NULL;
    }

    symbol *sym = (symbol *)calloc(1, sizeof(symbol));
    if (sym == NULL) {
        pc_error(SEV_FATAL, "Out of memory");
        return NULL;
    }

    strncpy(sym->name, name, sNAMEMAX);
    sym->name[sNAMEMAX] = '\0';
    sym->kind = kind;
    sym->tag = tag;
    sym->vclass = vclass;
    sym->addr = g_code_idx;
    sym->size = 1;

    unsigned int h = hash_str(name);
    sym->next = sym_table_hash[h];
    sym_table_hash[h] = sym;
    sym_count++;

    return sym;
}

symbol *pc_add_function(const char *name, int tag, int functag) {
    symbol *sym = pc_add_symbol(name, KIND_FUNCTION, tag, 0);
    if (sym != NULL) {
        sym->functag = functag;
    }
    return sym;
}

int pc_find_tag(const char *name) {
    tag_entry *t = tag_table;
    while (t != NULL) {
        if (strcmp(t->name, name) == 0)
            return t->id;
        t = t->next;
    }
    return 0;
}

int pc_add_tag(const char *name, int parent) {
    if (pc_find_tag(name) != 0)
        return pc_find_tag(name);

    tag_entry *t = (tag_entry *)calloc(1, sizeof(tag_entry));
    if (t == NULL) return 0;

    strncpy(t->name, name, sNAMEMAX);
    t->name[sNAMEMAX] = '\0';
    t->id = next_tag_id++;
    t->parent = parent;
    t->next = tag_table;
    tag_table = t;
    return t->id;
}

int pc_tagof(const char *name) {
    (void)name;
    return 0;
}

/* Code block management */
static void code_init(void) {
    memset(&g_codeblock, 0, sizeof(g_codeblock));
    g_codeblock.maxlength = 1024 * 1024;
    g_codeblock.code = (char *)malloc((size_t)g_codeblock.maxlength);
    if (g_codeblock.code != NULL)
        memset(g_codeblock.code, 0, (size_t)g_codeblock.maxlength);
    g_codeblock.stage = STAGE_NONE;

    g_codeblock.lfile = (long *)malloc(sizeof(long) * 4096);
    g_codeblock.lline = (long *)malloc(sizeof(long) * 4096);
    g_codeblock.num_file = 0;
    g_codeblock.num_line = 0;

    g_codeblock.curlength = 0;
    g_codeblock.lastfile = 0;
    g_codeblock.lastline = 0;
}

static void code_ensure(int bytes) {
    if (g_codeblock.curlength + bytes >= g_codeblock.maxlength) {
        g_codeblock.maxlength *= 2;
        char *new_code = (char *)realloc(g_codeblock.code, (size_t)g_codeblock.maxlength);
        if (new_code == NULL) {
            pc_error(SEV_FATAL, "Out of memory in code generation");
            return;
        }
        g_codeblock.code = new_code;
    }
}

void pc_emit(int opcode, ...) {
    va_list args;
    va_start(args, opcode);

    code_ensure(16);
    g_codeblock.code[g_codeblock.curlength++] = (char)(opcode & 0xFF);

    switch (opcode) {
        case OP_CONST_PRI:
        case OP_CONST_ALT:
        case OP_CONST:
        case OP_PUSH_C:
        case OP_PUSH_C_PRI:
        case OP_PUSH_C_ALT:
        case OP_JUMP:
        case OP_JREL:
        case OP_JZER:
        case OP_JNZ:
        case OP_CALL:
        case OP_CALLDIRECT:
        case OP_BOUNDS: {
            cell val = (cell)va_arg(args, int);
            code_ensure(4);
            memcpy(g_codeblock.code + g_codeblock.curlength, &val, sizeof(cell));
            g_codeblock.curlength += sizeof(cell);
            break;
        }
        case OP_PUSH_S:
        case OP_LOAD_S_PRI:
        case OP_LOAD_S_ALT:
        case OP_LOAD_S_BOTH:
        case OP_STOR_S_PRI:
        case OP_STOR_S_ALT:
        case OP_LREF_S_PRI:
        case OP_LREF_S_ALT:
        case OP_SREF_S_PRI:
        case OP_SREF_S_ALT:
        case OP_PUSH_R:
        case OP_PUSH_R_C:
        case OP_STACK: {
            cell val = (cell)va_arg(args, int);
            code_ensure(4);
            memcpy(g_codeblock.code + g_codeblock.curlength, &val, sizeof(cell));
            g_codeblock.curlength += sizeof(cell);
            break;
        }
        default:
            break;
    }
    va_end(args);
}

void pc_emit_block(int opcode) {
    code_ensure(1);
    g_codeblock.code[g_codeblock.curlength++] = (char)(opcode & 0xFF);
}

void pc_emit_const(cell val) {
    pc_emit(OP_CONST_PRI, (int)val);
}

void pc_emit_addr(int vclass, int addr) {
    (void)vclass;
    pc_emit(OP_CONST_PRI, addr);
}

void pc_emit_stack(int offset) {
    pc_emit(OP_PUSH_S, offset);
}

static void code_track_file_line(const char *filename, int line) {
    if (g_codeblock.num_file < 4096 && g_codeblock.num_line < 4096) {
        int fidx = 0;
        size_t i;
        for (i = 0; i < (size_t)g_codeblock.num_file; i++) {
            if (g_codeblock.lfile[i] == (long)(intptr_t)filename) {
                fidx = (int)i;
                break;
            }
        }
        if (fidx == g_codeblock.num_file && g_codeblock.num_file < 4096) {
            g_codeblock.lfile[g_codeblock.num_file++] = (long)(intptr_t)filename;
            fidx = g_codeblock.num_file - 1;
        }
        g_codeblock.lline[g_codeblock.num_line++] = (long)((fidx << 16) | (line & 0xFFFF));
    }
}

/* Lexer implementation */
void pc_lexinit(FILE *fp, const char *filename) {
    memset(&g_lex, 0, sizeof(g_lex));
    g_lex.fp = fp;
    strncpy(g_lex.filename, filename, sFNAME);
    g_lex.filename[sFNAME] = '\0';
    g_lex.line = 1;
    g_lex.col = 0;
    g_lex.saved_token = 0;
    g_lex.ifdef_active = 1;
    g_lex.ifdef_skip = 0;
    g_lex.include_depth = 0;

    g_line_number = 1;
    strncpy(g_input_filename, filename, sFNAME);
    g_input_filename[sFNAME] = '\0';
}

static int lex_getc(void) {
    if (g_lex.mf) {
        char c;
        if (g_lex.mf->pos < g_lex.mf->length) {
            c = g_lex.mf->buffer[g_lex.mf->pos++];
        } else {
            return EOF;
        }
        if (c == '\n') {
            g_lex.line++;
            g_lex.col = 0;
        } else {
            g_lex.col++;
        }
        return (unsigned char)c;
    }
    if (g_lex.fp == NULL)
        return EOF;
    int c = fgetc(g_lex.fp);
    if (c == '\n') {
        g_lex.line++;
        g_lex.col = 0;
    } else if (c != EOF) {
        g_lex.col++;
    }
    return c;
}

static void lex_ungetc(int c) {
    if (c == EOF) return;
    if (g_lex.mf) {
        if (g_lex.mf->pos > 0) {
            g_lex.mf->pos--;
            if (c == '\n') {
                g_lex.line--;
            }
        }
    } else if (g_lex.fp) {
        ungetc(c, g_lex.fp);
        if (c == '\n') {
            g_lex.line--;
        }
    }
}

static void lex_skip_line(void) {
    int c;
    do {
        c = lex_getc();
    } while (c != EOF && c != '\n');
}

static void lex_skip_block_comment(void) {
    int c, prev = 0;
    for (;;) {
        c = lex_getc();
        if (c == EOF) {
            pc_error(SEV_ERROR, "Unterminated comment");
            return;
        }
        if (prev == '*' && c == '/')
            return;
        prev = c;
    }
}

static int lex_read_number(char first) {
    int buf_idx = 0;
    char buf[MAX_LEXRET + 1];
    int c = first;

    while (buf_idx < MAX_LEXRET && (isdigit(c) || c == '.' || c == 'x' || c == 'X' ||
           (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        buf[buf_idx++] = (char)c;
        c = lex_getc();
    }
    if (c != EOF)
        lex_ungetc(c);
    buf[buf_idx] = '\0';
    strncpy(g_token, buf, MAX_LEXRET);
    return TOK_NUMBER;
}

static int lex_read_string(int delim) {
    int buf_idx = 0;
    int c;

    g_token[buf_idx++] = (char)delim;
    for (;;) {
        c = lex_getc();
        if (c == EOF) {
            pc_error(SEV_ERROR, "Unterminated string");
            g_token[buf_idx] = '\0';
            return TOK_STRING;
        }
        if (c == '\\') {
            int next = lex_getc();
            if (buf_idx < MAX_LEXRET)
                g_token[buf_idx++] = (char)c;
            if (buf_idx < MAX_LEXRET && next != EOF)
                g_token[buf_idx++] = (char)next;
            continue;
        }
        if (c == delim)
            break;
        if (buf_idx < MAX_LEXRET)
            g_token[buf_idx++] = (char)c;
    }
    if (buf_idx < MAX_LEXRET)
        g_token[buf_idx++] = (char)delim;
    g_token[buf_idx] = '\0';
    return TOK_STRING;
}

static int lex_read_word(char first) {
    int buf_idx = 0;
    int c = first;

    while (buf_idx < MAX_LEXRET && (isalnum(c) || c == '_')) {
        g_token[buf_idx++] = (char)c;
        c = lex_getc();
    }
    if (c != EOF)
        lex_ungetc(c);
    g_token[buf_idx] = '\0';

    if (is_keyword(g_token))
        return TOK_KEYWORD;

    return TOK_SYMBOL;
}

int pc_lex(char *dest, int maxlen) {
    int tok;
    int toktype;

    tok = pc_lexread(dest, maxlen, &toktype);
    return tok;
}

int pc_lexread(char *dest, int maxlen, int *toktype) {
    if (g_lex.saved_token) {
        int tok = g_lex.saved_token;
        strncpy(dest, g_lex.saved_str, maxlen - 1);
        if (maxlen > 0) dest[maxlen - 1] = '\0';
        g_lex.saved_token = 0;
        *toktype = tok;
        return tok;
    }

    int c;
    for (;;) {
        c = lex_getc();

        /* Skip whitespace */
        if (c == ' ' || c == '\t')
            continue;

        /* Newlines */
        if (c == '\n') {
            g_line_number = g_lex.line;
            *toktype = TOK_NEWLINE;
            if (dest) {
                dest[0] = '\n';
                if (maxlen > 1) dest[1] = '\0';
            }
            return TOK_NEWLINE;
        }

        if (c == EOF) {
            /* If we're inside an included file, pop back to the parent */
            if (lex_stack_ptr >= 0) {
                if (g_lex.fp) fclose(g_lex.fp);
                if (g_lex.mf) pc_memfile_close(g_lex.mf);
                g_lex = lex_stack[lex_stack_ptr--];
                g_line_number = g_lex.line;
                return pc_lexread(dest, maxlen, toktype);
            }
            *toktype = TOK_EOF;
            return TOK_EOF;
        }

        break;
    }

    g_line_number = g_lex.line;

    /* Skip comments */
    if (c == '/') {
        int next = lex_getc();
        if (next == '/') {
            lex_skip_line();
            return pc_lexread(dest, maxlen, toktype);
        }
        if (next == '*') {
            lex_skip_block_comment();
            return pc_lexread(dest, maxlen, toktype);
        }
        if (next != EOF)
            lex_ungetc(next);
    }

    /* Preprocessor */
    if (c == '#') {
        if (dest) {
            dest[0] = '#';
            if (maxlen > 1) dest[1] = '\0';
        }
        *toktype = TOK_PREPROC;
        return TOK_PREPROC;
    }

    /* Numbers */
    if (isdigit(c)) {
        *toktype = TOK_NUMBER;
        int idx = 0;
        if (dest && idx < maxlen - 1) dest[idx++] = (char)c;
        while ((c = lex_getc()) != EOF && (isalnum(c) || c == '.') && idx < maxlen - 1) {
            dest[idx++] = (char)c;
        }
        if (c != EOF) lex_ungetc(c);
        if (dest) dest[idx] = '\0';
        return TOK_NUMBER;
    }

    /* Strings */
    if (c == '"' || c == '\'') {
        int delim = c;
        int idx = 0;
        if (dest && idx < maxlen - 1) dest[idx++] = (char)c;
        for (;;) {
            c = lex_getc();
            if (c == EOF) break;
            if (c == '\\') {
                if (dest && idx < maxlen - 2) dest[idx++] = (char)c;
                c = lex_getc();
                if (c == EOF) break;
                if (dest && idx < maxlen - 2) dest[idx++] = (char)c;
                continue;
            }
            if (c == delim) break;
            if (dest && idx < maxlen - 1) dest[idx++] = (char)c;
        }
        if (dest && idx < maxlen - 1) dest[idx++] = (char)delim;
        if (dest) dest[idx] = '\0';
        *toktype = TOK_STRING;
        return TOK_STRING;
    }

    /* Identifiers */
    if (isalpha(c) || c == '_' || c == '@') {
        int idx = 0;
        if (dest && idx < maxlen - 1) dest[idx++] = (char)c;
        while ((c = lex_getc()) != EOF && (isalnum(c) || c == '_' || c == '@') && idx < maxlen - 1) {
            dest[idx++] = (char)c;
        }
        if (c != EOF) lex_ungetc(c);
        if (dest) dest[idx] = '\0';

        if (is_keyword(dest)) {
            *toktype = TOK_KEYWORD;
            return TOK_KEYWORD;
        }
        *toktype = TOK_SYMBOL;
        return TOK_SYMBOL;
    }

    /* Operators and punctuation */
    {
        int idx = 0;
        if (dest && idx < maxlen - 1) dest[idx++] = (char)c;

        /* Two-character operators */
        int next = lex_getc();
        if (next != EOF) {
            int combined = 0;
            if ((c == '=' && next == '=') ||
                (c == '!' && next == '=') ||
                (c == '<' && next == '=') ||
                (c == '>' && next == '=') ||
                (c == '+' && next == '+') ||
                (c == '-' && next == '-') ||
                (c == '+' && next == '=') ||
                (c == '-' && next == '=') ||
                (c == '*' && next == '=') ||
                (c == '/' && next == '=') ||
                (c == '%' && next == '=') ||
                (c == '&' && next == '&') ||
                (c == '|' && next == '|') ||
                (c == '<' && next == '<') ||
                (c == '>' && next == '>') ||
                (c == ':' && next == ':') ||
                (c == '-' && next == '>')) {
                if (dest && idx < maxlen - 1) dest[idx++] = (char)next;
                combined = 1;
            }
            if (!combined && next != EOF)
                lex_ungetc(next);
        }

        if (dest) dest[idx] = '\0';
        *toktype = TOK_OPERATOR;
        return TOK_OPERATOR;
    }
}

void pc_lexpush(int tok, const char *str) {
    g_lex.saved_token = tok;
    if (str)
        strncpy(g_lex.saved_str, str, MAX_LEXRET);
    else
        g_lex.saved_str[0] = '\0';
}

int pc_lexpeek(void) {
    if (g_lex.saved_token)
        return g_lex.saved_token;
    return 0;
}

/* Include file handling */
static FILE *pc_open_include(const char *name, char *fullpath, int maxpath) {
    FILE *fp;
    int i;

    /* Helper: try opening with a given path format */
    /* Try name.inc first, then name */
    const char *suffixes[] = {".inc", ""};
    int num_suffixes = sizeof(suffixes) / sizeof(suffixes[0]);

    /* Try the include paths */
    for (i = 0; i < g_numincludes; i++) {
        for (int s = 0; s < num_suffixes; s++) {
            snprintf(fullpath, maxpath, "%s/%s%s", g_inclist[i], name, suffixes[s]);
            fp = fopen(fullpath, "r");
            if (fp != NULL)
                return fp;
        }
    }

    /* Try the current directory */
    for (int s = 0; s < num_suffixes; s++) {
        snprintf(fullpath, maxpath, "%s%s", name, suffixes[s]);
        fp = fopen(fullpath, "r");
        if (fp != NULL)
            return fp;
    }

    /* Try the default pawno/include */
    for (int s = 0; s < num_suffixes; s++) {
        snprintf(fullpath, maxpath, "pawno/include/%s%s", name, suffixes[s]);
        fp = fopen(fullpath, "r");
        if (fp != NULL)
            return fp;
    }

    return NULL;
}

static int pc_handle_include(const char *filename) {
    if (g_lex.include_depth >= MAX_INCLUDE_DEPTH) {
        pc_error(SEV_ERROR, "Include depth exceeded");
        return 0;
    }

    char fullpath[512];
    FILE *fp = pc_open_include(filename, fullpath, sizeof(fullpath));
    if (fp == NULL) {
        pc_error(SEV_ERROR, "Cannot open include file: %s", filename);
        return 0;
    }

    /* Canonicalize path so .. / . / symlink duplicates are detected */
    pc_canonicalize_path(fullpath);

    /* Resolve symlinks (e.g., /sdcard -> /storage/emulated/0 on Android) */
    {
        char real[PATH_MAX];
        if (realpath(fullpath, real) != NULL) {
            strncpy(fullpath, real, 511);
            fullpath[511] = '\0';
        }
    }

    /* Duplicate include guard: if this file was already included, skip it */
    for (int i = 0; i < g_num_included_files; i++) {
        if (strcmp(g_included_files[i], fullpath) == 0) {
            fclose(fp);
            return 1;
        }
    }
    /* Track this file as included */
    if (g_num_included_files < MAX_INCLUDED_FILES) {
        strncpy(g_included_files[g_num_included_files], fullpath, 511);
        g_included_files[g_num_included_files][511] = '\0';
        g_num_included_files++;
    }

    /* Push current lex state */
    if (lex_stack_ptr < MAX_LEX_STACK - 1) {
        lex_stack[++lex_stack_ptr] = g_lex;
    }

    g_lex.include_depth++;
    pc_lexinit(fp, fullpath);
    return 1;
}

static int pc_handle_preproc(void) {
    char directive[64];
    int tok = pc_lexread(directive, sizeof(directive), &tok);

    if (strcmp(directive, "include") == 0) {
        char incname[256];
        int inc_idx = 0;
        int c;

        /* Skip whitespace */
        do {
            c = lex_getc();
        } while (c == ' ' || c == '\t');

        if (c == '<' || c == '"') {
            int delim = (c == '<') ? '>' : c;
            while (inc_idx < (int)sizeof(incname) - 1) {
                c = lex_getc();
                if (c == EOF) break;
                if (c == '\n') { lex_ungetc(c); break; }
                if (c == delim) break;
                incname[inc_idx++] = (char)c;
            }
        } else if (c != EOF && c != '\n') {
            incname[inc_idx++] = (char)c;
            while (inc_idx < (int)sizeof(incname) - 1) {
                c = lex_getc();
                if (c == EOF) break;
                if (c == '\n') { lex_ungetc(c); break; }
                if (c == ' ' || c == '\t') break;
                incname[inc_idx++] = (char)c;
            }
        }
        incname[inc_idx] = '\0';

        /* Convert backslashes to forward slashes (Linux/Android compatibility) */
        for (char *p = incname; *p; p++) {
            if (*p == '\\') *p = '/';
        }

        return pc_handle_include(incname);
    }

    /*****************************************************************
     * #define — actually define the macro symbol so include guards work
     *****************************************************************/
    if (strcmp(directive, "define") == 0) {
        int c;
        do { c = lex_getc(); } while (c == ' ' || c == '\t');
        if (c != EOF && c != '\n') {
            char macroname[64];
            int idx = 0;
            while (idx < 63 && (isalnum(c) || c == '_')) {
                macroname[idx++] = (char)c;
                c = lex_getc();
            }
            macroname[idx] = '\0';
            /* Skip the rest of the line */
            while (c != EOF && c != '\n') c = lex_getc();
            if (c == '\n') g_lex.line--;
            /* Register the macro as a symbol (include-guard use) */
            if (idx > 0 && pc_find_symbol(macroname) == NULL) {
                symbol *sym = (symbol *)calloc(1, sizeof(symbol));
                if (sym) {
                    strncpy(sym->name, macroname, sNAMEMAX);
                    sym->name[sNAMEMAX] = '\0';
                    sym->kind = KIND_CONSTANT;
                    sym->tag = 0;
                    sym->vclass = 0;
                    unsigned int h = hash_str(macroname);
                    sym->next = sym_table_hash[h];
                    sym_table_hash[h] = sym;
                }
            }
        }
        return 1;
    }

    /*****************************************************************
     * #endinput — return to parent file
     *****************************************************************/
    if (strcmp(directive, "endinput") == 0) {
        /* Pop back to parent file if inside an include */
        if (lex_stack_ptr >= 0) {
            if (g_lex.fp) fclose(g_lex.fp);
            if (g_lex.mf) pc_memfile_close(g_lex.mf);
            g_lex = lex_stack[lex_stack_ptr--];
            g_line_number = g_lex.line;
        }
        return 1;
    }

    /*****************************************************************
     * #if / #ifdef / #ifndef — conditional compilation
     * Keep a stack so we can handle nesting.
     *****************************************************************/
    if (strcmp(directive, "if") == 0 ||
        strcmp(directive, "ifdef") == 0 ||
        strcmp(directive, "ifndef") == 0) {

        int expect_true = 1;
        if (strcmp(directive, "ifndef") == 0)
            expect_true = 0;

        /* Read the condition: "defined X", "defined(X)", or bare symbol */
        int c;
        do { c = lex_getc(); } while (c == ' ' || c == '\t');

        /* Skip 'defined' keyword + optional '(' + optional whitespace */
        if (c == 'd') {
            char buf[16];
            int bi = 0;
            while (bi < 15 && isalpha(c)) { buf[bi++] = (char)c; c = lex_getc(); }
            buf[bi] = '\0';
            if (strcmp(buf, "defined") == 0) {
                while (c == ' ' || c == '\t') c = lex_getc();
                if (c == '(') { c = lex_getc(); while (c == ' ' || c == '\t') c = lex_getc(); }
            }
        }

        /* Read the symbol name */
        char condsym[64];
        int si = 0;
        while (si < 63 && (isalnum(c) || c == '_')) {
            condsym[si++] = (char)c;
            c = lex_getc();
        }
        condsym[si] = '\0';

        /* Skip the rest of the line (closing paren etc.) */
        while (c != EOF && c != '\n') c = lex_getc();
        if (c == '\n') g_lex.line--;

        /* Evaluate: is the symbol defined? */
        int sym_found = (pc_find_symbol(condsym) != NULL);
        int condition = expect_true ? sym_found : !sym_found;
        int should_skip = condition ? 0 : 1;

        /* Also skip if we are already inside a skipped conditional */
        if (g_cond_depth > 0 && g_cond_stack[g_cond_depth - 1])
            should_skip = 1;

        if (g_cond_depth < MAX_COND_DEPTH) {
            g_cond_stack[g_cond_depth++] = should_skip ? 1 : 0;
        }

        /* If we should skip, we must consume lines until #else/#endif */
        if (should_skip) {
            int nest = 0;
            for (;;) {
                c = lex_getc();
                if (c == EOF) break;
                if (c == '\n') { g_lex.line++; continue; }
                if (c == '#') {
                    char dir[64]; int di = 0;
                    /* read directive name */
                    c = lex_getc();
                    while (di < 63 && (isalpha(c) || c == '_')) {
                        dir[di++] = (char)c;
                        c = lex_getc();
                    }
                    dir[di] = '\0';
                    if (strcmp(dir, "if") == 0 || strcmp(dir, "ifdef") == 0 || strcmp(dir, "ifndef") == 0) {
                        nest++;
                        /* skip rest of line */
                        while (c != EOF && c != '\n') c = lex_getc();
                    } else if (strcmp(dir, "else") == 0 || strcmp(dir, "elseif") == 0) {
                        if (nest == 0) {
                            /* We hit #else while skipping — switch to active */
                            /* But only if the original #if was false (not nested skip) */
                            if (g_cond_depth > 0 && !(g_cond_depth > 1 && g_cond_stack[g_cond_depth - 2]))
                                g_cond_stack[g_cond_depth - 1] = 0;
                            /* skip rest of #else line */
                            while (c != EOF && c != '\n') c = lex_getc();
                            /* Someone else has to process the #else body next time */
                            break;
                        }
                        while (c != EOF && c != '\n') c = lex_getc();
                    } else if (strcmp(dir, "endif") == 0) {
                        if (nest == 0) {
                            if (g_cond_depth > 0) g_cond_depth--;
                            /* skip rest of line */
                            while (c != EOF && c != '\n') c = lex_getc();
                            break;
                        }
                        nest--;
                        while (c != EOF && c != '\n') c = lex_getc();
                    } else {
                        /* skip the rest of the line */
                        while (c != EOF && c != '\n') c = lex_getc();
                    }
                }
            }
        }

        return 1;
    }

    /*****************************************************************
     * #else — toggle between skip/active within current level
     *****************************************************************/
    if (strcmp(directive, "else") == 0) {
        int c;
        do { c = lex_getc(); } while (c != EOF && c != '\n');
        if (c == '\n') g_lex.line--;

        if (g_cond_depth > 0) {
            /* Check if we hit #else while skipping */
            if (g_cond_stack[g_cond_depth - 1]) {
                /* Only switch to active if the parent #if/#ifdef/#ifndef was false
                 * and we haven't already found a true branch.
                 * Since we track simple skip/not-skip, just toggle. */
                g_cond_stack[g_cond_depth - 1] = 0;
            } else {
                /* We were active — now start skipping until #endif */
                g_cond_stack[g_cond_depth - 1] = 1;
            }
        }

        /* If we are now skipping, consume lines until #endif */
        if (g_cond_depth > 0 && g_cond_stack[g_cond_depth - 1]) {
            int nest = 0;
            for (;;) {
                c = lex_getc();
                if (c == EOF) break;
                if (c == '\n') { g_lex.line++; continue; }
                if (c == '#') {
                    char dir[64]; int di = 0;
                    c = lex_getc();
                    while (di < 63 && (isalpha(c) || c == '_')) {
                        dir[di++] = (char)c;
                        c = lex_getc();
                    }
                    dir[di] = '\0';
                    if (strcmp(dir, "if") == 0 || strcmp(dir, "ifdef") == 0 || strcmp(dir, "ifndef") == 0) {
                        nest++;
                        while (c != EOF && c != '\n') c = lex_getc();
                    } else if (strcmp(dir, "else") == 0 || strcmp(dir, "elseif") == 0) {
                        if (nest == 0) {
                            /* This should not happen in well-formed code after #else,
                             * but handle it by stopping. */
                            while (c != EOF && c != '\n') c = lex_getc();
                            break;
                        }
                        while (c != EOF && c != '\n') c = lex_getc();
                    } else if (strcmp(dir, "endif") == 0) {
                        if (nest == 0) {
                            if (g_cond_depth > 0) g_cond_depth--;
                            while (c != EOF && c != '\n') c = lex_getc();
                            break;
                        }
                        nest--;
                        while (c != EOF && c != '\n') c = lex_getc();
                    } else {
                        while (c != EOF && c != '\n') c = lex_getc();
                    }
                }
            }
        }

        return 1;
    }

    /*****************************************************************
     * #elseif — evaluate condition like #if but as alternative branch
     *****************************************************************/
    if (strcmp(directive, "elseif") == 0) {
        if (g_cond_depth > 0) {
            /* If previous #if was true (we are active), this branch is irrelevant — skip */
            if (!g_cond_stack[g_cond_depth - 1]) {
                /* The first branch was already active; mark as skip and consume until #endif */
                g_cond_stack[g_cond_depth - 1] = 1;
                /* skip rest of #elseif line */
                int c;
                do { c = lex_getc(); } while (c != EOF && c != '\n');
                if (c == '\n') g_lex.line--;
                /* consume until #endif */
                int nest = 0;
                for (;;) {
                    c = lex_getc();
                    if (c == EOF) break;
                    if (c == '\n') { g_lex.line++; continue; }
                    if (c == '#') {
                        char dir[64]; int di = 0;
                        c = lex_getc();
                        while (di < 63 && (isalpha(c) || c == '_')) {
                            dir[di++] = (char)c;
                            c = lex_getc();
                        }
                        dir[di] = '\0';
                        if (strcmp(dir, "if") == 0 || strcmp(dir, "ifdef") == 0 || strcmp(dir, "ifndef") == 0) { nest++; while (c != EOF && c != '\n') c = lex_getc(); }
                        else if (strcmp(dir, "else") == 0 || strcmp(dir, "elseif") == 0) { if (nest == 0) { while (c != EOF && c != '\n') c = lex_getc(); break; } while (c != EOF && c != '\n') c = lex_getc(); }
                        else if (strcmp(dir, "endif") == 0) { if (nest == 0) { if (g_cond_depth > 0) g_cond_depth--; while (c != EOF && c != '\n') c = lex_getc(); break; } nest--; while (c != EOF && c != '\n') c = lex_getc(); }
                        else { while (c != EOF && c != '\n') c = lex_getc(); }
                    }
                }
                return 1;
            }

            /* Previous #if was false — we are in a skipped branch */
            /* Try to evaluate this #elseif condition */
            int c;
            do { c = lex_getc(); } while (c == ' ' || c == '\t');

            if (c == 'd') {
                char buf[16]; int bi = 0;
                while (bi < 15 && isalpha(c)) { buf[bi++] = (char)c; c = lex_getc(); }
                buf[bi] = '\0';
                if (strcmp(buf, "defined") == 0) {
                    while (c == ' ' || c == '\t') c = lex_getc();
                    if (c == '(') { c = lex_getc(); while (c == ' ' || c == '\t') c = lex_getc(); }
                }
            }
            char condsym[64]; int si = 0;
            while (si < 63 && (isalnum(c) || c == '_')) { condsym[si++] = (char)c; c = lex_getc(); }
            condsym[si] = '\0';
            while (c != EOF && c != '\n') c = lex_getc();
            if (c == '\n') g_lex.line--;

            int condition = (pc_find_symbol(condsym) != NULL);
            if (condition) {
                g_cond_stack[g_cond_depth - 1] = 0;  /* activate this branch */
            } else {
                /* Still skipping — consume until #else/#endif */
                int nest = 0;
                for (;;) {
                    c = lex_getc();
                    if (c == EOF) break;
                    if (c == '\n') { g_lex.line++; continue; }
                    if (c == '#') {
                        char dir[64]; int di = 0;
                        c = lex_getc();
                        while (di < 63 && (isalpha(c) || c == '_')) {
                            dir[di++] = (char)c;
                            c = lex_getc();
                        }
                        dir[di] = '\0';
                        if (strcmp(dir, "if") == 0 || strcmp(dir, "ifdef") == 0 || strcmp(dir, "ifndef") == 0) { nest++; while (c != EOF && c != '\n') c = lex_getc(); }
                        else if (strcmp(dir, "else") == 0 || strcmp(dir, "elseif") == 0) { if (nest == 0) { while (c != EOF && c != '\n') c = lex_getc(); break; } while (c != EOF && c != '\n') c = lex_getc(); }
                        else if (strcmp(dir, "endif") == 0) { if (nest == 0) { if (g_cond_depth > 0) g_cond_depth--; while (c != EOF && c != '\n') c = lex_getc(); break; } nest--; while (c != EOF && c != '\n') c = lex_getc(); }
                        else { while (c != EOF && c != '\n') c = lex_getc(); }
                    }
                }
            }
        } else {
            /* No #if to pair with — skip line */
            int c;
            do { c = lex_getc(); } while (c != EOF && c != '\n');
            if (c == '\n') g_lex.line--;
        }
        return 1;
    }

    /*****************************************************************
     * #endif — pop one level of the condition stack
     *****************************************************************/
    if (strcmp(directive, "endif") == 0) {
        int c;
        do { c = lex_getc(); } while (c != EOF && c != '\n');
        if (c == '\n') g_lex.line--;

        if (g_cond_depth > 0)
            g_cond_depth--;

        return 1;
    }

    /* Other conditional-relevant directives are now properly handled above.
     * The remaining ones are skipped with a single-line skip. */
    if (strcmp(directive, "pragma") == 0 ||
        strcmp(directive, "tryinclude") == 0 ||
        strcmp(directive, "assert") == 0 ||
        strcmp(directive, "error") == 0 ||
        strcmp(directive, "warning") == 0 ||
        strcmp(directive, "undef") == 0) {
        /* Skip the rest of the line */
        int c;
        do {
            c = lex_getc();
        } while (c != EOF && c != '\n');
        if (c == '\n') g_lex.line--;
        return 1;
    }

    /* Unknown directive - skip line */
    {
        int c;
        do {
            c = lex_getc();
        } while (c != EOF && c != '\n');
        if (c == '\n') g_lex.line--;
    }

    return 1;
}

/* Parser — simplified for proof-of-concept */
static int current_indent = 0;
static int current_tag = 0;

#define MAX_FUNC_STACK 256
static int func_stack[MAX_FUNC_STACK];
static int func_sp = -1;

static int expect_token(int expected, const char *msg) {
    char token[MAX_LEXRET + 1];
    int toktype;
    int tok = pc_lexread(token, sizeof(token), &toktype);

    if (tok != expected) {
        pc_error(SEV_ERROR, "%s", msg);
        return 0;
    }
    return 1;
}

static int expect_symbol(char *buf, int maxlen) {
    char token[MAX_LEXRET + 1];
    int toktype;
    int tok = pc_lexread(token, sizeof(token), &toktype);

    if (tok != TOK_SYMBOL && tok != TOK_KEYWORD) {
        pc_error(SEV_ERROR, "Expected identifier");
        return 0;
    }
    if (buf)
        strncpy(buf, token, maxlen - 1);
    return 1;
}

static int pc_parse_declaration(void);
static int pc_parse_variable_declaration(int tag);
static int pc_parse_function_definition(const char *name, int tag, int is_public, int is_native, int is_stock, int is_static);
static int pc_parse_statement_block(void);

static int pc_parse_expression(value *val) {
    char token[MAX_LEXRET + 1];
    int toktype;
    int tok = pc_lexread(token, sizeof(token), &toktype);

    if (tok == TOK_EOF)
        return 0;

    if (tok == TOK_NUMBER) {
        cell num = (cell)strtol(token, NULL, 0);
        val->constval = num;
        val->ident = EXPR_CONST;
        val->tag = 0;
        pc_emit(OP_CONST_PRI, (int)num);
        return 1;
    }

    if (tok == TOK_SYMBOL) {
        symbol *sym = pc_find_symbol(token);
        if (sym != NULL) {
            if (sym->kind == KIND_VARIABLE || sym->kind == KIND_ARRAY) {
                val->ident = EXPR_PRI;
                val->tag = sym->tag;
                val->addr = sym->addr;
                if (sym->vclass == 0)
                    pc_emit(OP_CONST_PRI, sym->addr);
                else
                    pc_emit(OP_LOAD_S_PRI, sym->addr);
                return 1;
            }
            if (sym->kind == KIND_CONSTANT) {
                val->constval = sym->addr;
                val->ident = EXPR_CONST;
                pc_emit(OP_CONST_PRI, sym->addr);
                return 1;
            }
            if (sym->kind == KIND_FUNCTION) {
                val->ident = EXPR_CONST;
                val->addr = sym->addr;
                pc_emit(OP_CONST_PRI, sym->addr);
                return 1;
            }
        }
        /* Unknown symbol - emit 0 as fallback */
        pc_emit(OP_CONST_PRI, 0);
        val->ident = EXPR_CONST;
        val->constval = 0;
        val->tag = 0;
        return 1;
    }

    if (tok == TOK_STRING) {
        pc_emit(OP_CONST_PRI, 0);
        val->ident = EXPR_CONST;
        val->tag = 0;
        return 1;
    }

    if (tok == TOK_OPERATOR) {
        if (strcmp(token, "(") == 0) {
            int result = pc_parse_expression(val);
            if (result) {
                expect_token(TOK_OPERATOR, "Expected ')'");
            }
            return result;
        }
    }

    if (tok == TOK_KEYWORD) {
        if (strcmp(token, "true") == 0 || strcmp(token, "false") == 0) {
            val->constval = (strcmp(token, "true") == 0) ? 1 : 0;
            val->ident = EXPR_CONST;
            val->tag = 0;
            pc_emit(OP_CONST_PRI, (int)val->constval);
            return 1;
        }
    }

    /* Fallback */
    val->ident = EXPR_CONST;
    val->constval = 0;
    return 1;
}

static int pc_parse_if_statement(void) {
    char token[MAX_LEXRET + 1];
    int toktype;

    expect_token(TOK_OPERATOR, "Expected '('");
    value val;
    pc_parse_expression(&val);
    expect_token(TOK_OPERATOR, "Expected ')'");

    /* Emit jzer for condition */
    int patch_addr = g_codeblock.curlength;
    pc_emit(OP_JZER, 0);

    pc_parse_statement();

    /* Check for else */
    int tok = pc_lexread(token, sizeof(token), &toktype);
    if (tok == TOK_KEYWORD && strcmp(token, "else") == 0) {
        int else_patch = g_codeblock.curlength;
        pc_emit(OP_JUMP, 0);

        /* Patch jzer to jump here */
        int target = g_codeblock.curlength;
        memcpy(g_codeblock.code + patch_addr + 1, &target, sizeof(int));

        pc_parse_statement();

        /* Patch jump */
        target = g_codeblock.curlength;
        memcpy(g_codeblock.code + else_patch + 1, &target, sizeof(int));
    } else {
        pc_lexpush(tok, token);
        /* Patch jzer */
        int target = g_codeblock.curlength;
        memcpy(g_codeblock.code + patch_addr + 1, &target, sizeof(int));
    }

    return 1;
}

static int pc_parse_while_statement(void) {
    int loop_start = g_codeblock.curlength;

    expect_token(TOK_OPERATOR, "Expected '('");
    value val;
    pc_parse_expression(&val);
    expect_token(TOK_OPERATOR, "Expected ')'");

    int patch_addr = g_codeblock.curlength;
    pc_emit(OP_JZER, 0);

    pc_parse_statement();

    pc_emit(OP_JUMP, loop_start);

    int target = g_codeblock.curlength;
    memcpy(g_codeblock.code + patch_addr + 1, &target, sizeof(int));

    return 1;
}

static int pc_parse_for_statement(void) {
    expect_token(TOK_OPERATOR, "Expected '('");

    /* Init */
    int tok;
    char token[MAX_LEXRET + 1];
    int toktype;
    tok = pc_lexread(token, sizeof(token), &toktype);
    if (tok != TOK_OPERATOR || strcmp(token, ";") != 0) {
        pc_lexpush(tok, token);
        pc_parse_expression(NULL);
        expect_token(TOK_OPERATOR, "Expected ';'");
    }

    /* Condition */
    int loop_check = g_codeblock.curlength;
    value cond_val;
    tok = pc_lexread(token, sizeof(token), &toktype);
    int has_cond = !(tok == TOK_OPERATOR && strcmp(token, ";") == 0);
    if (has_cond) {
        pc_lexpush(tok, token);
        pc_parse_expression(&cond_val);
        expect_token(TOK_OPERATOR, "Expected ';'");
    }

    int patch_addr = g_codeblock.curlength;
    if (has_cond)
        pc_emit(OP_JZER, 0);

    /* Increment (parse but don't keep) */
    int inc_start = g_codeblock.curlength;
    tok = pc_lexread(token, sizeof(token), &toktype);
    if (!(tok == TOK_OPERATOR && strcmp(token, ")") == 0)) {
        pc_lexpush(tok, token);
        pc_parse_expression(NULL);
        expect_token(TOK_OPERATOR, "Expected ')'");
    }

    pc_parse_statement();

    /* Jump to increment */
    pc_emit(OP_JUMP, inc_start);

    int after_loop = g_codeblock.curlength;

    /* Patch condition jump */
    if (has_cond) {
        memcpy(g_codeblock.code + patch_addr + 1, &after_loop, sizeof(int));
    }

    /* Jump from inc check to loop check */
    int jump_to_check = loop_check;
    int current_pos = g_codeblock.curlength;

    return 1;
}

static int pc_parse_return_statement(void) {
    char token[MAX_LEXRET + 1];
    int toktype;

    int tok = pc_lexread(token, sizeof(token), &toktype);
    if (tok == TOK_OPERATOR && strcmp(token, ";") == 0) {
        pc_emit_block(OP_ZERO_PRI);
        pc_emit_block(OP_RETN);
        return 1;
    }

    pc_lexpush(tok, token);
    value val;
    pc_parse_expression(&val);
    pc_emit_block(OP_RETN);

    tok = pc_lexread(token, sizeof(token), &toktype);
    if (tok == TOK_OPERATOR && strcmp(token, ";") == 0) {
        return 1;
    }
    pc_lexpush(tok, token);
    return 1;
}

int pc_parse_statement(void) {
    char token[MAX_LEXRET + 1];
    int toktype;
    int tok = pc_lexread(token, sizeof(token), &toktype);

    if (tok == TOK_EOF)
        return 0;

    if (tok == TOK_NEWLINE)
        return 1;

    if (tok == TOK_KEYWORD) {
        if (strcmp(token, "if") == 0)
            return pc_parse_if_statement();
        if (strcmp(token, "while") == 0)
            return pc_parse_while_statement();
        if (strcmp(token, "for") == 0)
            return pc_parse_for_statement();
        if (strcmp(token, "return") == 0)
            return pc_parse_return_statement();
        if (strcmp(token, "break") == 0) {
            pc_emit_block(OP_JUMP);
            return 1;
        }
        if (strcmp(token, "continue") == 0) {
            pc_emit_block(OP_JUMP);
            return 1;
        }
        if (strcmp(token, "new") == 0) {
            char varname[64];
            expect_symbol(varname, sizeof(varname));
            symbol *sym = pc_add_symbol(varname, KIND_VARIABLE, current_tag, 1);
            if (sym) {
                sym->addr = current_indent;
                current_indent += 4;
            }
            int tok2 = pc_lexread(token, sizeof(token), &toktype);
            if (tok2 == TOK_OPERATOR && strcmp(token, "=") == 0) {
                value val;
                pc_parse_expression(&val);
                if (sym)
                    pc_emit(OP_STOR_S_PRI, sym->addr);
            } else {
                pc_lexpush(tok2, token);
            }
            return 1;
        }
        if (strcmp(token, "const") == 0) {
            char varname[64];
            expect_symbol(varname, sizeof(varname));
            int tok2 = pc_lexread(token, sizeof(token), &toktype);
            if (tok2 == TOK_OPERATOR && strcmp(token, "=") == 0) {
                value val;
                pc_parse_expression(&val);
                symbol *sym = pc_add_symbol(varname, KIND_CONSTANT, 0, 0);
                if (sym)
                    sym->addr = (int)val.constval;
            }
            return 1;
        }
        if (strcmp(token, "switch") == 0) {
            expect_token(TOK_OPERATOR, "Expected '('");
            value val;
            pc_parse_expression(&val);
            expect_token(TOK_OPERATOR, "Expected ')'");
            expect_token(TOK_OPERATOR, "Expected '{'");

            int end_patch = g_codeblock.curlength;
            pc_emit(OP_JUMP, 0);

            int target = g_codeblock.curlength;
            memcpy(g_codeblock.code + end_patch + 1, &target, sizeof(int));
            return 1;
        }
        if (strcmp(token, "state") == 0) {
            return 1;
        }
        printf("Warning: Unhandled keyword '%s' at line %d\n", token, g_line_number);
        return 1;
    }

    if (tok == TOK_SYMBOL) {
        symbol *sym = pc_find_symbol(token);
        if (sym != NULL && (sym->kind == KIND_FUNCTION)) {
            /* Function call */
            int old_sp = func_sp;
            int argc = 0;

            int tok2 = pc_lexread(token, sizeof(token), &toktype);
            if (tok2 == TOK_OPERATOR && strcmp(token, "(") == 0) {
                for (;;) {
                    tok2 = pc_lexread(token, sizeof(token), &toktype);
                    if (tok2 == TOK_OPERATOR && strcmp(token, ")") == 0)
                        break;
                    pc_lexpush(tok2, token);
                    value argval;
                    pc_parse_expression(&argval);
                    pc_emit(OP_PUSH, 0);
                    argc++;
                    tok2 = pc_lexread(token, sizeof(token), &toktype);
                    if (tok2 == TOK_OPERATOR && strcmp(token, ")") == 0)
                        break;
                    if (tok2 == TOK_OPERATOR && strcmp(token, ",") == 0)
                        continue;
                }
            }

            pc_emit(OP_CALL, sym->addr);

            /* Pop arguments */
            if (argc > 0) {
                pc_emit(OP_STACK, argc * 4);
            }

            int tok3 = pc_lexread(token, sizeof(token), &toktype);
            if (tok3 == TOK_OPERATOR && strcmp(token, ";") == 0) {
                return 1;
            }
            pc_lexpush(tok3, token);
            return 1;
        }

        /* Variable assignment */
        if (sym != NULL) {
            int tok2 = pc_lexread(token, sizeof(token), &toktype);
            if (tok2 == TOK_OPERATOR) {
                if (strcmp(token, "=") == 0) {
                    value val;
                    pc_parse_expression(&val);
                    if (sym->vclass == 0)
                        pc_emit(OP_STOR_PRI, sym->addr);
                    else
                        pc_emit(OP_STOR_S_PRI, sym->addr);
                } else if (strcmp(token, "+=") == 0) {
                    if (sym->vclass == 0)
                        pc_emit(OP_LOAD_PRI, sym->addr);
                    else
                        pc_emit(OP_LOAD_S_PRI, sym->addr);
                    value val;
                    pc_parse_expression(&val);
                    pc_emit_block(OP_ADD);
                    if (sym->vclass == 0)
                        pc_emit(OP_STOR_PRI, sym->addr);
                    else
                        pc_emit(OP_STOR_S_PRI, sym->addr);
                } else if (strcmp(token, "-=") == 0) {
                    if (sym->vclass == 0)
                        pc_emit(OP_LOAD_PRI, sym->addr);
                    else
                        pc_emit(OP_LOAD_S_PRI, sym->addr);
                    value val;
                    pc_parse_expression(&val);
                    pc_emit_block(OP_SUB);
                    if (sym->vclass == 0)
                        pc_emit(OP_STOR_PRI, sym->addr);
                    else
                        pc_emit(OP_STOR_S_PRI, sym->addr);
                } else if (strcmp(token, "++") == 0) {
                    if (sym->vclass == 0) {
                        pc_emit(OP_INC, sym->addr);
                    } else {
                        pc_emit(OP_LOAD_S_PRI, sym->addr);
                        pc_emit(OP_CONST_ALT, 1);
                        pc_emit_block(OP_ADD);
                        pc_emit(OP_STOR_S_PRI, sym->addr);
                    }
                } else if (strcmp(token, "--") == 0) {
                    if (sym->vclass == 0) {
                        pc_emit(OP_DEC, sym->addr);
                    } else {
                        pc_emit(OP_LOAD_S_PRI, sym->addr);
                        pc_emit(OP_CONST_ALT, 1);
                        pc_emit_block(OP_SUB);
                        pc_emit(OP_STOR_S_PRI, sym->addr);
                    }
                }
            }
        }

        int tok4 = pc_lexread(token, sizeof(token), &toktype);
        if (tok4 == TOK_OPERATOR && strcmp(token, ";") == 0)
            return 1;
        pc_lexpush(tok4, token);
        return 1;
    }

    if (tok == TOK_OPERATOR) {
        if (strcmp(token, "{") == 0) {
            return pc_parse_statement_block();
        }
        if (strcmp(token, ";") == 0)
            return 1;
    }

    if (tok == TOK_PREPROC) {
        return pc_handle_preproc();
    }

    return 1;
}

static int pc_parse_statement_block(void) {
    int tok;
    char token[MAX_LEXRET + 1];
    int toktype;

    for (;;) {
        tok = pc_lexread(token, sizeof(token), &toktype);
        if (tok == TOK_EOF)
            break;
        if (tok == TOK_OPERATOR && strcmp(token, "}") == 0)
            break;
        pc_lexpush(tok, token);
        pc_parse_statement();
    }

    return 1;
}

static int pc_parse_parameter_list(void) {
    int param_count = 0;
    char token[MAX_LEXRET + 1];
    int toktype;

    for (;;) {
        int tok = pc_lexread(token, sizeof(token), &toktype);
        if (tok == TOK_OPERATOR && strcmp(token, ")") == 0)
            break;

        pc_lexpush(tok, token);

        /* new/const type name */
        tok = pc_lexread(token, sizeof(token), &toktype);
        if (tok == TOK_KEYWORD && (strcmp(token, "new") == 0 || strcmp(token, "const") == 0)) {
            tok = pc_lexread(token, sizeof(token), &toktype);
        }

        if (tok == TOK_SYMBOL) {
            symbol *sym = pc_add_symbol(token, KIND_VARIABLE, 0, 1);
            if (sym) {
                sym->addr = -(param_count + 1) * 4;
            }
            param_count++;
        }

        tok = pc_lexread(token, sizeof(token), &toktype);
        if (tok == TOK_OPERATOR && strcmp(token, ")") == 0)
            break;
        if (tok == TOK_OPERATOR && strcmp(token, ",") == 0)
            continue;
    }

    return param_count;
}

static int pc_parse_function_definition(const char *name, int tag, int is_public, int is_native, int is_stock, int is_static) {
    (void)is_static;

    if (is_native) {
        /* Native function - parse parameters and semicolon */
        char token[MAX_LEXRET + 1];
        int toktype;
        int tok = pc_lexread(token, sizeof(token), &toktype);
        if (tok == TOK_OPERATOR && strcmp(token, "(") == 0) {
            pc_parse_parameter_list();
        }
        /* Expect semicolon */
        tok = pc_lexread(token, sizeof(token), &toktype);
        if (tok == TOK_OPERATOR && strcmp(token, ";") == 0) {
            symbol *sym = pc_add_function(name, tag, 0);
            if (sym) {
                sym->flags |= SYMBOL_NATIVE;
            }
            return 1;
        }
        pc_lexpush(tok, token);
        return 1;
    }

    /* Check for forward declaration */
    char token[MAX_LEXRET + 1];
    int toktype;
    int tok = pc_lexread(token, sizeof(token), &toktype);

    if (tok == TOK_OPERATOR && strcmp(token, ";") == 0) {
        /* Forward declaration */
        symbol *sym = pc_add_function(name, tag, 0);
        if (sym && is_public) sym->flags |= SYMBOL_PUBLIC;
        if (sym && is_stock) sym->flags |= SYMBOL_STOCK;
        return 1;
    }

    if (!(tok == TOK_OPERATOR && strcmp(token, "(") == 0)) {
        if (tok != TOK_EOF)
            pc_lexpush(tok, token);
        return 1;
    }

    /* Parse parameters */
    current_indent = 4; /* Space for return address */
    int param_count = pc_parse_parameter_list();

    /* Parse function body */
    tok = pc_lexread(token, sizeof(token), &toktype);
    if (!(tok == TOK_OPERATOR && strcmp(token, "{") == 0)) {
        if (tok != TOK_EOF)
            pc_lexpush(tok, token);
        return 1;
    }

    /* Emit PROC */
    pc_emit_block(OP_PROC);

    /* Mark function address */
    symbol *sym = pc_add_function(name, tag, 0);
    if (sym) {
        sym->addr = g_codeblock.curlength;
        sym->size = param_count;
        if (is_public) sym->flags |= SYMBOL_PUBLIC;
        if (is_stock) sym->flags |= SYMBOL_STOCK;
    }

    /* Add to public list if needed */
    if (is_public) {
        int public_addr = sym ? sym->addr : 0;
        (void)public_addr;
    }

    /* Parse body */
    code_track_file_line(g_lex.filename, g_lex.line);
    pc_parse_statement_block();

    /* Emit return */
    pc_emit_block(OP_ZERO_PRI);
    pc_emit_block(OP_RETN_PROC);

    /* Fix return address offset */
    if (sym) {
        /* patch the initial stack usage */
    }

    return 1;
}

static int pc_parse_declaration(void) {
    char token[MAX_LEXRET + 1];
    int toktype;
    int tag = 0;
    int is_public = 0;
    int is_native = 0;
    int is_stock = 0;
    int is_static = 0;

    int tok = pc_lexread(token, sizeof(token), &toktype);

    if (tok == TOK_EOF)
        return 0;

    if (tok == TOK_PREPROC) {
        return pc_handle_preproc();
    }

    if (tok == TOK_NEWLINE)
        return 1;

    if (tok != TOK_KEYWORD) {
        if (tok != TOK_EOF)
            pc_lexpush(tok, token);
        return pc_parse_statement();
    }

    /* Parse modifiers */
    if (strcmp(token, "public") == 0) {
        is_public = 1;
        tok = pc_lexread(token, sizeof(token), &toktype);
    }

    if (strcmp(token, "native") == 0) {
        is_native = 1;
        tok = pc_lexread(token, sizeof(token), &toktype);
    }

    if (strcmp(token, "stock") == 0) {
        is_stock = 1;
        tok = pc_lexread(token, sizeof(token), &toktype);
    }

    if (strcmp(token, "static") == 0) {
        is_static = 1;
        tok = pc_lexread(token, sizeof(token), &toktype);
    }

    if (tok != TOK_KEYWORD && tok != TOK_SYMBOL) {
        if (tok != TOK_EOF)
            pc_lexpush(tok, token);
        return 1;
    }

    /* Check for tag (e.g., "Float:") */
    char next_token[MAX_LEXRET + 1];
    int peek_tok = pc_lexread(next_token, sizeof(next_token), &toktype);
    if (peek_tok == TOK_OPERATOR && strcmp(next_token, ":") == 0) {
        tag = pc_find_tag(token);
        if (tag == 0) {
            tag = pc_add_tag(token, 0);
        }
        tok = pc_lexread(token, sizeof(token), &toktype);
    } else {
        pc_lexpush(peek_tok, next_token);
    }

    if (tok != TOK_SYMBOL) {
        if (tok != TOK_EOF)
            pc_lexpush(tok, token);
        return 1;
    }

    /* Peek for function or variable */
    char name[sNAMEMAX + 1];
    strncpy(name, token, sNAMEMAX);
    name[sNAMEMAX] = '\0';

    int next = pc_lexread(next_token, sizeof(next_token), &toktype);

    if (next == TOK_OPERATOR && strcmp(next_token, "(") == 0) {
        /* Function definition */
        pc_lexpush(next, next_token);
        return pc_parse_function_definition(name, tag, is_public, is_native, is_stock, is_static);
    }

    /* Variable declaration */
    pc_lexpush(next, next_token);

    symbol *sym = pc_add_symbol(name, KIND_VARIABLE, tag, is_static ? 0 : 1);
    if (sym) {
        if (is_public) sym->flags |= SYMBOL_PUBLIC;
        if (is_stock) sym->flags |= SYMBOL_STOCK;
        sym->addr = g_code_idx;
        g_code_idx += 4;
    }

    /* Check for initialization */
    next = pc_lexread(next_token, sizeof(next_token), &toktype);
    if (next == TOK_OPERATOR && strcmp(next_token, "=") == 0) {
        value val;
        pc_parse_expression(&val);
        if (sym) {
            pc_emit(OP_STOR_PRI, sym->addr);
        }
    } else if (next == TOK_OPERATOR && strcmp(next_token, "[") == 0) {
        /* Array declaration */
        if (sym) {
            sym->kind = KIND_ARRAY;
            int arr_size = 256;
            int dim = 0;
            sym->dims[dim++] = arr_size;
            sym->size = arr_size;
            g_code_idx += arr_size * 4;
        }
        next = pc_lexread(next_token, sizeof(next_token), &toktype);
        if (next == TOK_OPERATOR && strcmp(next_token, "]") == 0) {
            next = pc_lexread(next_token, sizeof(next_token), &toktype);
            if (next == TOK_OPERATOR && strcmp(next_token, "=") == 0) {
                /* Array initialization - skip for now */
                /* Parse initializer list */
                int init_tok = pc_lexread(next_token, sizeof(next_token), &toktype);
                if (init_tok == TOK_OPERATOR && strcmp(next_token, "{") == 0) {
                    int depth = 1;
                    while (depth > 0) {
                        init_tok = pc_lexread(next_token, sizeof(next_token), &toktype);
                        if (init_tok == TOK_EOF) break;
                        if (init_tok == TOK_OPERATOR && strcmp(next_token, "{") == 0) depth++;
                        if (init_tok == TOK_OPERATOR && strcmp(next_token, "}") == 0) depth--;
                    }
                }
            } else {
                pc_lexpush(next, next_token);
            }
        } else {
            pc_lexpush(next, next_token);
        }
    } else {
        pc_lexpush(next, next_token);
    }

    /* Expect semicolon */
    next = pc_lexread(next_token, sizeof(next_token), &toktype);
    if (next == TOK_OPERATOR && strcmp(next_token, ";") == 0)
        return 1;
    if (next != TOK_EOF)
        pc_lexpush(next, next_token);
    return 1;
}

static int pc_parse_file(void) {
    int result;

    while ((result = pc_parse_declaration()) != 0) {
        /* Continue parsing */
    }

    return g_errors == 0;
}

/* AMX Writing */
static void pc_emit_amx_header(FILE *fp) {
    AMX_HEADER hdr;
    memset(&hdr, 0, sizeof(hdr));

    hdr.magic = AMX_MAGIC;
    hdr.file_version = 8;
    hdr.amx_version = AMX_VERSION;
    hdr.flags = g_debug ? AMX_FLAG_DEBUG : 0;
    hdr.defsize = sizeof(AMX_HEADER);

    /* Compute sizes */
    hdr.hea = 0;
    hdr.stp = 1024;
    hdr.cip = 0;

    /* Code starts after header */
    int code_size = (g_codeblock.curlength + 3) & ~3;
    hdr.cod = sizeof(AMX_HEADER);
    hdr.dat = hdr.cod + code_size;

    fwrite(&hdr, sizeof(AMX_HEADER), 1, fp);
}

static void pc_emit_code(FILE *fp) {
    /* Align to 4 bytes */
    int padded_size = (g_codeblock.curlength + 3) & ~3;
    fwrite(g_codeblock.code, 1, (size_t)g_codeblock.curlength, fp);

    /* Write padding */
    while (g_codeblock.curlength < padded_size) {
        char pad = 0;
        fwrite(&pad, 1, 1, fp);
        g_codeblock.curlength++;
    }
}

void pc_write_amx(const char *filename) {
    FILE *fp = fopen(filename, "wb");
    if (fp == NULL) {
        pc_error(SEV_FATAL, "File write error: %s", filename);
        return;
    }

    pc_emit_amx_header(fp);
    pc_emit_code(fp);

    fclose(fp);

    if (!g_quiet) {
        pc_printf("Written: %s (%d bytes)\n", filename, g_codeblock.curlength + (int)sizeof(AMX_HEADER));
    }
}

/* Main compile function */
int pc_compile(const char *infile, const char *outfile, const char *includes[], int num_includes, char *error_buf, int error_buf_size, int compiler_version) {
    int result;

    /* Reset state */
    memset(&g_lex, 0, sizeof(g_lex));
    lex_stack_ptr = -1;
    memset(sym_table_hash, 0, sizeof(sym_table_hash));
    sym_count = 0;
    g_errors = 0;
    g_warnings = 0;
    g_code_idx = 0;
    current_indent = 0;
    current_tag = 0;
    func_sp = -1;
    g_cond_depth = 0;
    g_num_included_files = 0;
    memset(g_included_files, 0, sizeof(g_included_files));

    /* Initialize include list */
    g_numincludes = 0;
    for (int i = 0; i < num_includes && i < MAX_INCLUDE_DEPTH; i++) {
        if (includes[i] != NULL) {
            g_inclist[g_numincludes++] = (char *)includes[i];
        }
    }

    strncpy(g_infile, infile, sFNAME);
    g_infile[sFNAME] = '\0';
    strncpy(g_outfile, outfile, sFNAME);
    g_outfile[sFNAME] = '\0';

    /* Initialize code block */
    code_init();

    /* Redirect stdout/stderr for capturing */
    if (error_buf != NULL)
        error_buf[0] = '\0';

    /* Open input file */
    FILE *fp = fopen(infile, "r");
    if (fp == NULL) {
        snprintf(error_buf, error_buf_size, "Cannot open file: %s", infile);
        return 0;
    }

    /* Print version banner */
    {
        const char *ver_str;
        const char *ver_label;
        if (compiler_version == COMPILER_VERSION_OMP) {
            ver_str = PAWN_COMPILER_VERSION_31011;
            ver_label = "open.mp";
        } else {
            ver_str = PAWN_COMPILER_VERSION_307;
            ver_label = "SA-MP";
        }
        pc_printf("%s v%s (%s compatible)\n", PAWN_COMPILER_NAME, ver_str, ver_label);
        pc_printf("Compiling: %s\n", infile);
        pc_printf("Output:    %s\n", outfile);
    }

    /* Initialize lexer */
    pc_lexinit(fp, infile);

    /* Set error handler */
    if (setjmp(g_errbuf)) {
        /* Fatal error occurred */
        fclose(fp);
        return 0;
    }

    /* Parse */
    g_stage = STAGE_PARSE;
    result = pc_parse_file();

    fclose(fp);

    if (result && g_errors == 0) {
        g_stage = STAGE_EMIT;
        pc_write_amx(outfile);
    }

    return result && g_errors == 0;
}
