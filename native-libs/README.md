# Native Libs

Place `libtdjson.dll` and its dependencies here:
- libtdjson.dll (TDLib)
- zlib1.dll
- libssl-3-x64.dll
- libcrypto-3-x64.dll

These are placed alongside the packaged executable via `appResourcesRootDir`.

On development without TDLib, the app runs in mock mode (set -Dsolgram.mock=true or absence of dll triggers mock).

Download TDLib from https://github.com/tdlib/td

Build steps for Windows:
```
# Build TDLib for Windows
# See https://tdlib.github.io/td/build.html?language=Java
```

The file `libtdjson.dll` is the ONE non-Kotlin binary in the stack, isolated entirely behind a single interface (TdLibEngine.kt).
