package com.cycling.mynote.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Marker for the single immutable object a screen renders from. */
interface UiState

/** Marker for everything the user can do on a screen. */
interface UiEvent

/** Marker for one-shot things a screen does (navigate, show a message) that are not state. */
interface UiEffect

/**
 * The MVI loop: events in, state out, effects on the side.
 *
 * State is a single [StateFlow] of one immutable object rather than several independent flows, so
 * a recomposition always sees a consistent snapshot — with separate flows, a frame can render the
 * new note list next to the old loading flag.
 *
 * Effects are a [Channel] rather than shared state because they must fire exactly once: a
 * navigation or a snackbar delivered through state would re-fire on every configuration change.
 */
abstract class MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<F>(capacity = Channel.BUFFERED)
    val effects: Flow<F> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    /** The only way state changes; [reduce] receives the current state and returns the next one. */
    protected fun setState(reduce: S.() -> S) = _state.update(reduce)

    /**
     * Sends a one-shot effect.
     *
     * Uses [Channel.send] rather than `trySend` so a burst of effects is queued in order instead of
     * dropped — the channel is buffered, and the view model is alive for as long as anything could
     * be collecting it.
     */
    protected fun sendEffect(effect: F) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /** Handles one user or system event. */
    abstract fun onEvent(event: E)
}

/**
 * Collects a view model's effects for the lifetime of the composable.
 *
 * [onEffect] is read through [rememberUpdatedState] so a caller that passes a new lambda on every
 * recomposition does not restart collection and lose effects that were already buffered.
 */
@Composable
fun <F : UiEffect> EffectCollector(effects: Flow<F>, onEffect: (F) -> Unit) {
    val latest by rememberUpdatedState(onEffect)
    LaunchedEffect(effects) {
        effects.collect { latest(it) }
    }
}
