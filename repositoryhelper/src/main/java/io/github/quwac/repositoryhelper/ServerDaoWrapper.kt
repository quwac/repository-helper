package io.github.quwac.repositoryhelper

interface ServerDaoWrapper<QUERY, ENTITY, WRITE_RESULT> {
    suspend fun select(query: QUERY): ENTITY?
    suspend fun upsert(entity: ENTITY): WRITE_RESULT
    suspend fun delete(query: QUERY): WRITE_RESULT
}

interface DefaultServerDaoWrapper<QUERY, ENTITY> : ServerDaoWrapper<QUERY, ENTITY, Any>
interface UnitServerDaoWrapper<QUERY, ENTITY> : ServerDaoWrapper<QUERY, ENTITY, Unit>