package ua.ukrtv.app.ui.top200

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.ukrtv.app.data.repository.Top200Repository
import ua.ukrtv.app.domain.model.Top200Movie
import javax.inject.Inject

@HiltViewModel
class Top200ViewModel @Inject constructor(
    private val repository: Top200Repository
) : ViewModel() {
    private val _movies = MutableStateFlow<List<Top200Movie>>(emptyList())
    val movies = _movies.asStateFlow()

    init {
        viewModelScope.launch {
            _movies.value = repository.getTop200()
        }
    }
}
