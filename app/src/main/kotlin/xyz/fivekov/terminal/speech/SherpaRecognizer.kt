package xyz.fivekov.terminal.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Always-listening speech recognizer using Sherpa-ONNX streaming model.
 *
 * The recognizer runs continuously, feeding audio to the model at all times.
 * This keeps the model warmed up so there's no initial latency.
 *
 * When the user presses the mic button, we start capturing recognized text.
 * When released, we wait for an endpoint (silence), then deliver the result.
 * Text recognized while the button is NOT held is silently discarded.
 */
class SherpaRecognizer(private val context: Context) {

    private val assetManager: AssetManager = context.assets

    companion object {
        private const val TAG = "SherpaSTT"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "sherpa-model"
        private const val MAX_TRAIL_MS = 3000L
    }

    private var recognizer: OnlineRecognizer? = null
    private var listeningJob: Job? = null

    // State machine: IDLE -> CAPTURING -> TRAILING -> IDLE
    @Volatile private var capturing = false
    @Volatile private var trailing = false
    private var trailingStartMs = 0L

    // Accumulated text during this capture session
    private val capturedText = StringBuilder()
    private var currentSegment = ""

    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun warmup() {
        Log.d(TAG, "Warming up recognizer...")
        ensureRecognizer()
        Log.d(TAG, "Recognizer ready")
    }

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
                rule2 = EndpointRule(true, 0.8f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f),
            ),
            enableEndpoint = true,
        )

        return OnlineRecognizer(assetManager = assetManager, config = config).also {
            recognizer = it
        }
    }

    /**
     * Start the always-listening loop. Call once at activity start.
     * Audio is always fed to the model, but results are only delivered
     * when capturing is active (between startListening/stopListening).
     */
    fun startContinuousListening(scope: CoroutineScope) {
        if (listeningJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "No RECORD_AUDIO permission")
            return
        }

        listeningJob = scope.launch(Dispatchers.Default) {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            ).coerceAtLeast(SAMPLE_RATE)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, bufferSize * 4,
            )
            record.startRecording()
            Log.d(TAG, "Continuous listening started")

            val rec = ensureRecognizer()
            var stream = rec.createStream()
            val buffer = FloatArray(SAMPLE_RATE / 10) // 100ms

            try {
                while (isActive) {
                    val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) continue

                    val samples = if (read == buffer.size) buffer else buffer.copyOf(read)
                    stream.acceptWaveform(samples, SAMPLE_RATE)

                    while (rec.isReady(stream)) {
                        rec.decode(stream)
                    }

                    val text = rec.getResult(stream).text.trim()

                    if (capturing || trailing) {
                        // We're actively capturing or trailing after mic release
                        if (text.isNotEmpty() && text != currentSegment) {
                            currentSegment = text
                            if (capturing) {
                                val full = if (capturedText.isEmpty()) text
                                           else "$capturedText $text"
                                withContext(Dispatchers.Main) {
                                    onPartialResult?.invoke(full)
                                }
                            }
                        }

                        if (rec.isEndpoint(stream)) {
                            if (currentSegment.isNotEmpty()) {
                                if (capturedText.isNotEmpty()) capturedText.append(" ")
                                capturedText.append(currentSegment)
                                currentSegment = ""
                            }
                            rec.reset(stream)
                            stream.release()
                            stream = rec.createStream()

                            if (trailing) {
                                // Endpoint detected after mic release - deliver result
                                deliverFinalResult()
                            }
                        }

                        // Safety timeout for trailing
                        if (trailing && System.currentTimeMillis() - trailingStartMs > MAX_TRAIL_MS) {
                            Log.d(TAG, "Trail timeout, delivering result")
                            deliverFinalResult()
                        }
                    } else {
                        // Not capturing - discard recognized text and reset on endpoints
                        if (rec.isEndpoint(stream)) {
                            rec.reset(stream)
                            stream.release()
                            stream = rec.createStream()
                        }
                    }
                }
            } finally {
                try { record.stop() } catch (_: Exception) {}
                try { record.release() } catch (_: Exception) {}
                try { stream.release() } catch (_: Exception) {}
                Log.d(TAG, "Continuous listening stopped")
            }
        }
    }

    private fun deliverFinalResult() {
        if (currentSegment.isNotEmpty()) {
            if (capturedText.isNotEmpty()) capturedText.append(" ")
            capturedText.append(currentSegment)
            currentSegment = ""
        }

        val finalText = capturedText.toString().trim()
        Log.d(TAG, "Final text: '$finalText'")
        capturedText.clear()
        capturing = false
        trailing = false

        if (finalText.isNotEmpty()) {
            Handler(Looper.getMainLooper()).post {
                onFinalResult?.invoke(finalText)
            }
        }
    }

    /** User pressed mic button - start capturing recognized text */
    fun startListening(scope: CoroutineScope) {
        if (capturing) return
        Log.d(TAG, "Capture started")
        capturedText.clear()
        currentSegment = ""
        capturing = true
        trailing = false

        // If continuous listening isn't running, start it
        if (listeningJob?.isActive != true) {
            startContinuousListening(scope)
        }
    }

    /** User released mic button - wait for endpoint then deliver */
    fun stopListening() {
        if (!capturing) return
        Log.d(TAG, "Mic released, trailing...")
        capturing = false
        trailing = true
        trailingStartMs = System.currentTimeMillis()
    }

    fun release() {
        capturing = false
        trailing = false
        listeningJob?.cancel()
        listeningJob = null
        recognizer = null
    }
}
