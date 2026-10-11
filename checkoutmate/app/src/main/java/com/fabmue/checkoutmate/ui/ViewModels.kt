package com.fabmue.checkoutmate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabmue.checkoutmate.AppContainer
import com.fabmue.checkoutmate.CheckoutMateApp
import com.fabmue.checkoutmate.data.MatchSerializer
import com.fabmue.checkoutmate.data.Settings
import com.fabmue.checkoutmate.domain.Dart
import com.fabmue.checkoutmate.domain.MatchEvent
import com.fabmue.checkoutmate.domain.X01Config
import com.fabmue.checkoutmate.domain.X01Engine
import com.fabmue.checkoutmate.domain.X01Match
import com.fabmue.checkoutmate.domain.training.TrainingGame
import com.fabmue.checkoutmate.domain.training.TrainingType
import com.fabmue.checkoutmate.domain.training.newTrainingGame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun rememberContainer(): AppContainer {
    val app = LocalContext.current.applicationContext as CheckoutMateApp
    return app.container
}

@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    crossinline create: (AppContainer) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as CheckoutMateApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container) } })
}

/** What to show on the checkout line. */
data class CheckoutInfo(
    val path: List<String>?,
    val locked: Boolean,
    val bogey: Boolean,
    val score: Int,
)

class MatchViewModel(private val container: AppContainer) : ViewModel() {

    private val defaultConfig = X01Config(listOf("Player 1"), 501, 3, 1, false, true)

    val config: X01Config
    private val undo = ArrayDeque<X01Match>()

    val match: MutableStateFlow<X01Match>
    val pending = MutableStateFlow<List<Dart>>(emptyList())
    val canUndo = MutableStateFlow(false)
    val events = MutableSharedFlow<MatchEvent>(extraBufferCapacity = 8)

    val settings: StateFlow<Settings> =
        container.prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val entitlements = container.entitlements.entitlements

    init {
        val initial = container.pendingResume
            ?: container.pendingNewConfig?.let { X01Engine.start(it) }
            ?: X01Engine.start(defaultConfig)
        container.pendingResume = null
        container.pendingNewConfig = null
        config = initial.config
        match = MutableStateFlow(initial)
        persist()
    }

    val checkoutInfo: StateFlow<CheckoutInfo?> =
        combine(match, pending, settings, entitlements) { m, pend, s, ent ->
            if (m.finished) return@combine null
            val projected = X01Engine.preview(m, pend)
            val target = projected.remaining
            if (target !in 2..170) return@combine null
            val hasFeature = ent.contains(com.fabmue.checkoutmate.data.Feature.CHECKOUT)
            if (container.checkout.isBogey(target)) {
                CheckoutInfo(null, locked = !hasFeature, bogey = true, score = target)
            } else {
                val label = prefDoubleLabel(s.prefDouble)
                val suggestion = container.checkout.suggestion(target, label)
                CheckoutInfo(suggestion?.path, locked = !hasFeature, bogey = false, score = target)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun addDart(dart: Dart) {
        val m = match.value
        if (m.finished) return
        val newPending = (pending.value + dart).take(3)
        val preview = X01Engine.preview(m, newPending)
        if (preview.closed) commitDarts(newPending) else pending.value = newPending
    }

    fun removeLastDart() {
        pending.value = pending.value.dropLast(1)
    }

    fun commitPending() {
        if (pending.value.isNotEmpty()) commitDarts(pending.value)
    }

    fun commitTotal(total: Int) {
        val before = match.value
        if (before.finished) return
        pushUndo(before)
        val after = X01Engine.applyTotal(before, total)
        match.value = after
        pending.value = emptyList()
        afterCommit(after)
    }

    private fun commitDarts(darts: List<Dart>) {
        val before = match.value
        pushUndo(before)
        val after = X01Engine.applyDarts(before, darts)
        match.value = after
        pending.value = emptyList()
        afterCommit(after)
    }

    fun undo() {
        if (pending.value.isNotEmpty()) {
            pending.value = emptyList()
            return
        }
        val prev = undo.removeLastOrNull() ?: return
        match.value = prev
        canUndo.value = undo.isNotEmpty()
        persist()
    }

    fun rematch() {
        undo.clear()
        canUndo.value = false
        match.value = X01Engine.start(config)
        pending.value = emptyList()
        persist()
    }

    private fun pushUndo(state: X01Match) {
        undo.addLast(state)
        if (undo.size > 100) undo.removeFirst()
        canUndo.value = true
    }

    private fun afterCommit(after: X01Match) {
        after.lastEvent?.let { events.tryEmit(it) }
        if (after.finished) {
            viewModelScope.launch {
                container.history.saveFinishedMatch(after)
                container.prefs.setSavedMatch(null)
            }
        } else {
            persist()
        }
    }

    private fun persist() {
        val snapshot = match.value
        if (snapshot.finished) return
        viewModelScope.launch {
            container.prefs.setSavedMatch(MatchSerializer.toJson(snapshot))
        }
    }

    private fun prefDoubleLabel(prefDouble: Int): String? = when (prefDouble) {
        0 -> null
        25 -> "Bull"
        else -> "D$prefDouble"
    }
}

class TrainingViewModel(
    private val container: AppContainer,
    private val type: TrainingType,
) : ViewModel() {

    val game = MutableStateFlow(newTrainingGame(type))
    private var saved = false

    fun onDart(dart: Dart) {
        if (game.value.view().finished) return
        game.value = game.value.onDart(dart)
        maybeSave()
    }

    fun onMiss() {
        if (game.value.view().finished) return
        game.value = game.value.onMiss()
        maybeSave()
    }

    fun restart() {
        saved = false
        game.value = game.value.restart()
    }

    private fun maybeSave() {
        val view = game.value.view()
        if (view.finished && !saved) {
            saved = true
            viewModelScope.launch {
                container.history.saveTraining(type.name, view.score, view.steps)
            }
        }
    }
}
