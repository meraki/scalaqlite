#include "Sqlite3.h"
#include <stdio.h>
#include <string.h>
#include <sqlite3.h>

JNIEXPORT jint JNICALL
Java_Sqlite3_sqlite3_1open(JNIEnv *env, jclass cls, jstring jpath, jlongArray jdb)
{
    printf("sqlite3_open\n");
    jboolean iscopy;
    const char *cpath = env->GetStringUTFChars(jpath, &iscopy);
    sqlite3 *db;
    jint r = sqlite3_open(cpath, &db);
    if (r == SQLITE_OK) {
        jlong a[] = {(jlong) db};
        env->SetLongArrayRegion(jdb, 0, 1, a);
    }
    env->ReleaseStringUTFChars(jpath, cpath);
    return r;
}

JNIEXPORT jint JNICALL
Java_Sqlite3_sqlite3_1enable_1load_1extension(JNIEnv *env, jclass cls, jlong jdb, jint onoff)
{
    printf("sqlite3_enable_load_extension\n");
    sqlite3 *db = (sqlite3*) jdb;
    return sqlite3_enable_load_extension(db, onoff);
}

JNIEXPORT jint JNICALL
Java_Sqlite3_sqlite3_1prepare_1v2(
    JNIEnv *env, jclass cls, jlong jdb, jstring jsql, jlongArray jstmt)
{
    printf("sqlite3_prepare_v2\n");
    sqlite3 *db = (sqlite3*) jdb;
    jboolean iscopy;
    const char *csql = env->GetStringUTFChars(jsql, &iscopy);
    sqlite3_stmt *stmt;
    // XXX: does strlen work with UTF8?
    int r = sqlite3_prepare_v2(db, csql, strlen(csql), &stmt, NULL);
    if (r == SQLITE_OK) {
        jlong a[] = {(jlong) stmt};
        env->SetLongArrayRegion(jstmt, 0, 1, a);
    }
    env->ReleaseStringUTFChars(jsql, csql);
    return r;
}
