#include "sc.h"
#include <string.h>
#include <ctype.h>

char *pc_strlwr(char *str) {
    char *p = str;
    while (*p) {
        *p = (char)tolower((unsigned char)*p);
        p++;
    }
    return str;
}

char *pc_strupr(char *str) {
    char *p = str;
    while (*p) {
        *p = (char)toupper((unsigned char)*p);
        p++;
    }
    return str;
}

int pc_strnicmp(const char *s1, const char *s2, int n) {
    while (n > 0 && *s1 && *s2) {
        char c1 = (char)tolower((unsigned char)*s1);
        char c2 = (char)tolower((unsigned char)*s2);
        if (c1 != c2)
            return c1 - c2;
        s1++;
        s2++;
        n--;
    }
    if (n == 0)
        return 0;
    if (*s1 == '\0' && *s2 == '\0')
        return 0;
    return *s1 == '\0' ? -1 : 1;
}

char *pc_strmid(char *dest, const char *src, int start, int len) {
    if (dest == NULL || src == NULL)
        return NULL;

    int srclen = (int)strlen(src);
    if (start >= srclen) {
        dest[0] = '\0';
        return dest;
    }

    int copylen = len;
    if (start + copylen > srclen)
        copylen = srclen - start;
    if (copylen < 0)
        copylen = 0;

    memmove(dest, src + start, (size_t)copylen);
    dest[copylen] = '\0';
    return dest;
}
