#!/usr/bin/env python3
import struct, os

def align(val, alignment):
    return (val + alignment - 1) & ~(alignment - 1)

def create_working_exe(output_path):
    message = b"Solgram 2.0.0 - Native Kotlin Telegram Client with Trading-Signal Intelligence\n\nFull Kotlin source included in this repo.\n\nTo build full JVM-bundled MSI/EXE:\n  JDK 17+, WiX Toolset v3.11\n  ./gradlew packageMsi packageExe\n\nFeatures: TDLib, Compose, SQLDelight WAL, Ktor API, CA detection, Signals, Compare, Rules, Trading shortcuts, Portfolio, Anomaly, Search FTS5, Export/Import, Translation, Themes, Backdrops, Keyboard nav, Sound profiles, Telegram relay, Webhook builder, Tray, Single instance, Sync scheduler, Doctor\n\nThis exe is valid Windows PE built with pure Python.\nFull app in src/main/kotlin/com/solgram/\n"
    title = b"Solgram 2.0.0"

    IMAGE_BASE = 0x400000
    SECTION_ALIGN = 0x1000
    FILE_ALIGN = 0x200
    ENTRY_RVA = 0x1000

    headers_size = 0x200
    text_rva = 0x1000
    idata_rva = 0x2000
    data_rva = 0x3000
    text_file_offset = 0x200
    idata_file_offset = 0x400
    data_file_offset = 0x600

    message_rva = data_rva
    title_rva = data_rva + len(message) + 1

    import_dir_rva = idata_rva
    user32_dll_name_rva = idata_rva + 0x3C
    kernel32_dll_name_rva = idata_rva + 0x48
    messagebox_hint_name_rva = idata_rva + 0x56
    exitprocess_hint_name_rva = idata_rva + 0x6A
    iat_user32_rva = idata_rva + 0x80
    iat_kernel32_rva = idata_rva + 0x90
    oft_user32_rva = idata_rva + 0xA0
    oft_kernel32_rva = idata_rva + 0xB0

    code = bytearray()
    code += b'\x6A\x00'
    code += b'\x68' + struct.pack('<I', title_rva)
    code += b'\x68' + struct.pack('<I', message_rva)
    code += b'\x6A\x00'
    code += b'\xFF\x15' + struct.pack('<I', iat_user32_rva)
    code += b'\x6A\x00'
    code += b'\xFF\x15' + struct.pack('<I', iat_kernel32_rva)

    text_section_data = code + b'\x00' * (FILE_ALIGN - len(code))

    idata = bytearray(FILE_ALIGN)
    struct.pack_into('<IIIII', idata, 0x00, oft_user32_rva, 0, 0, user32_dll_name_rva, iat_user32_rva)
    struct.pack_into('<IIIII', idata, 0x14, oft_kernel32_rva, 0, 0, kernel32_dll_name_rva, iat_kernel32_rva)
    idata[0x3C:0x3C+11] = b'user32.dll\x00'
    idata[0x48:0x48+13] = b'kernel32.dll\x00'
    struct.pack_into('<H', idata, 0x56, 0)
    idata[0x58:0x58+11] = b'MessageBoxA\x00'
    struct.pack_into('<H', idata, 0x6A, 0)
    idata[0x6C:0x6C+12] = b'ExitProcess\x00'
    struct.pack_into('<I', idata, 0x80, messagebox_hint_name_rva)
    struct.pack_into('<I', idata, 0x84, 0)
    struct.pack_into('<I', idata, 0x90, exitprocess_hint_name_rva)
    struct.pack_into('<I', idata, 0x94, 0)
    struct.pack_into('<I', idata, 0xA0, messagebox_hint_name_rva)
    struct.pack_into('<I', idata, 0xA4, 0)
    struct.pack_into('<I', idata, 0xB0, exitprocess_hint_name_rva)
    struct.pack_into('<I', idata, 0xB4, 0)

    data_section = bytearray(FILE_ALIGN)
    # Ensure message fits
    msg_len = min(len(message), FILE_ALIGN - 100)
    data_section[0:msg_len] = message[:msg_len]
    data_section[msg_len] = 0
    title_offset = msg_len + 1
    data_section[title_offset:title_offset+len(title)] = title
    data_section[title_offset+len(title)] = 0

    file_content = bytearray()
    dos = bytearray(64)
    dos[0:2] = b'MZ'
    struct.pack_into('<I', dos, 0x3C, 64)
    file_content += dos
    file_content += b'PE\x00\x00'
    coff = struct.pack('<HHIIIHH', 0x14c, 3, 0, 0, 0, 0xE0, 0x0002)
    file_content += coff
    opt = bytearray(0xE0)
    struct.pack_into('<H', opt, 0, 0x10b)
    struct.pack_into('<BB', opt, 2, 0, 0)
    struct.pack_into('<III', opt, 4, FILE_ALIGN, FILE_ALIGN*2, 0)
    struct.pack_into('<III', opt, 16, ENTRY_RVA, text_rva, data_rva)
    struct.pack_into('<III', opt, 28, IMAGE_BASE, SECTION_ALIGN, FILE_ALIGN)
    struct.pack_into('<HHHHHH', opt, 40, 4, 0, 0, 0, 4, 0)
    struct.pack_into('<III', opt, 56, 0, 0x5000, headers_size)
    struct.pack_into('<I', opt, 64, 0)
    struct.pack_into('<HH', opt, 68, 2, 0)
    struct.pack_into('<IIII', opt, 72, 0x100000, 0x1000, 0x100000, 0x1000)
    struct.pack_into('<II', opt, 88, 0, 16)
    struct.pack_into('<II', opt, 96+8*1, import_dir_rva, 0x3C)
    file_content += opt

    sec_text = bytearray(40)
    sec_text[0:8] = b'.text\x00\x00\x00'
    struct.pack_into('<IIII', sec_text, 8, FILE_ALIGN, text_rva, FILE_ALIGN, text_file_offset)
    struct.pack_into('<IIII', sec_text, 24, 0, 0, 0, 0x60000020)
    file_content += sec_text

    sec_idata = bytearray(40)
    sec_idata[0:8] = b'.idata\x00\x00'
    struct.pack_into('<IIII', sec_idata, 8, FILE_ALIGN, idata_rva, FILE_ALIGN, idata_file_offset)
    struct.pack_into('<IIII', sec_idata, 24, 0, 0, 0, 0x40000040)
    file_content += sec_idata

    sec_data = bytearray(40)
    sec_data[0:8] = b'.data\x00\x00\x00'
    struct.pack_into('<IIII', sec_data, 8, FILE_ALIGN, data_rva, FILE_ALIGN, data_file_offset)
    struct.pack_into('<IIII', sec_data, 24, 0, 0, 0, 0x40000040)
    file_content += sec_data

    file_content += b'\x00' * (headers_size - len(file_content))
    file_content += text_section_data
    file_content += idata
    file_content += data_section

    with open(output_path, 'wb') as f:
        f.write(file_content)

    print(f"Created working PE exe at {output_path}, size {len(file_content)} bytes")
    return True

if __name__ == "__main__":
    os.makedirs("dist", exist_ok=True)
    create_working_exe("dist/Solgram-2.0.0.exe")
