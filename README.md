# **mkudfimg - UDF Disk Image Creator**

**mkudfimg** is a Java-based command-line tool for creating **UDF (Universal Disk Format)** disk images from directories and files. It supports both **CD (2048-byte blocks)** and **HD (512-byte blocks)** formats, with features like symbolic links, duplicate file detection, manifest generation, and space optimization.

Perfect for software distribution images, or any application requiring UDF-formatted disks.

## ☕ Support

If you find this project helpful, consider supporting me:

## [![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/B0B01E8SY7)

## **Features**

✅ **UDF Compliance** – Generates standard-compliant UDF images  
✅ **Flexible Block Sizes** – Supports **2048-byte (CD/DVD)** and **512-byte (HD/USB)** modes  
✅ **Duplicate File Handling** – Optionally hard-links identical files to save space  
✅ **Manifest Generation** – Creates checksums for file integrity verification  
✅ **Symbolic Link Support** – Follows or preserves symlinks as needed  
✅ **Space Optimization** – `--compact` reduces padding, `--trim-empty` removes unused files  
✅ **Volume Labeling** – Customize the disk volume name

---

## **Installation**

### **Prerequisites**

- **Java 17+** (OpenJDK or Oracle JDK)
- **Maven** (for building from source)

### **Build from Source**

```sh
git clone https://github.com/jet-logic/mkudfimg.git
cd mkudfimg
mvn package
```

This generates `mkudfimg.jar` in the `target/` directory.

### **Run Directly**

- **Linux/Mac**:
  ```sh
  ./bin/mkudfimg [OPTIONS] [DIRECTORY...] -o OUTPUT_IMAGE
  ```
- **Windows**:
  ```cmd
  bin\mkudfimg.cmd [OPTIONS] [DIRECTORY...] -o OUTPUT_IMAGE
  ```

---

## **Usage**

### **Basic Command**

```sh
mkudfimg -o image.iso /path/to/directory
```

Creates a UDF image (`image.iso`) in **CD mode (2048B blocks)**.

### **Common Options**

| Option              | Description                                    |
| ------------------- | ---------------------------------------------- |
| `-o FILE`           | Output image file (required)                   |
| `-V LABEL`          | Set volume label (e.g., `-V "MY_DISK"`)        |
| `--hd`              | Use **512-byte blocks** (HD/USB mode)          |
| `--manifest`        | Generate a checksum manifest (`.etc/checksum`) |
| `--compact`         | Optimize space usage                           |
| `--link-duplicates` | Hard-link duplicate files                      |
| `--trim-empty`      | Remove empty files/directories                 |

### **Examples**

1. **Create a UDF HD Image**

   ```sh
   mkudfimg --hd -o disk.img /my/files
   ```

2. **Optimize Space & Remove Empty Files**
   ```sh
   mkudfimg --compact --trim-empty -o minimal.iso /data
   ```

---

## **Output Format**

The generated UDF image includes:  
✔ Volume descriptors  
✔ File & directory structures  
✔ Optional checksum manifest (if `--manifest` is used)  
✔ Configurable block size (CD/DVD vs. HD/USB)

---

🚀 **Happy imaging!** 🚀
