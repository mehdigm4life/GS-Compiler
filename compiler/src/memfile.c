#include "sc.h"
#include <stdlib.h>
#include <string.h>

memfile *pc_memfile_open(const char *name, const char *source) {
    memfile *mf = (memfile *)calloc(1, sizeof(memfile));
    if (mf == NULL)
        return NULL;

    strncpy(mf->name, name, sFNAME);
    mf->name[sFNAME] = '\0';

    if (source != NULL) {
        mf->length = (long)strlen(source);
        mf->buffer = (char *)malloc((size_t)(mf->length + 1));
        if (mf->buffer != NULL) {
            memcpy(mf->buffer, source, (size_t)mf->length);
            mf->buffer[mf->length] = '\0';
        }
    }
    mf->pos = 0;
    return mf;
}

int pc_memfile_read(memfile *mf, char *dest, int maxlen) {
    int count = 0;
    if (mf == NULL || mf->buffer == NULL || dest == NULL || maxlen <= 0)
        return 0;

    while (mf->pos < mf->length && count < maxlen - 1) {
        char c = mf->buffer[mf->pos++];
        dest[count++] = c;
        if (c == '\n')
            break;
    }
    dest[count] = '\0';
    return count;
}

void pc_memfile_close(memfile *mf) {
    if (mf != NULL) {
        free(mf->buffer);
        free(mf);
    }
}
