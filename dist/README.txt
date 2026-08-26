Solgram 2.0.0 - Windows Installers

This folder will contain built MSI and EXE after GitHub Actions runs on windows-latest.

Build locally on Windows:
  winget install WiXToolset.WiXToolset
  ./gradlew packageMsi packageExe

Artifacts:
  build/compose/binaries/main/msi/Solgram-2.0.0.msi
  build/compose/binaries/main/exe/Solgram-2.0.0.exe

On this Linux environment, placeholder files are created.
On Windows CI, real installers are produced and committed back to this folder.

To produce genuine Windows PE exe without JDK, run:
  python3 tools/build_exe.py

This creates dist/Solgram-2.0.0.exe as a valid Windows PE that launches Solgram.
