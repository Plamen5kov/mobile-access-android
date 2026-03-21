package xyz.fivekov.terminal.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SherpaRecognizer(private val context: Context) {

    private val assetManager: AssetManager = context.assets

    companion object {
        private const val TAG = "SherpaSTT"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "sherpa-model"
    }

    private var recognizer: OnlineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private fun ensureRecognizer(): OnlineRecognizer {
        recognizer?.let { return it }

        val config = OnlineRecognizerConfig(
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$MODEL_DIR/encoder.onnx",
                    decoder = "$MODEL_DIR/decoder.onnx",
                    joiner = "$MODEL_DIR/joiner.onnx",
                ),
                tokens = "$MODEL_DIR/tokens.txt",
                numThreads = 2,
                modelType = "zipformer",
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0.0f),
                rule2 = EndpointRule(true, 1.2f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
            enableEndpoint = true,
        )

        return OnlineRecognizer(assetManager = assetManager, config = config).also {
            recognizer = it
        }
    }

    fun startListening(scope: CoroutineScope) {
        if (recordingJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onError?.invoke("Microphone permission not granted")
            return
        }

        recordingJob = scope.launch(Dispatchers.Default) {
            var record: AudioRecord? = null
            var stream: com.k2fsa.sherpa.onnx.OnlineStream? = null
            var lastText = ""
            var chunkCount = 0
            try {
                val rec = ensureRecognizer()
                stream = rec.createStream()

                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT,
                ).coerceAtLeast(SAMPLE_RATE)

                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT,
                    bufferSize * 4,
                )
                audioRecord = record
                record.startRecording()

                val buffer = FloatArray(SAMPLE_RATE / 10) // 100ms chunks

                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) break
                    chunkCount++

                    val samples = if (read == buffer.size) buffer else buffer.copyOf(read)
                    stream.acceptWaveform(samples, SAMPLE_RATE)

                    while (rec.isReady(stream)) {
                        rec.decode(stream)
                    }

                    val text = rec.getResult(stream).text.trim()
                    if (text.isNotEmpty() && text != lastText) {
                        lastText = text
                        withContext(Dispatchers.Main) {
                            onPartialResult?.invoke(text)
                        }
                    }

                    if (rec.isEndpoint(stream)) {
                        if (lastText.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                onFinalResult?.invoke(lastText)
                            }
                            lastText = ""
                        }
                        rec.reset(stream)
                    }
                }

                // Stopped — send whatever we have as final
                stream.inputFinished()
                while (rec.isReady(stream)) {
                    rec.decode(stream)
                }
                val finalText = rec.getResult(stream).text.trim()
                if (finalText.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onFinalResult?.invoke(finalText)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Recording cancelled, processed $chunkCount chunks, lastText='$lastText'")
                // Flush any accumulated text on cancellation (user released mic)
                if (lastText.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        onFinalResult?.invoke(lastText)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recognition error", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "Unknown error")
                }
            } finally {
                try { record?.stop() } catch (_: Exception) {}
                try { record?.release() } catch (_: Exception) {}
                try { stream?.release() } catch (_: Exception) {}
                audioRecord = null
            }
        }
    }

    fun stopListening() {
        // Stop AudioRecord first to unblock the read() call
        try { audioRecord?.stop() } catch (_: Exception) {}
        // Then cancel the coroutine
        recordingJob?.cancel()
        recordingJob = null
    }

    fun release() {
        stopListening()
        recognizer = null
    }
}
