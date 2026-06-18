package com.iyr.db.database

/**
 * Contratos de listeners — firmas idénticas a las que hoy usan IANKMP/Operators
 * en `services/firebase/FirebaseRealtimeDatabaseService.kt`, para que el adapter
 * de cada cliente sea un delegado trivial.
 */

interface ValueEventListener {
    fun onDataChange(snapshot: DataSnapshot)
    fun onCancelled(error: DatabaseError)
}

interface ChildEventListener {
    fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?)
    fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?)
    fun onChildRemoved(snapshot: DataSnapshot)
    fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?)
    fun onCancelled(error: DatabaseError)
}

class DatabaseError internal constructor(
    private val code: Int,
    private val message: String,
    val details: String? = null,
) {
    fun getCode(): Int = code
    fun getMessage(): String = message
    fun toException(): Exception = DatabaseException("$message (code=$code)")
}

class DatabaseException(message: String) : Exception(message)

/** Resultado de una operación de escritura (paridad con la fachada actual de IANKMP). */
sealed class DatabaseResult {
    data class Success(val key: String? = null) : DatabaseResult()
    data class Error(val message: String) : DatabaseResult()
}
