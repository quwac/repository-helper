package io.github.quwac.repositoryhelper.daowrap

interface NoQuerySelectOnlyServerDaoWrapper<ENTITY> {
    suspend fun select(): ENTITY?
}

fun <ENTITY> NoQuerySelectOnlyServerDaoWrapper<ENTITY>.toServerDaoWrapper() =
    object : ServerDaoWrapper<Unit, ENTITY> {
        override suspend fun select(query: Unit): ENTITY? {
            return (this@toServerDaoWrapper).select()
        }

        override suspend fun upsert(entity: ENTITY) {
            throw IllegalStateException("Cannot call upsert. Use ServerDaoWrapper.")
        }

        override suspend fun deleteByQuery(query: Unit) {
            throw IllegalStateException("Cannot call deleteByQuery. Use ServerDaoWrapper.")
        }

    }