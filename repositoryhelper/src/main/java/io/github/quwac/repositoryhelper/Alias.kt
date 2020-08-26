package io.github.quwac.repositoryhelper

import kotlinx.coroutines.flow.Flow

typealias QueryToHash<QUERY> = (query: QUERY) -> String