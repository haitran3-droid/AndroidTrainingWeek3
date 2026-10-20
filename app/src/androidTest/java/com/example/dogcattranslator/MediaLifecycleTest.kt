package com.example.dogcattranslator

import android.Manifest
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.dogcattranslator.media.*
import com.example.dogcattranslator.ui.sound.SoundViewModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaLifecycleTest {
    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    @Test
    fun mediaPlayerPlaysPausesSeeksCompletesReplaysAndHandlesMissingFile() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val file = createSilentWav()
            lateinit var manager: SoundPlayerManager
            var initialized = false
            try {
                instrumentation.runOnMainSync {
                    manager = SoundPlayerManager(context, scope)
                    initialized = true
                    manager.prepare(Uri.fromFile(file))
                }
                awaitCondition { manager.state.value.status == PlaybackStatus.READY }
                instrumentation.runOnMainSync { manager.play() }
                awaitCondition {
                    manager.state.value.status == PlaybackStatus.PLAYING &&
                        manager.state.value.position > 100
                }
                instrumentation.runOnMainSync { manager.pause() }
                val paused = manager.state.value.position
                SystemClock.sleep(350)
                assertEquals(PlaybackStatus.PAUSED, manager.state.value.status)
                assertEquals(paused, manager.state.value.position)
                instrumentation.runOnMainSync { manager.seekTo(1000) }
                assertEquals(1000, manager.state.value.position)
                instrumentation.runOnMainSync { manager.play() }
                awaitCondition { manager.state.value.position >= 1000 }
                instrumentation.runOnMainSync { manager.seekTo(3900) }
                awaitCondition { manager.state.value.status == PlaybackStatus.COMPLETED }
                instrumentation.runOnMainSync { manager.play() }
                awaitCondition {
                    manager.state.value.status == PlaybackStatus.PLAYING &&
                        manager.state.value.position < 1000
                }
                instrumentation.runOnMainSync { manager.prepare(null) }
                assertEquals(R.string.missing_audio, manager.state.value.message)
                instrumentation.runOnMainSync {
                    manager.prepare(Uri.fromFile(File(context.cacheDir, "absent-test-audio.wav")))
                }
                awaitCondition { manager.state.value.status == PlaybackStatus.ERROR }
            } finally {
                instrumentation.runOnMainSync {
                    if (initialized) {
                        manager.release()
                        manager.release()
                    }
                }
                scope.cancel()
                file.delete()
            }
        }
    }

    @Test
    fun completedSoundRestoresFromBeginning() {
        instrumentation.runOnMainSync {
            val saved = SavedStateHandle()
            val original = SoundViewModel(saved)
            original.update(
                PlaybackState(PlaybackStatus.COMPLETED, position = 4000, duration = 4000),
            )
            val restored = SoundViewModel(saved)
            assertEquals(0, restored.position)
        }
    }

    @Test
    fun recorderAutoStopsAt15SecondsAndDeletesCache() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            var elapsed = 0L
            val started = SystemClock.elapsedRealtime()
            val result =
                withTimeout(20000) {
                    AudioRecorderManager(context).record { time, _ -> elapsed = time }
                }
            assertTrue(result.sampleCount > 80)
            assertEquals(15000L, elapsed)
            assertTrue(SystemClock.elapsedRealtime() - started >= 15000)
            assertNoRecordings()
        }
    }

    @Test
    fun recorderEarlyStopAndCancellationReleaseResources() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use {
            val manager = AudioRecorderManager(context)
            val recording = async {
                manager.record { elapsed, _ -> if (elapsed >= 700) manager.requestStop() }
            }
            val result = withTimeout(5000) { recording.await() }
            assertTrue(result.sampleCount in 1..20)
            assertNoRecordings()
            val started = CompletableDeferred<Unit>()
            val cancelJob = launch {
                AudioRecorderManager(context).record { _, _ -> started.complete(Unit) }
            }
            withTimeout(5000) { started.await() }
            cancelJob.cancelAndJoin()
            assertNoRecordings()
        }
    }

    private fun assertNoRecordings() {
        assertTrue(context.cacheDir.listFiles()?.none { it.name.startsWith("voice_") } ?: true)
    }

    private fun createSilentWav(): File {
        // A valid PCM fixture exercises the actual Android decoder, without masquerading as pet
        // audio.
        val dataSize = 8000 * 4 * 2
        val bytes = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bytes
            .put("RIFF".toByteArray())
            .putInt(36 + dataSize)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
        bytes.putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(dataSize)
        return File(context.cacheDir, "test-silence.wav").also { it.writeBytes(bytes.array()) }
    }
}
