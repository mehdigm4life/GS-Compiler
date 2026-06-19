#include "sc.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

int main(int argc, char *argv[]) {
    const char *infile = NULL;
    const char *outfile = NULL;
    const char *includes[MAX_INCLUDE_DEPTH];
    int num_includes = 0;
    int i;

    g_quiet = 0;
    g_debug = 0;
    g_verbose = 0;

    memset(includes, 0, sizeof(includes));

    for (i = 1; i < argc; i++) {
        if (argv[i][0] == '-' || argv[i][0] == '/') {
            const char *opt = argv[i] + 1;

            if (strcmp(opt, "i") == 0 || strcmp(opt, "I") == 0 || strcmp(opt, "-include") == 0) {
                if (i + 1 < argc) {
                    if (num_includes < MAX_INCLUDE_DEPTH) {
                        includes[num_includes++] = argv[++i];
                    }
                }
            } else if (strcmp(opt, "o") == 0 || strcmp(opt, "O") == 0 || strcmp(opt, "-output") == 0) {
                if (i + 1 < argc) {
                    outfile = argv[++i];
                }
            } else if (strcmp(opt, "d") == 0 || strcmp(opt, "debug") == 0) {
                g_debug = 1;
            } else if (strcmp(opt, "-") == 0) {
                if (strcmp(argv[i], "--") == 0) {
                    /* End of options */
                    if (i + 1 < argc) infile = argv[++i];
                    if (i + 1 < argc) outfile = argv[++i];
                    break;
                }
                /* Long options */
                if (strncmp(argv[i], "--include=", 10) == 0) {
                    if (num_includes < MAX_INCLUDE_DEPTH) {
                        includes[num_includes++] = argv[i] + 10;
                    }
                } else if (strncmp(argv[i], "--output=", 9) == 0) {
                    outfile = argv[i] + 9;
                } else if (strcmp(argv[i], "--quiet") == 0) {
                    g_quiet = 1;
                } else if (strcmp(argv[i], "--verbose") == 0) {
                    g_verbose = 1;
                } else if (strcmp(argv[i], "--debug") == 0) {
                    g_debug = 1;
                }
            }
        } else {
            if (infile == NULL) {
                infile = argv[i];
            } else if (outfile == NULL) {
                outfile = argv[i];
            }
        }
    }

    if (infile == NULL) {
        fprintf(stderr, "Usage: %s <input.pwn> [output.amx] [-i<include_path>] [-o<output>] [-d[ebug]]\n", argv[0]);
        return 1;
    }

    if (outfile == NULL) {
        const char *dot = strrchr(infile, '.');
        if (dot != NULL) {
            size_t len = (size_t)(dot - infile);
            char *buf = (char *)malloc(len + 5);
            memcpy(buf, infile, len);
            buf[len] = '\0';
            strcat(buf, ".amx");
            outfile = buf;
        } else {
            outfile = "output.amx";
        }
    }

    if (!g_quiet) {
        fprintf(stdout, "%s v%s\n", PAWN_COMPILER_NAME, PAWN_COMPILER_VERSION);
        fprintf(stdout, "Compiling: %s\n", infile);
        fprintf(stdout, "Output:    %s\n", outfile);
        fflush(stdout);
    }

    char error_buf[4096] = {0};
    int compiler_version = COMPILER_VERSION_SAMP;
    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--version-samp") == 0) {
            compiler_version = COMPILER_VERSION_SAMP;
        } else if (strcmp(argv[i], "--version-omp") == 0) {
            compiler_version = COMPILER_VERSION_OMP;
        }
    }
    int result = pc_compile(infile, outfile, includes, num_includes, error_buf, sizeof(error_buf), compiler_version);

    if (!g_quiet) {
        fprintf(stdout, "\nCompilation %s\n", result ? "successful" : "failed");
        if (g_errors > 0 || g_warnings > 0) {
            fprintf(stdout, "Errors: %d, Warnings: %d\n", g_errors, g_warnings);
        }
        fflush(stdout);
    }

    if (error_buf[0]) {
        fprintf(stderr, "%s\n", error_buf);
        fflush(stderr);
    }

    return result ? 0 : 1;
}
