#define _GNU_SOURCE
#include <limits.h>
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int main(int argc, char **argv) {
    char exe[PATH_MAX];
    ssize_t len = readlink("/proc/self/exe", exe, sizeof(exe)-1);
    if (len < 0) { perror("eLuajs: /proc/self/exe"); return 1; }
    exe[len] = '\0';
    char basebuf[PATH_MAX];
    strncpy(basebuf, exe, sizeof(basebuf));
    char *base = dirname(basebuf);

    char java[PATH_MAX];
    char jar[PATH_MAX];
    snprintf(java, sizeof(java), "%s/runtime/bin/java", base);
    snprintf(jar, sizeof(jar), "%s/app/eLuajs.jar", base);

    int extra = argc > 1 ? argc - 1 : 0;
    char **args = calloc((size_t)extra + 6, sizeof(char*));
    if (!args) return 1;
    int i = 0;
    args[i++] = java;
    args[i++] = "-Xms64m";
    args[i++] = "-Xmx1024m";
    args[i++] = "-jar";
    args[i++] = jar;
    for (int a = 1; a < argc; a++) args[i++] = argv[a];
    args[i] = NULL;
    execv(java, args);
    perror("eLuajs: Java başlatılamadı");
    return 1;
}
