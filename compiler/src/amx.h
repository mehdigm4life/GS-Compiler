#ifndef AMX_H_INCLUDED
#define AMX_H_INCLUDED

#define AMX_VERSION 9

#define AMX_MAGIC 0x9f0f0000
#define AMX_MAGIC_LE 0x00000f9f
#define AMX_FLAG_DEBUG 0x01
#define AMX_FLAG_COMPACT 0x02
#define AMX_FLAG_BYTEOPC 0x04
#define AMX_FLAG_NoChk 0x08

#define sEXPMAX 127
#define sLINEMAX 511
#define sFNAME 63
#define sNAMEMAX 127

typedef uint32_t cell;
typedef cell *ucell;

typedef struct AMX_HEADER {
    int32_t magic;
    int16_t file_version;
    int16_t amx_version;
    int16_t flags;
    int16_t defsize;
    int32_t cod;
    int32_t dat;
    int32_t hea;
    int32_t stp;
    int32_t cip;
    int32_t publics;
    int32_t natives;
    int32_t libraries;
    int32_t pubvars;
    int32_t tags;
    int32_t nametable;
} AMX_HEADER;

#define AMX_UINT16(p) ((uint16_t)(((uint8_t*)(p))[0] | ((uint8_t*)(p))[1] << 8))
#define AMX_UINT32(p) ((uint32_t)(((uint8_t*)(p))[0] | ((uint8_t*)(p))[1] << 8 | ((uint8_t*)(p))[2] << 16 | ((uint8_t*)(p))[3] << 24))

enum {
    AMX_ERR_NONE,
    AMX_ERR_EXIT,
    AMX_ERR_ASSERT,
    AMX_ERR_STACKERR,
    AMX_ERR_BOUNDS,
    AMX_ERR_MEMACCESS,
    AMX_ERR_INVINSTR,
    AMX_ERR_STACKLOW,
    AMX_ERR_HEAPLOW,
    AMX_ERR_CALLBACK,
    AMX_ERR_NATIVE,
    AMX_ERR_DIVIDE,
    AMX_ERR_SLEEP,
    AMX_ERR_INVSTATE,
    AMX_ERR_OVERLAY,
    AMX_ERR_INDEX,
    AMX_ERR_DEBUG,
    AMX_ERR_INIT,
    AMX_ERR_INVINIT,
};

#endif
