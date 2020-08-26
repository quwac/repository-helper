package io.github.quwac.repositoryhelper.daowrap

import kotlinx.coroutines.flow.Flow

interface NoQueryCacheDaoWrapper<ENTITY, READ_RESULT> {
    fun selectFlow(): Flow<READ_RESULT>
    suspend fun selectRaw(): ENTITY?
    suspend fun upsert(entity: ENTITY)
    suspend fun deleteAll()
}

fun <ENTITY, READ_RESULT> NoQueryCacheDaoWrapper<ENTITY, READ_RESULT>.toCacheDaoWrapper() =
    object : CacheDaoWrapper<Unit, ENTITY, READ_RESULT> {
        override fun selectFlow(query: Unit): Flow<READ_RESULT> {
            return (this@toCacheDaoWrapper).selectFlow()
        }

        override suspend fun selectRaw(query: Unit): ENTITY? {
            return (this@toCacheDaoWrapper).selectRaw()
        }

        override suspend fun upsert(entity: ENTITY) {
            return (this@toCacheDaoWrapper).upsert(entity)
        }

        override suspend fun deleteByQuery(query: Unit) {
            (this@toCacheDaoWrapper).deleteAll()
        }

    }