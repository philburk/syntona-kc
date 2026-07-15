import os

# Dynamically resolve the repository root directory (one level up from the scripts/ directory)
script_dir = os.path.dirname(os.path.abspath(__file__))
repo_dir = os.path.dirname(script_dir)

count = 0
for root, dirs, files in os.walk(repo_dir):
    # Ignore build/gradle cache/vcs dirs
    for d in list(dirs):
        if d in ("build", ".gradle", ".git", ".idea", "kotlin-js-store"):
            dirs.remove(d)
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            lines = content.splitlines()
            new_lines = [line.rstrip() for line in lines]
            new_content = "\n".join(new_lines) + ("\n" if content.endswith("\n") else "")
            if content != new_content:
                with open(path, "w", encoding="utf-8") as f:
                    f.write(new_content)
                print(f"Cleaned trailing whitespace: {os.path.relpath(path, repo_dir)}")
                count += 1
print(f"Done. Cleaned {count} files.")
