package io.github.quwac.repositoryhelper

import kotlinx.coroutines.flow.Flow

typealias SelectFlow<QUERY, READ_RESULT> = (query: QUERY) -> Flow<READ_RESULT>
typealias SelectRaw<QUERY, ENTITY> = suspend (query: QUERY) -> ENTITY?
typealias Upsert<ENTITY, WRITE_RESULT> = suspend (result: ENTITY) -> WRITE_RESULT
typealias Delete<QUERY, WRITE_RESULT> = suspend (query: QUERY) -> WRITE_RESULT
typealias QueryToHash<QUERY> = (query: QUERY) -> String