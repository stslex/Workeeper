#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define TRACE_PREFIX "WORKEEPER_EPOCH_SYNC "
#define INTERPOSE(replacement, original) \
    __attribute__((used)) static struct { const void *replace; const void *replacee; } \
    interpose_##original __attribute__((section("__DATA,__interpose"))) = { \
        (const void *)&replacement, (const void *)&original \
    }

static unsigned long sequence;
static const char *nonce;

static void quoted(const char *value) {
    fputc('"', stderr);
    for (const unsigned char *p = (const unsigned char *)value; *p; ++p) {
        if (*p == '"' || *p == '\\') fprintf(stderr, "\\%c", *p);
        else if (*p < 32) fprintf(stderr, "\\u%04x", *p);
        else fputc(*p, stderr);
    }
    fputc('"', stderr);
}

static int scoped(const char *path) {
    const char *part = strstr(path, "/WorkeeperInstallation");
    return part && (part[22] == '/' || part[22] == '\0');
}

static void event_with_flags(const char *operation, const char *path, const char *target,
                             const struct stat *info, int result, int failure, int flags) {
    flockfile(stderr);
    fprintf(stderr, TRACE_PREFIX "{\"sequence\":%lu,\"pid\":%d,\"nonce\":", ++sequence, getpid());
    quoted(nonce ? nonce : "");
    fprintf(stderr, ",\"operation\":");
    quoted(operation);
    fprintf(stderr, ",\"path\":");
    quoted(path);
    fprintf(stderr, ",\"target\":");
    quoted(target);
    fprintf(stderr, ",\"device\":%llu,\"inode\":%llu,\"size\":%lld,\"directory\":%s,\"regular\":%s,"
                    "\"result\":%d,\"errno\":%d,\"flags\":%d,\"create\":%s,\"exclusive\":%s,\"no_follow\":%s}\n",
            (unsigned long long)info->st_dev, (unsigned long long)info->st_ino,
            (long long)info->st_size, S_ISDIR(info->st_mode) ? "true" : "false",
            S_ISREG(info->st_mode) ? "true" : "false", result, failure, flags,
            flags & O_CREAT ? "true" : "false", flags & O_EXCL ? "true" : "false",
            flags & O_NOFOLLOW ? "true" : "false");
    funlockfile(stderr);
}

static void event(const char *operation, const char *path, const char *target,
                  const struct stat *info, int result, int failure) {
    event_with_flags(operation, path, target, info, result, failure, 0);
}

__attribute__((constructor)) static void activate(void) {
    nonce = getenv("WORKEEPER_EPOCH_SYNC_NONCE");
    const struct stat empty = {0};
    event("activate", "", "", &empty, 0, 0);
}

static int descriptor_path(int fd, char *path) {
    if (fd == AT_FDCWD) return getcwd(path, PATH_MAX) ? 0 : -1;
    return fcntl(fd, F_GETPATH, path);
}

static int child_path(int fd, const char *name, char *path) {
    if (name[0] == '/') return snprintf(path, PATH_MAX, "%s", name) < PATH_MAX ? 0 : -1;
    char parent[PATH_MAX];
    if (descriptor_path(fd, parent) != 0) return -1;
    return snprintf(path, PATH_MAX, "%s/%s", parent, name) < PATH_MAX ? 0 : -1;
}

static int observe_openat(int fd, const char *name, int flags, ...) {
    char path[PATH_MAX] = "";
    struct stat info = {0};
    int relevant = child_path(fd, name, path) == 0 && scoped(path);
    int result;
    if (flags & O_CREAT) {
        va_list arguments;
        va_start(arguments, flags);
        int mode = va_arg(arguments, int);
        va_end(arguments);
        result = openat(fd, name, flags, mode);
    } else {
        result = openat(fd, name, flags);
    }
    int failure = errno;
    if (relevant) {
        if (result >= 0) fstat(result, &info);
        event_with_flags("openat", path, "", &info, result, result < 0 ? failure : 0, flags);
    }
    errno = failure;
    return result;
}

static ssize_t observe_write(int fd, const void *bytes, size_t count) {
    char path[PATH_MAX] = "";
    struct stat info = {0};
    int relevant = descriptor_path(fd, path) == 0 && scoped(path);
    ssize_t result = write(fd, bytes, count);
    int failure = errno;
    if (relevant && fstat(fd, &info) == 0) {
        event("write", path, "", &info, (int)result, result < 0 ? failure : 0);
    }
    errno = failure;
    return result;
}

static int observe_sync(int fd) {
    char path[PATH_MAX] = "";
    struct stat info = {0};
    int relevant = descriptor_path(fd, path) == 0 && scoped(path) && fstat(fd, &info) == 0;
    int result = fsync(fd);
    int failure = errno;
    if (relevant) event("fsync", path, "", &info, result, result ? failure : 0);
    errno = failure;
    return result;
}

static int observe_publication(int oldfd, const char *oldname, int newfd, const char *newname, int flags) {
    char path[PATH_MAX] = "", target[PATH_MAX] = "";
    struct stat info = {0};
    int relevant = child_path(oldfd, oldname, path) == 0 && child_path(newfd, newname, target) == 0 &&
                   scoped(path) && scoped(target) && fstatat(oldfd, oldname, &info, AT_SYMLINK_NOFOLLOW) == 0;
    int result = linkat(oldfd, oldname, newfd, newname, flags);
    int failure = errno;
    if (relevant) event("linkat", path, target, &info, result, result ? failure : 0);
    errno = failure;
    return result;
}

static int observe_cleanup(int fd, const char *name, int flags) {
    char path[PATH_MAX] = "";
    struct stat info = {0};
    int relevant = child_path(fd, name, path) == 0 && scoped(path) &&
                   fstatat(fd, name, &info, AT_SYMLINK_NOFOLLOW) == 0;
    int result = unlinkat(fd, name, flags);
    int failure = errno;
    if (relevant) event("unlinkat", path, "", &info, result, result ? failure : 0);
    errno = failure;
    return result;
}

INTERPOSE(observe_openat, openat);
INTERPOSE(observe_write, write);
INTERPOSE(observe_sync, fsync);
INTERPOSE(observe_publication, linkat);
INTERPOSE(observe_cleanup, unlinkat);
