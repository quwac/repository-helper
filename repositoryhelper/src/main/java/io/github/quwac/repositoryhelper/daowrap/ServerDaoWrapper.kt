package io.github.quwac.repositoryhelper.daowrap

interface ServerDaoWrapper<QUERY, ENTITY>: SelectOnlyServerDaoWrapper<QUERY, ENTITY> {
    suspend fun upsert(entity: ENTITY)
    suspend fun deleteByQuery(query: QUERY)
}
