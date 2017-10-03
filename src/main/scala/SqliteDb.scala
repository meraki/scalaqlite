// Copyright (c) 2010 Sean C. Rhea <sean.c.rhea@gmail.com>
// All rights reserved.
//
// See the file LICENSE included in this distribution for details.

package org.srhea.scalaqlite

case class SqlException(msg: String) extends RuntimeException(msg)

object SqlValue {
  val charset = java.nio.charset.Charset.forName("UTF-8")
}

abstract class SqlValue {
  def toDouble: Double = throw SqlException(s"$this is not a double")
  def toInt: Int = throw SqlException(s"$this is not an int")
  def toLong: Long = throw SqlException(s"$this is not an long")
  def toBlob: Seq[Byte] = throw SqlException(s"$this is not a blob")
  def isNull = false
  def bindValue(stmt: Long, col: Int): Int
}
case object SqlNull extends SqlValue {
  override def toString = "NULL"
  override def isNull = true
  override def bindValue(stmt: Long, col: Int) = Sqlite3C.bind_null(stmt, col)
}
case class SqlLong(i: Long) extends SqlValue {
  override def toString = i.toString
  override def toDouble = i
  override def toInt =
    if (i <= Integer.MAX_VALUE && i >= Integer.MIN_VALUE) i.toInt else super.toInt
  override def toLong = i
  override def bindValue(stmt: Long, col: Int) = Sqlite3C.bind_int64(stmt, col, i)
}
case class SqlDouble(d: Double) extends SqlValue {
  override def toString = d.toString
  override def toDouble = d
  override def toInt = if (d.round == d) d.toInt else super.toInt
  override def toLong = if (d.round == d) d.toLong else super.toLong
  override def bindValue(stmt: Long, col: Int) = Sqlite3C.bind_double(stmt, col, d)
}
case class SqlBlob(bytes: Seq[Byte]) extends SqlValue {
    override def toBlob = bytes
    override def toString = new String(bytes.toArray)
    override def bindValue(stmt: Long, col: Int) = Sqlite3C.bind_blob(stmt, col, bytes.toArray)
}
case class SqlText(s: String) extends SqlValue {
    override def toString = s
    override def bindValue(stmt: Long, col: Int) = Sqlite3C.bind_text(stmt, col, s.getBytes(SqlValue.charset))
}

class SqliteStatement(db: SqliteDb, private val stmt: Array[Long]) {
    def query[R](params: SqlValue*)(f: Iterator[IndexedSeq[SqlValue]] => R): R = {
        params.foldLeft(1) { (i, param) => param.bindValue(stmt(0), i); i + 1 }
        try f(new SqliteResultIterator(db, stmt(0))) finally Sqlite3C.reset(stmt(0))
    }
    def foreachRow(params: SqlValue*)(f: IndexedSeq[SqlValue] => Unit) {
      query(params:_*) { i => i.foreach { row => f(row) } }
    }
    def execute(params: SqlValue*) { query(params:_*) { i => i.foreach { row => Unit } } }
    def mapRows[R](params: SqlValue*)(f: IndexedSeq[SqlValue] => R) = query(params:_*)(_.map(f).toSeq)
    def getRows(params: SqlValue*) = query(params:_*)(_.toSeq)
    def close = if (stmt(0) != 0) stmt(0) = Sqlite3C.finalize(stmt(0))
}

class SqliteResultIterator(db: SqliteDb, private val stmt: Long)
    extends Iterator[IndexedSeq[SqlValue]]
{
    private var cachedRow: IndexedSeq[SqlValue] = null

    assert(stmt != 0)
    advance()

    private def advance() {
        cachedRow = Sqlite3C.step(stmt) match {
            case Sqlite3C.ROW =>
                (0 until Sqlite3C.column_count(stmt)).map { i =>
                    Sqlite3C.column_type(stmt, i) match {
                        case Sqlite3C.INTEGER => SqlLong(Sqlite3C.column_int64(stmt, i))
                        case Sqlite3C.FLOAT => SqlDouble(Sqlite3C.column_double(stmt, i))
                        case Sqlite3C.TEXT => SqlText(new String(Sqlite3C.column_blob(stmt, i)))
                        case Sqlite3C.BLOB => SqlBlob(Sqlite3C.column_blob(stmt, i))
                        case Sqlite3C.NULL => SqlNull
                        case _ => throw SqlException("unsupported type")
                    }
                }
            case Sqlite3C.DONE | Sqlite3C.OK =>
                null
            case rc =>
                throw SqlException(SqliteDb.errorMessage(db, rc))
        }
    }

    def hasNext = cachedRow != null

    def next: IndexedSeq[SqlValue] = {
        assert(hasNext)
        val result = cachedRow
        advance()
        result
    }
}

object SqliteDb {
    import Sqlite3C._
    final val BaseFlags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE
    val SQLITE_OK = 0
    val SQLITE_ERROR = 1
    val SQLITE_INTERNAL = 2
    val SQLITE_PERM = 3
    val SQLITE_ABORT = 4
    val SQLITE_BUSY = 5
    val SQLITE_LOCKED = 6
    val SQLITE_NOMEM = 7
    val SQLITE_READONLY = 8
    val SQLITE_INTERRUPT = 9
    val SQLITE_IOERR = 10
    val SQLITE_CORRUPT = 11
    val SQLITE_NOTFOUND = 12
    val SQLITE_FULL = 13
    val SQLITE_CANTOPEN = 14
    val SQLITE_PROTOCOL = 15
    val SQLITE_EMPTY = 16
    val SQLITE_SCHEMA = 17
    val SQLITE_TOOBIG = 18
    val SQLITE_CONSTRAINT = 19
    val SQLITE_MISMATCH = 20
    val SQLITE_MISUSE = 21
    val SQLITE_NOLFS = 22
    val SQLITE_AUTH = 23
    val SQLITE_FORMAT = 24
    val SQLITE_RANGE = 25
    val SQLITE_NOTADB = 26
    val SQLITE_NOTICE = 27
    val SQLITE_WARNING = 28
    val SQLITE_ROW = 100
    val SQLITE_DONE = 101
    def errorMessage(db: SqliteDb, code: Int) = {
      val detailed = db.errmsg.trim match { case "" => ""; case m => s"$m\n" }
      s"${detailed}Error code $code (${resultToMessage(code)})"
    }
    def resultToMessage(code: Int) = code match {
        case SQLITE_OK => "Successful result"
        case SQLITE_ERROR => "SQL error or missing database"
        case SQLITE_INTERNAL => "Internal logic error in SQLite"
        case SQLITE_PERM => "Access permission denied"
        case SQLITE_ABORT => "Callback routine requested an abort"
        case SQLITE_BUSY => "The database file is locked"
        case SQLITE_LOCKED => "A table in the database is locked"
        case SQLITE_NOMEM => "A malloc() failed"
        case SQLITE_READONLY => "Attempt to write a readonly database"
        case SQLITE_INTERRUPT => "Operation terminated by sqlite3_interrupt()"
        case SQLITE_IOERR => "Some kind of disk I/O error occurred"
        case SQLITE_CORRUPT => "The database disk image is malformed"
        case SQLITE_NOTFOUND => "Unknown opcode in sqlite3_file_control()"
        case SQLITE_FULL => "Insertion failed because database is full"
        case SQLITE_CANTOPEN => "Unable to open the database file"
        case SQLITE_PROTOCOL => "Database lock protocol error"
        case SQLITE_EMPTY => "Database is empty"
        case SQLITE_SCHEMA => "The database schema changed"
        case SQLITE_TOOBIG => "String or BLOB exceeds size limit"
        case SQLITE_CONSTRAINT => "Abort due to constraint violation"
        case SQLITE_MISMATCH => "Data type mismatch"
        case SQLITE_MISUSE => "Library used incorrectly"
        case SQLITE_NOLFS => "Uses OS features not supported on host"
        case SQLITE_AUTH => "Authorization denied"
        case SQLITE_FORMAT => "Auxiliary database format error"
        case SQLITE_RANGE => "2nd parameter to sqlite3_bind out of range"
        case SQLITE_NOTADB => "File opened that is not a database file"
        case SQLITE_NOTICE => "Notifications from sqlite3_log()"
        case SQLITE_WARNING => "Warnings from sqlite3_log()"
        case SQLITE_ROW => "sqlite3_step() has another row ready"
        case SQLITE_DONE => "sqlite3_step() has finished executing"
        case _ => "unknown error"
    }
}

class SqliteDb(path: String, flags: Int = SqliteDb.BaseFlags) {
    private val db = Array(0L)
    Sqlite3C.open_v2(path, db, flags, null) ensuring (_ == Sqlite3C.OK, errmsg)
    def close() {
        assert(db(0) != 0, "already closed")
        Sqlite3C.close(db(0)) ensuring (_ == Sqlite3C.OK, errmsg)
        db(0) = 0
    }
    override def finalize() { if (db(0) != 0) Sqlite3C.close(db(0)) }
    def prepare[R](sql: String)(f: SqliteStatement => R): R = {
        assert(db(0) != 0, "db is closed")
        val stmtPointer = Array(0L)
        val rc = Sqlite3C.prepare_v2(db(0), sql, stmtPointer)
        if (rc != Sqlite3C.OK) throw SqlException(SqliteDb.errorMessage(this, rc))
        val stmt = new SqliteStatement(this, stmtPointer)
        try f(stmt) finally stmt.close
    }
    def query[R](sql: String, params: SqlValue*)(f: Iterator[IndexedSeq[SqlValue]] => R): R =
      prepare(sql)(_.query(params:_*)(f))
    def foreachRow(sql: String, params: SqlValue*)(f: IndexedSeq[SqlValue] => Unit) =
      prepare(sql)( _.foreachRow(params:_*)(f))
    def mapRows[R](sql: String, params: SqlValue*)(f: IndexedSeq[SqlValue] => R) =
      prepare(sql)(_.mapRows(params:_*)(f))
    def getRows(sql: String, params: SqlValue*) = prepare(sql)(_.getRows(params:_*))
    def execute(sql: String, params: SqlValue*) { prepare(sql)(_.execute(params:_*)) }
    def enableLoadExtension(on: Boolean) {
        Sqlite3C.enable_load_extension(db(0), if (on) 1 else 0)
    }
    def errmsg: String = if (db(0) == 0) "db not open" else Sqlite3C.errmsg(db(0))
    def changeCount: Int = Sqlite3C.sqlite3_changes(db(0))
}
