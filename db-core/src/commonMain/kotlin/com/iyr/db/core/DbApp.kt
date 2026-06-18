package com.iyr.db.core

/**
 * Punto de inicialización del SDK (equivalente a `FirebaseApp`).
 *
 * Se inicializa una sola vez al boot de cada cliente con las [DbOptions] del
 * gateway. Los servicios (db-database, etc.) lo obtienen vía [getInstance].
 *
 * No usa DI interno: singletons estilo Firebase para mantener la misma forma
 * de uso que firebase-kmp-sdk y minimizar el cambio en los actuals de los
 * clientes KMP que lo adopten.
 */
class DbApp private constructor(
    val name: String,
    val options: DbOptions,
) {
    companion object {
        private const val DEFAULT_NAME = "[DEFAULT]"
        private val apps = mutableMapOf<String, DbApp>()

        fun initialize(options: DbOptions, name: String = DEFAULT_NAME): DbApp {
            val app = DbApp(name, options)
            apps[name] = app
            return app
        }

        fun getInstance(name: String = DEFAULT_NAME): DbApp =
            apps[name] ?: error("DbApp '$name' no inicializada. Llamá a DbApp.initialize(options) al boot.")

        fun getApps(): List<DbApp> = apps.values.toList()

        fun isInitialized(name: String = DEFAULT_NAME): Boolean = apps.containsKey(name)
    }
}
