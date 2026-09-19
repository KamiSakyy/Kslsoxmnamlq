#!/usr/bin/env python3
import sys
import os
import glob
import shutil
import zipfile
import subprocess
from concurrent.futures import ThreadPoolExecutor

def main():
    if len(sys.argv) < 4:
        print("Usage: build_real_multidex.py <d8_binary> <decoy_source> <output_dir_or_apk> [count=105] [apk2...]")
        return 1

    d8_bin = sys.argv[1]
    decoy_file = sys.argv[2]
    apks = sys.argv[3:]
    count = 105

    apk_targets = []
    for arg in apks:
        if arg.isdigit():
            count = int(arg)
        else:
            apk_targets.append(arg)

    print("=== Real Multi-DEX Generator ===")
    print("D8 binary:", d8_bin)
    print("Decoy source:", decoy_file)
    print(f"Target count: {count} DEX files")
    print("APK targets:", apk_targets)

    if not os.path.exists(d8_bin):
        print(f"Error: d8 not found at {d8_bin}")
        return 1

    if not os.path.exists(decoy_file):
        print(f"Error: decoy file not found at {decoy_file}")
        return 1

    with open(decoy_file, 'r', encoding='utf-8') as f:
        content = f.read()

    content = content.replace("package com.tsuyu.line.r;", "").strip()
    parts = content.split("class ")
    raw_classes = [("class " + p.strip()) for p in parts if p.strip()]
    total_classes = len(raw_classes)
    print(f"Extracted {total_classes} decoy classes")

    num_groups = count - 1
    groups = [[] for _ in range(num_groups)]
    for idx, cls in enumerate(raw_classes):
        groups[idx % num_groups].append(cls)

    work_dir = "/tmp/real_multidex"
    if os.path.exists(work_dir):
        shutil.rmtree(work_dir)
    src_dir = os.path.join(work_dir, "src")
    classes_dir = os.path.join(work_dir, "classes")
    dex_dir = os.path.join(work_dir, "dex")
    os.makedirs(src_dir, exist_ok=True)
    os.makedirs(classes_dir, exist_ok=True)
    os.makedirs(dex_dir, exist_ok=True)

    print("Writing grouped Java source files...")
    for idx, grp in enumerate(groups):
        dex_num = idx + 2
        java_path = os.path.join(src_dir, f"DecoyGroup{dex_num:03d}.java")
        with open(java_path, "w", encoding="utf-8") as f:
            f.write(f"package com.tsuyu.line.r.g{dex_num};\n\n")
            for c in grp:
                f.write(c + "\n")

    print("Compiling all decoy sources with javac...")
    java_files = glob.glob(os.path.join(src_dir, "*.java"))
    javac_cmd = ["javac", "-source", "1.8", "-target", "1.8", "-d", classes_dir] + java_files
    res = subprocess.run(javac_cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"javac failed:\n{res.stderr}")
        return 1
    print("Compilation successful!")

    print(f"Compiling {num_groups} DEX files in parallel with D8...")
    def compile_dex(dex_num):
        group_pkg_dir = os.path.join(classes_dir, "com", "tsuyu", "line", "r", f"g{dex_num}")
        class_files = glob.glob(os.path.join(group_pkg_dir, "*.class"))
        if not class_files:
            return dex_num, None, "No class files found"
        out_dex_sub = os.path.join(dex_dir, f"d{dex_num}")
        os.makedirs(out_dex_sub, exist_ok=True)
        d8_cmd = [d8_bin, "--min-api", "26", "--output", out_dex_sub] + class_files
        run_res = subprocess.run(d8_cmd, capture_output=True, text=True)
        if run_res.returncode != 0:
            return dex_num, None, run_res.stderr
        out_dex_path = os.path.join(out_dex_sub, "classes.dex")
        if not os.path.exists(out_dex_path):
            return dex_num, None, "Output classes.dex missing"
        with open(out_dex_path, "rb") as f:
            data = f.read()
        return dex_num, data, None

    dex_map = {}
    with ThreadPoolExecutor(max_workers=8) as executor:
        futures = [executor.submit(compile_dex, idx + 2) for idx in range(num_groups)]
        for f in futures:
            dex_num, data, err = f.result()
            if err:
                print(f"Error compiling classes{dex_num}.dex: {err}")
                return 1
            dex_map[f"classes{dex_num}.dex"] = data

    print(f"Successfully generated {len(dex_map)} DEX files!")
    sizes = [len(v) for v in dex_map.values()]
    print(f"DEX size range: min={min(sizes)} bytes, max={max(sizes)} bytes, avg={sum(sizes)//len(sizes)} bytes")

    for apk_path in apk_targets:
        if not os.path.exists(apk_path):
            print(f"Warning: target APK {apk_path} does not exist, skipping")
            continue
        print(f"Injecting DEX files into {apk_path}...")
        temp_apk = apk_path + ".tmp"
        with zipfile.ZipFile(apk_path, "r") as zin, zipfile.ZipFile(temp_apk, "w") as zout:
            for item in zin.infolist():
                if item.filename.startswith("classes") and item.filename.endswith(".dex") and item.filename != "classes.dex":
                    continue
                if item.filename.startswith("META-INF/") and (item.filename.endswith(".SF") or item.filename.endswith(".RSA") or item.filename.endswith(".MF")):
                    continue
                data = zin.read(item.filename)
                zout.writestr(item, data)

            for dex_name in sorted(dex_map.keys(), key=lambda x: int(x.replace("classes", "").replace(".dex", ""))):
                zinfo = zipfile.ZipInfo(filename=dex_name)
                zinfo.compress_type = zipfile.ZIP_DEFLATED
                zout.writestr(zinfo, dex_map[dex_name])

        os.replace(temp_apk, apk_path)
        print(f"Successfully updated {apk_path} with {len(dex_map)} real secondary DEX files!")

    return 0

if __name__ == "__main__":
    sys.exit(main())
