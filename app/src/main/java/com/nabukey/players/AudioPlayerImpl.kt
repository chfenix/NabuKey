package com.nabukey.players

import android.media.AudioManager
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementation of AudioPlayer backed by an ExoPlayer.
 * Refactored to reuse the ExoPlayer instance and manage focus lifecycle separately.
 */
@UnstableApi
class AudioPlayerImpl(
    private val audioManager: AudioManager,
    val focusGain: Int,
    private val playerBuilder: () -> Player
) : AudioPlayer {
    private var _player: Player? = null
    private var focusRegistration: AudioFocusRegistration? = null
    private var activeListener: Player.Listener? = null

    private val _state = MutableStateFlow(AudioPlayerState.IDLE)
    override val state = _state.asStateFlow()

    private val isPlaying: Boolean get() = _player?.isPlaying ?: false

    private val isPaused: Boolean
        get() = _player?.let {
            !it.isPlaying && it.playbackState != Player.STATE_IDLE && it.playbackState != Player.STATE_ENDED
        } ?: false

    private var _volume: Float = 1.0f
    override var volume
        get() = _volume
        set(value) {
            _volume = value
            _player?.volume = value
        }

    override fun init() {
        // Ensure player exists
        if (_player == null) {
            Log.d(TAG, "Creating new ExoPlayer instance")
            _player = playerBuilder().apply {
                volume = _volume
                repeatMode = Player.REPEAT_MODE_OFF
            }
        }
        
        // Ensure focus is requested
        if (focusRegistration == null) {
            Log.d(TAG, "Requesting audio focus")
            focusRegistration = AudioFocusRegistration.request(
                audioManager = audioManager,
                audioAttributes = _player!!.audioAttributes,
                focusGain = focusGain
            )
        }
    }

    override fun play(mediaUris: Iterable<String>, onCompletion: () -> Unit) {
        init() // Ensure player and focus
        val player = _player!!

        // Remove previous listener to avoid duplicates or leaks
        activeListener?.let { 
            player.removeListener(it) 
        }

        val listener = getPlayerListener(onCompletion)
        activeListener = listener
        player.addListener(listener)

        runCatching {
            player.stop() // Reset state
            player.clearMediaItems()
            var hasItems = false
            for (mediaUri in mediaUris) {
                if (mediaUri.isNotEmpty()) {
                    player.addMediaItem(MediaItem.fromUri(mediaUri))
                    hasItems = true
                } else Log.w(TAG, "Ignoring empty media uri")
            }
            
            if (hasItems) {
                player.playWhenReady = true
                player.prepare()
            } else {
                 Log.w(TAG, "No valid media URIs to play")
                 onCompletion() // Nothing to play
                 abandonFocus()
            }
        }.onFailure {
            Log.e(TAG, "Error playing media $mediaUris", it)
            onCompletion()
            // We don't release the player on error, just stop and abandon focus
            player.stop()
            abandonFocus()
        }
    }

    override fun pause() {
        if (isPlaying)
            _player?.pause()
    }

    override fun unpause() {
        if (isPaused)
            _player?.play()
    }

    override fun stop() {
        _player?.stop()
        abandonFocus()
    }
    
    private fun abandonFocus() {
        focusRegistration?.close()
        focusRegistration = null
    }

    private fun getPlayerListener(onCompletion: () -> Unit) = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Playback state changed to $playbackState")
            if (playbackState == Player.STATE_ENDED) {
                onCompletion()
                abandonFocus() // Done speaking, let others play
            } else if (playbackState == Player.STATE_IDLE) {
                 // Idle usually means stopped or error or initial
                 // We don't necessarily call onCompletion here unless we were expecting to play
                 // But since we use this listener specifically for a play() call, if it goes IDLE it implies stop.
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.message}", error)
            onCompletion()
            abandonFocus()
            _player?.stop() // Reset to idle
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying)
                _state.value = AudioPlayerState.PLAYING
            else if (isPaused)
                _state.value = AudioPlayerState.PAUSED
            else
                _state.value = AudioPlayerState.IDLE
        }
    }

    override fun close() {
        Log.d(TAG, "Releasing ExoPlayer")
        _player?.release()
        _player = null
        abandonFocus()
        _state.value = AudioPlayerState.IDLE
    }

    companion object {
        private const val TAG = "AudioPlayer"
    }
}