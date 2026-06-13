#include "sc.h"

char g_infile[sFNAME + 1] = {0};
char g_outfile[sFNAME + 1] = {0};
char g_error_msg[MAX_ERR_MSG + 1] = {0};
int g_errors = 0;
int g_warnings = 0;
int g_total_errors = 0;
int g_total_warnings = 0;
int g_quiet = 0;
int g_debug = 0;
int g_verbose = 0;
int g_showstats = 0;
int g_compact = 0;
int g_require_semicolon = 1;
int g_tabsize = 8;
char *g_inclist[MAX_INCLUDE_DEPTH] = {NULL};
int g_numincludes = 0;
char g_includedir[512] = {0};
jmp_buf g_errbuf;

static int error_count = 0;
static int warning_count = 0;

FILE *g_input_file = NULL;
char g_input_filename[sFNAME + 1] = {0};
int g_line_number = 0;
int g_stage = STAGE_NONE;
symbol *g_symbol_table = NULL;
int g_code_scope = 0;
int g_code_idx = 0;
int g_current_function = -1;
int g_tag_count = 0;
codeblock g_codeblock;

/* Error message definitions */
static const char *error_messages[] = {
    "No error",
    "Invalid function or declaration",
    "Invalid expression",
    "Array index out of bounds",
    "Symbol is already defined: %s",
    "Symbol is not defined: %s",
    "Invalid assignment",
    "Type mismatch",
    "Invalid tag override",
    "Function may not be declared here",
    "Invalid number of arguments",
    "Invalid array size",
    "String literal exceeds maximum length",
    "Unexpected end of file",
    "Missing semicolon",
    "Missing right parenthesis",
    "Missing left parenthesis",
    "Missing right bracket",
    "Missing left bracket",
    "Invalid preprocessor directive",
    "Cannot read from file: %s",
    "Cannot open include file: %s",
    "Include depth exceeded",
    "Nested comments are not allowed",
    "Divide by zero",
    "Constant expression expected",
    "Invalid public function",
    "Native function must have a body",
    "Invalid stock function",
    "Symbol is not a function: %s",
    "Symbol is not an array: %s",
    "Invalid index expression",
    "Too many nested function calls",
    "Return value required",
    "No return value expected",
    "Switch expression must be integer",
    "Duplicate default case",
    "Too many cases",
    "Invalid break/continue",
    "Internal compiler error",
    "Out of memory",
    "File write error",
    "Assertion failed",
};

void pc_error(int severity, const char *fmt, ...) {
    char msg[MAX_ERR_MSG + 1];
    va_list args;
    va_start(args, fmt);
    vsnprintf(msg, MAX_ERR_MSG, fmt, args);
    va_end(args);

    if (severity == SEV_WARNING) {
        warning_count++;
        g_warnings++;
        g_total_warnings++;
        pc_printf_err("Warning: %s (line %d)\n", msg, g_line_number);
    } else {
        error_count++;
        g_errors++;
        g_total_errors++;
        pc_printf_err("Error: %s (line %d)\n", msg, g_line_number);
        if (severity >= SEV_FATAL) {
            longjmp(g_errbuf, 1);
        }
    }
}

void pc_warning(const char *fmt, ...) {
    char msg[MAX_ERR_MSG + 1];
    va_list args;
    va_start(args, fmt);
    vsnprintf(msg, MAX_ERR_MSG, fmt, args);
    va_end(args);
    pc_error(SEV_WARNING, "%s", msg);
}

void pc_printf(const char *fmt, ...) {
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    fprintf(stdout, "%s", buf);
    fflush(stdout);
}

void pc_printf_err(const char *fmt, ...) {
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    fprintf(stderr, "%s", buf);
    fflush(stderr);
}

const char *pc_get_error_msg(int num) {
    if (num < 0 || num >= (int)(sizeof(error_messages) / sizeof(error_messages[0])))
        return "Unknown error";
    return error_messages[num];
}
