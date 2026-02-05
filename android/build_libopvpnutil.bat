@echo off
REM Build libopvpnutil.so with 16 KB page size alignment using Android NDK
REM This script compiles the C source code directly with proper flags

echo ========================================================================
echo Building Custom libopvpnutil.so with 16 KB Page Size Support
echo ========================================================================

set NDK_PATH=%ANDROID_HOME%\ndk\27.0.12077973
if not exist "%NDK_PATH%\toolchains\llvm\prebuilt\windows-x86_64\bin\clang.cmd" (
    echo ERROR: Android NDK 27 not found at %NDK_PATH%
    echo Please install NDK 27 via Android Studio SDK Manager
    exit /b 1
)

set CC=%NDK_PATH%\toolchains\llvm\prebuilt\windows-x86_64\bin\clang.cmd
set SRC_DIR=%~dp0src\main\cpp
set OUT_DIR=%~dp0src\main\jniLibs

echo.
echo Compiling for arm64-v8a...
%CC% --target=aarch64-linux-android24 ^
    -fPIC -shared ^
    -Wl,-z,max-page-size=16384 ^
    -DANDROID_MAX_PAGE_SIZE=16384 ^
    -fstack-protector-strong ^
    -D_FORTIFY_SOURCE=2 ^
    -llog -landroid ^
    -o "%OUT_DIR%\arm64-v8a\libopvpnutil.so" ^
    "%SRC_DIR%\opvpnutil.c"
if errorlevel 1 (
    echo ERROR: Failed to compile arm64-v8a
    exit /b 1
)
echo   arm64-v8a: OK

echo.
echo Compiling for x86_64...
%CC% --target=x86_64-linux-android24 ^
    -fPIC -shared ^
    -Wl,-z,max-page-size=16384 ^
    -DANDROID_MAX_PAGE_SIZE=16384 ^
    -fstack-protector-strong ^
    -D_FORTIFY_SOURCE=2 ^
    -llog -landroid ^
    -o "%OUT_DIR%\x86_64\libopvpnutil.so" ^
    "%SRC_DIR%\opvpnutil.c"
if errorlevel 1 (
    echo ERROR: Failed to compile x86_64
    exit /b 1
)
echo   x86_64: OK

echo.
echo ========================================================================
echo   BUILD COMPLETE!
echo ========================================================================
echo.
echo Verifying libraries...
for %%A in (arm64-v8a x86_64) do (
    if exist "%OUT_DIR%\%%A\libopvpnutil.so" (
        echo   %%A: Exists
    ) else (
        echo   %%A: MISSING
    )
)

echo.
echo Next step: Commit and push to git, then rebuild AAB
echo ========================================================================
