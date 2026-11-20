if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
    for candidate in \
        "$HOME"/.jdk/temurin-17/*/Contents/Home \
        "$HOME"/Library/Java/JavaVirtualMachines/*/Contents/Home \
        /Library/Java/JavaVirtualMachines/*/Contents/Home; do
        if [[ -x "$candidate/bin/java" ]]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
elif ! command -v java >/dev/null 2>&1; then
    printf '%s\n' "Java 17 or newer is required. Install Eclipse Temurin 17 or newer and set JAVA_HOME." >&2
    return 1
fi

java_version=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')
if [[ -z "$java_version" || "$java_version" -lt 17 ]]; then
    printf '%s\n' "Java 17 or newer is required; found Java ${java_version:-unknown}." >&2
    return 1
fi
