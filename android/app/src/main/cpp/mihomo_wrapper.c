#include <dlfcn.h>
#include <errno.h>
#include <libgen.h>
#include <limits.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

typedef int (*mihomo_entry_t)(int argc, char **argv);

int main(int argc, char **argv) {
    char self_path[PATH_MAX];
    ssize_t n = readlink("/proc/self/exe", self_path, sizeof(self_path) - 1);
    if (n < 0) {
        fprintf(stderr, "mihomo wrapper: readlink /proc/self/exe: %s\n", strerror(errno));
        return 1;
    }
    self_path[n] = '\0';

    char path_copy[PATH_MAX];
    strncpy(path_copy, self_path, sizeof(path_copy));
    path_copy[sizeof(path_copy) - 1] = '\0';
    const char *dir = dirname(path_copy);

    char lib_path[PATH_MAX];
    if (snprintf(lib_path, sizeof(lib_path), "%s/libmihomo.so", dir) >= (int) sizeof(lib_path)) {
        fprintf(stderr, "mihomo wrapper: lib path too long\n");
        return 1;
    }

    void *handle = dlopen(lib_path, RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        fprintf(stderr, "mihomo wrapper: dlopen %s: %s\n", lib_path, dlerror());
        return 1;
    }

    mihomo_entry_t entry = (mihomo_entry_t) dlsym(handle, "mihomoEntry");
    if (!entry) {
        fprintf(stderr, "mihomo wrapper: dlsym mihomoEntry: %s\n", dlerror());
        return 1;
    }

    return entry(argc, argv);
}
