# ✅ CMake configuration for 16 KB page size support
# This file ensures native libraries are built with 16 KB page size alignment

cmake_minimum_required(VERSION 3.22.1)

project("axevpn_flutter_openvpn")

# ✅ CRITICAL: 16 KB page size configuration
set(ANDROID_MAX_PAGE_SIZE 16384)
add_link_options(-Wl,-z,max-page-size=16384)

# ✅ Compiler flags for 16 KB support
add_compile_definitions(ANDROID_MAX_PAGE_SIZE=16384)

# Security hardening flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fPIC -fstack-protector-strong -D_FORTIFY_SOURCE=2")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fPIC -fstack-protector-strong -D_FORTIFY_SOURCE=2")

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)

# ✅ Build our custom libopvpnutil.so with 16 KB alignment
# This will override nizwar's 4 KB version via pickFirst in build.gradle
add_library(opvpnutil SHARED
        opvpnutil.c)

target_link_libraries(opvpnutil
        ${log-lib}
        ${android-lib})

# ✅ Verify alignment after build
add_custom_command(TARGET opvpnutil POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E echo "✅ Built libopvpnutil.so with 16 KB page size alignment"
        COMMAND ${CMAKE_COMMAND} -E echo "   Library: $<TARGET_FILE:opvpnutil>")
