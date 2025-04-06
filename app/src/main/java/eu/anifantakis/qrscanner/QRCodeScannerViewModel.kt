package eu.anifantakis.qrscanner

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRCodeScannerViewModel : ViewModel() {

    // State flows για τα UI states
    private val _scannedValue = MutableStateFlow<String?>(null)
    val scannedValue: StateFlow<String?> = _scannedValue.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(false)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    // Camera resources
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    // Μέθοδος για την προετοιμασία του camera provider
    fun initializeCameraProvider(context: Context, onComplete: (ProcessCameraProvider?) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                onComplete(cameraProvider)
            } catch (e: Exception) {
                Log.e("QRCodeScanner", "Error getting camera provider", e)
                onComplete(null)
            }
        }, context.mainExecutor)
    }

    // Μέθοδος για τη ρύθμιση της κάμερας
    fun setupCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        hasCameraPermission: Boolean
    ) {
        if (!hasCameraPermission || !_isCameraEnabled.value || cameraProvider == null) {
            unbindCamera()
            return
        }

        try {
            cameraProvider?.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcodes ->
                        barcodes.firstOrNull()?.rawValue?.let { value ->
                            if (_scannedValue.value != value) {
                                viewModelScope.launch {
                                    _scannedValue.emit(value)
                                }
                            }
                        }
                    })
                }

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("QRCodeScanner", "Camera Use case binding FAILED", exc)
            viewModelScope.launch {
                _isCameraEnabled.emit(false)
            }
        }
    }

    // Toggle της κάμερας (on/off)
    fun toggleCamera(isEnabled: Boolean) {
        viewModelScope.launch {
            _scannedValue.emit(null)
            _isCameraEnabled.emit(isEnabled)
        }
    }

    // Καθαρισμός των πόρων της κάμερας
    fun unbindCamera() {
        cameraProvider?.unbindAll()
    }

    // Καθαρισμός όλων των πόρων
    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
        unbindCamera()
    }
}