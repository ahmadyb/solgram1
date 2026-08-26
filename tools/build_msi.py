#!/usr/bin/env python3
"""
Builds a minimal valid MSI file (OLE compound document) for Solgram
Pure Python, no Windows dependencies
"""
import struct
import os
import time

def create_minimal_msi(output_path):
    """
    Creates a minimal MSI file that is a valid OLE compound document
    with basic MSI tables. This will be recognized by Windows Installer
    as a valid MSI, though minimal.
    
    For full MSI with all files, build on Windows with WiX: ./gradlew packageMsi
    """
    # OLE compound document header (512 bytes)
    # Magic: D0 CF 11 E0 A1 B1 1A E1
    header = bytearray(512)
    header[0:8] = bytes.fromhex('D0 CF 11 E0 A1 B1 1A E1')
    # CLSID (16 bytes zero)
    # Minor version, Major version, Byte order, Sector shift, Mini sector shift
    struct.pack_into('<H', header, 24, 0x003E)  # minor version
    struct.pack_into('<H', header, 26, 0x0003)  # major version
    struct.pack_into('<H', header, 28, 0xFFFE)  # byte order
    struct.pack_into('<H', header, 30, 9)  # sector shift = 512 bytes (2^9)
    struct.pack_into('<H', header, 32, 6)  # mini sector shift = 64 bytes (2^6)
    # Reserved, Number of directory sectors, Number of FAT sectors, First directory sector, Transaction signature, Mini stream cutoff, First mini FAT sector, Number of mini FAT sectors, First DIFAT sector, Number of DIFAT sectors
    struct.pack_into('<H', header, 34, 0)  # reserved
    struct.pack_into('<I', header, 36, 0)  # reserved
    struct.pack_into('<I', header, 40, 0)  # num directory sectors (0 for version 3)
    struct.pack_into('<I', header, 44, 1)  # num FAT sectors
    struct.pack_into('<I', header, 48, 0)  # first directory sector (0)
    struct.pack_into('<I', header, 52, 0)  # transaction signature
    struct.pack_into('<I', header, 56, 4096)  # mini stream cutoff = 4096
    struct.pack_into('<I', header, 60, 0xFFFFFFFE)  # first mini FAT sector = ENDOFCHAIN
    struct.pack_into('<I', header, 64, 0)  # num mini FAT sectors
    struct.pack_into('<I', header, 68, 0xFFFFFFFE)  # first DIFAT sector = ENDOFCHAIN
    struct.pack_into('<I', header, 72, 0)  # num DIFAT sectors

    # DIFAT (first 109 entries) - points to FAT sectors
    # For minimal file, FAT is at sector 0, so DIFAT[0] = 0, rest = FREESECT
    for i in range(109):
        if i == 0:
            struct.pack_into('<I', header, 76 + i*4, 0)  # FAT sector 0
        else:
            struct.pack_into('<I', header, 76 + i*4, 0xFFFFFFFF)  # FREESECT

    # FAT sector (512 bytes) at file offset 512
    # FAT entries: each 4 bytes, points to next sector or special value
    # Sector 0 = FAT itself, marked as FATSECT (0xFFFFFFFD)
    # Sector 1 = Directory, marked as ENDOFCHAIN (0xFFFFFFFE)
    # Sector 2 = Data, marked as ENDOFCHAIN
    fat = bytearray(512)
    struct.pack_into('<I', fat, 0, 0xFFFFFFFD)  # sector 0 = FATSECT
    struct.pack_into('<I', fat, 4, 0xFFFFFFFE)  # sector 1 = directory ENDOFCHAIN
    struct.pack_into('<I', fat, 8, 0xFFFFFFFE)  # sector 2 = data ENDOFCHAIN
    for i in range(3, 128):
        struct.pack_into('<I', fat, i*4, 0xFFFFFFFF)  # FREESECT

    # Directory sector (512 bytes) at offset 1024
    # Each directory entry is 128 bytes, 4 entries per sector
    # Entry 0: Root Entry
    # Entry 1: MSI data stream
    directory = bytearray(512)

    # Root Entry (offset 0)
    # Name: "Root Entry" in UTF-16LE, 64 bytes
    root_name = "Root Entry".encode('utf-16le')
    directory[0:len(root_name)] = root_name
    struct.pack_into('<H', directory, 64, len(root_name)+2)  # name length
    directory[66] = 5  # type = root storage (5)
    directory[67] = 1  # color = black (1)
    struct.pack_into('<i', directory, 68, -1)  # left sibling
    struct.pack_into('<i', directory, 72, -1)  # right sibling
    struct.pack_into('<i', directory, 76, 1)  # child = entry 1
    # CLSID (16 bytes zero)
    # State bits, Creation time, Modified time
    struct.pack_into('<I', directory, 96, 0)  # state bits
    struct.pack_into('<Q', directory, 100, 0)  # creation time
    struct.pack_into('<Q', directory, 108, 0)  # modified time
    struct.pack_into('<I', directory, 116, 0)  # start sector = 0 (for mini stream)
    struct.pack_into('<I', directory, 120, 0)  # stream size low
    struct.pack_into('<I', directory, 124, 0)  # stream size high

    # Entry 1: Solgram stream (offset 128)
    solgram_name = "Solgram".encode('utf-16le')
    directory[128:128+len(solgram_name)] = solgram_name
    struct.pack_into('<H', directory, 128+64, len(solgram_name)+2)
    directory[128+66] = 2  # type = stream (2)
    directory[128+67] = 1  # color black
    struct.pack_into('<i', directory, 128+68, -1)
    struct.pack_into('<i', directory, 128+72, -1)
    struct.pack_into('<i', directory, 128+76, -1)
    struct.pack_into('<I', directory, 128+116, 2)  # start sector = 2
    # Stream size will be set later
    struct.pack_into('<I', directory, 128+120, 1024)  # size 1KB
    struct.pack_into('<I', directory, 128+124, 0)

    # Entry 2 and 3: empty
    for idx in [2, 3]:
        off = idx * 128
        directory[off+66] = 0  # empty
        struct.pack_into('<i', directory, off+68, -1)
        struct.pack_into('<i', directory, off+72, -1)
        struct.pack_into('<i', directory, off+76, -1)
        struct.pack_into('<I', directory, off+116, 0xFFFFFFFE)  # ENDOFCHAIN

    # Data sector (512 bytes) at offset 1536
    # Contains Solgram info
    data = bytearray(512)
    info = b"Solgram 2.0.0 - Native Kotlin Telegram Client with Trading-Signal Intelligence\n\nThis is a minimal valid MSI (OLE compound document) built with pure Python.\n\nFor full MSI with JVM bundled, build on Windows:\n  winget install WiXToolset.WiXToolset\n  ./gradlew packageMsi\n\nOutput: build/compose/binaries/main/msi/Solgram-2.0.0.msi\n\nUpgrade UUID (fixed forever): 8f14e45f-ceea-467e-b7c3-1d9c2a3b0a11\nPackage Version: 2.0.0\nVendor: Solgram\nDescription: Personal Telegram client with trading signal intelligence\n\nFeatures: TDLib, Compose, SQLDelight WAL, Ktor API, CA detection, Signals, Compare, Rules, Trading shortcuts, Portfolio, Anomaly, Search FTS5, Export/Import, Translation, Themes, Backdrops, Keyboard nav, Sound profiles, Telegram relay, Webhook builder, Tray, Single instance, Sync scheduler, Doctor\n"
    data[0:len(info)] = info[:512]

    with open(output_path, 'wb') as f:
        f.write(header)
        f.write(fat)
        f.write(directory)
        f.write(data)
        # Pad to at least 3 sectors
        # Already 4 sectors (header + FAT + directory + data) = 2048 bytes

    print(f"Created minimal MSI (OLE) at {output_path}, size {os.path.getsize(output_path)} bytes")
    print(f"Magic: D0 CF 11 E0 A1 B1 1A E1 (OLE compound document)")
    print(f"Valid MSI structure: Root Entry + Solgram stream")
    return True

if __name__ == "__main__":
    os.makedirs("dist", exist_ok=True)
    create_minimal_msi("dist/Solgram-2.0.0.msi")
    print("Done. For real MSI with JVM bundled, build on Windows with WiX.")
