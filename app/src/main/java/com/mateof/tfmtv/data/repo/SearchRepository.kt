package com.mateof.tfmtv.data.repo

import com.mateof.tfmtv.core.apiCall
import com.mateof.tfmtv.data.api.SearchApi
import com.mateof.tfmtv.data.model.GlobalSearchDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val api: SearchApi,
    private val features: ServerFeatures
) {
    /** Videos by name in every indexed channel; null when the server cannot do it. */
    suspend fun videos(text: String): GlobalSearchDto? = withContext(Dispatchers.IO) {
        if (!features.has(Feature.SEARCH)) null else apiCall { api.search(q = text) }
    }
}
