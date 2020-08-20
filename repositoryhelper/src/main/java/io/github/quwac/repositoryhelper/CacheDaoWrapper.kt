package io.github.quwac.repositoryhelper

import kotlinx.coroutines.flow.Flow

interface CacheDaoWrapper<QUERY, ENTITY, READ_RESULT, WRITE_RESULT> {
    fun selectFlow(query: QUERY): Flow<READ_RESULT>
    suspend fun selectRaw(query: QUERY): ENTITY?
    suspend fun upsert(entity: ENTITY): WRITE_RESULT
    suspend fun delete(query: QUERY): WRITE_RESULT
}

interface DefaultCacheDaoWrapper<QUERY, ENTITY, READ_RESULT> :
    CacheDaoWrapper<QUERY, ENTITY, READ_RESULT, Any>

interface UnitCacheDaoWrapper<QUERY, ENTITY, READ_RESULT> :
    CacheDaoWrapper<QUERY, ENTITY, READ_RESULT, Unit>