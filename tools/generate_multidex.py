#!/usr/bin/env python3
import sys
import os
import zipfile
import hashlib
import zlib
import struct

def main():
    if len(sys.argv) < 3:
        print("Usage: generate_multidex.py <template_dex> <apk_path> [count=105]")
        return 1

    template_path = sys.argv[1]
    apk_path = sys.argv[2]
    count = int(sys.argv[3]) if len(sys.argv) > 3 else 105

    if not os.path.exists(template_path):
        print(f"Error: {template_path} not found")
        return 1

    with open(template_path, 'rb') as f:
        tmpl = bytearray(f.read())

    print(f"Loaded template DEX: {len(tmpl)} bytes")

    # Generate classes2.dex ... classes{count}.dex
    dex_dict = {}
    for i in range(2, count + 1):
        cur = bytearray(tmpl)
        target = b"D000"
        replacement = f"D{i:03d}".encode()
        if target in cur:
            idx = cur.index(target)
            cur[idx:idx+len(target)] = replacement
        else:
            cur[-4:] = struct.pack('<I', i)

        # Recalculate SHA-1
        sig = hashlib.sha1(cur[32:]).digest()
        cur[12:32] = sig
        # Recalculate Adler32
        chk = zlib.adler32(cur[12:]) & 0xffffffff
        cur[8:12] = struct.pack('<I', chk)

        dex_dict[f"classes{i}.dex"] = bytes(cur)

    print(f"Generated {len(dex_dict)} additional DEX files")

    # Add to APK preserving existing compression flags
    temp_apk = apk_path + ".tmp"
    with zipfile.ZipFile(apk_path, 'r') as zin, zipfile.ZipFile(temp_apk, 'w') as zout:
        for item in zin.infolist():
            # Don't re-copy signatures because apksigner will sign after
            if item.filename.startswith('META-INF/') and (item.filename.endswith('.SF') or item.filename.endswith('.RSA') or item.filename.endswith('.MF')):
                continue
            data = zin.read(item.filename)
            zout.writestr(item, data)
        for name, data in dex_dict.items():
            zinfo = zipfile.ZipInfo(filename=name)
            zinfo.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(zinfo, data)

    os.replace(temp_apk, apk_path)
    print(f"Successfully added {len(dex_dict)} DEX files to {apk_path}")
    return 0

if __name__ == '__main__':
    sys.exit(main())
