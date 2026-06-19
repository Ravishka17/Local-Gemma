cmake_minimum_required(VERSION 3.16)
project(localgemma VERSION 0.1.0 LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Build llama.cpp as a static library
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory(vendor/llama.cpp)

# JNI bridge shared library
add_library(localgemma SHARED src/main/cpp/llama_jni.cpp)

target_link_libraries(localgemma PRIVATE llama)
target_include_directories(localgemma PRIVATE vendor/llama.cpp/include)

# Platform-specific library naming is handled by CMake automatically:
#   Linux   -> liblocalgemma.so
#   macOS   -> liblocalgemma.dylib
#   Windows -> localgemma.dll
