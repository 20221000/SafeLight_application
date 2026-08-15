package com.example.safelight.ui.search

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safelight.data.net.Network
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PlaceSearch"

/** 검색 결과 한 줄. 웹 PlaceSearchBox 가 만들던 `{ name, address, lat, lng }` 와 같다. */
data class SearchedPlace(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * 헤더 장소 검색. 웹 PlaceSearchBox 의 `kakao.maps.services.Places.keywordSearch` 자리다.
 * 안드로이드 지도 SDK 에는 services 대응물이 없어 Kakao Local REST 로 간다.
 */
class PlaceSearchViewModel : ViewModel() {

    var query by mutableStateOf("")
        private set

    var results by mutableStateOf<List<SearchedPlace>>(emptyList())
        private set

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        query = value
        searchJob?.cancel()
        if (value.isBlank()) {
            results = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            // 웹은 글자마다 바로 조회한다. 여기서는 한 박자 기다린다 —
            // 모바일에서 글자마다 요청을 내보내면 오타 몇 번에 십수 건이 날아간다.
            // 화면에 보이는 결과는 같고 마지막 입력 기준이라 순서가 뒤집힐 일도 없다.
            delay(250)
            results = search(value)
        }
    }

    /** 엔터로 확정. 웹과 같이 목록이 있으면 첫 줄, 없으면 즉시 조회해서 첫 줄을 고른다. */
    fun onSubmit(onPick: (SearchedPlace) -> Unit) {
        results.firstOrNull()?.let { onPick(it); return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            search(query).firstOrNull()?.let(onPick)
        }
    }

    /** 장소를 고르면 입력칸에 이름을 채우고 목록을 접는다(웹 deliver 와 같다). */
    fun onPicked(place: SearchedPlace) {
        searchJob?.cancel()
        query = place.name
        results = emptyList()
    }

    fun clear() {
        searchJob?.cancel()
        query = ""
        results = emptyList()
    }

    private suspend fun search(keyword: String): List<SearchedPlace> = runCatching {
        Network.kakaoLocal.searchKeyword(query = keyword).documents.map {
            SearchedPlace(
                name = it.placeName,
                address = it.roadAddress.ifBlank { it.address },
                latitude = it.y.toDoubleOrNull() ?: 0.0,
                longitude = it.x.toDoubleOrNull() ?: 0.0,
            )
        // 웹도 5개까지만 띄운다.
        }.take(5)
    }.getOrElse {
        if (it is kotlinx.coroutines.CancellationException) throw it
        Log.e(TAG, "장소 검색 실패", it)
        emptyList()
    }
}
