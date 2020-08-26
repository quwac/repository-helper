package io.github.quwac.repositoryhelper.daowrap

interface SelectOnlyServerDaoWrapper<QUERY, ENTITY> {
    suspend fun select(query: QUERY): ENTITY?
}

fun <QUERY, ENTITY> SelectOnlyServerDaoWrapper<QUERY, ENTITY>.toServerDaoWrapper()
    = object : ServerDaoWrapper<QUERY, ENTITY> {
    override suspend fun upsert(entity: ENTITY) {
        throw IllegalStateException("Cannot use upsert. Use ServerDaoWrapper.")
    }

    override suspend fun deleteByQuery(query: QUERY) {
        throw IllegalStateException("Cannot use deleteByQuery. Use ServerDaoWrapper.")
    }

    override suspend fun select(query: QUERY): ENTITY? {
        return select(query)
    }

}