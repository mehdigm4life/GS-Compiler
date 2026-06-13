cmake_minimum_required(VERSION 3.22.1)
project("gs-compiler-native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Enable NEON optimizations for ARM
if(CMAKE_ANDROID_ARM_NEON)
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon -mfloat-abi=softfp")
endif()

# Strict compilation for stability
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -Wno-unused-parameter -Wno-sign-compare -Wno-missing-field-initializers -fPIC -O2")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -fPIC -O2")

# Source files for the Pawn compiler
set(PAWN_SOURCES
    src/pawncc.c
    src/sc.c
    src/scvars.c
    src/sclist.c
    src/scstr.c
    src/memfile.c
    src/lngfile.c
)

# JNI bridge
set(JNI_SOURCES
    src/jni_bridge.cpp
)

# Create the native library
add_library(gs-compiler SHARED
    ${PAWN_SOURCES}
    ${JNI_SOURCES}
)

# Include headers
target_include_directories(gs-compiler PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
)

# Link log library for Android logging
target_link_libraries(gs-compiler
    log
    android
)

# Define compiler flags
target_compile_definitions(gs-compiler PRIVATE
    -DANDROID
    -D__ANDROID__
    -D_GNU_SOURCE
)
