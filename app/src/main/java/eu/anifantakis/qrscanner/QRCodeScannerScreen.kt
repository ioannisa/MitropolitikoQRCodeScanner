package eu.anifantakis.qrscanner

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrCodeScannerScreen(
    viewModel: QRCodeScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Collect states από το ViewModel
    val scannedValue by viewModel.scannedValue.collectAsStateWithLifecycle()
    val isCameraEnabled by viewModel.isCameraEnabled.collectAsStateWithLifecycle()

    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    )

    var cameraProvider by remember { mutableStateOf<androidx.camera.lifecycle.ProcessCameraProvider?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            this.scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Αρχικοποίηση του camera provider
    LaunchedEffect(Unit) {
        viewModel.initializeCameraProvider(context) { provider ->
            cameraProvider = provider
        }
    }

    // Ρύθμιση της κάμερας όταν αλλάζουν οι καταστάσεις
    LaunchedEffect(isCameraEnabled, cameraPermissionState.status.isGranted, cameraProvider) {
        viewModel.setupCamera(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = previewView.surfaceProvider,
            hasCameraPermission = cameraPermissionState.status.isGranted
        )
    }

    // Καθαρισμός πόρων όταν το Composable αφαιρείται
    DisposableEffect(Unit) {
        onDispose {
            viewModel.unbindCamera()
        }
    }

    HandlePermission(
        modifier = Modifier.fillMaxSize(),
        permissionState = cameraPermissionState,
        rationaleText = "Camera permission is needed to scan QR codes. Please grant the permission.",
        deniedText = "Camera permission is required for this feature. Please grant it.",
        contentWhenGranted = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(300.dp)
                            .aspectRatio(1f)
                    ) {
                        if (isCameraEnabled) {
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "Camera Off",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Scan QR Code")
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isCameraEnabled,
                            onCheckedChange = { checked ->
                                viewModel.toggleCamera(checked)
                            }
                        )
                    }
                    Text(
                        text = scannedValue ?: "Scan a QR code...",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
    )
}