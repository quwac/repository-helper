package io.github.quwac.repositoryhelper.daowrap

import kotlinx.coroutines.flow.Flow

interface CacheDaoWrapper<QUERY, ENTITY, READ_RESULT> {
    fun selectFlow(query: QUERY): Flow<READ_RESULT>
    suspend fun selectRaw(query: QUERY): ENTITY?
    suspend fun upsert(entity: ENTITY)
    suspend fun deleteByQuery(query: QUERY)
}
