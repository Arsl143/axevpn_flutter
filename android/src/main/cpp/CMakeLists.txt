# ✅ CMake configuration for 16 KB page size support
# This file ensures native libraries are built with 16 KB page size alignment

cmake_minimum_required(VERSION 3.22.1)

project("axevpn_flutter_openvpn")

# ✅ CRITICAL: 16 KB page size configuration
set(ANDROID_MAX_PAGE_SIZE 16384)
add_link_options(-Wl,-z,max-page-size=16384)

# ✅ Compiler flags for 16 KB support
add_compile_definitions(ANDROID_MAX_PAGE_SIZE=16384)

# Note: This CMakeLists.txt sets linker flags for any native builds
# However, the OpenVPN library uses pre-compiled .so files from JitPack
# which need to be rebuilt at the source with these flags
