package com.example.testmediapipe

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testmediapipe.ui.theme.TestMediaPipeTheme
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var handLandmarker: HandLandmarker
    private lateinit var overlay: LandmarkOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(
                this, CAMERAX_PERMISSIONS, 0
            )
        }

        setContent {
            TestMediaPipeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        HandDetection()
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun HandDetection() {
        val context = LocalContext.current
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("hand_landmarker.task")
        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder =
            HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.7f)
                .setMinTrackingConfidence(0.7f)
                .setMinHandPresenceConfidence(0.7f)
                .setNumHands(2)
                .setResultListener(this::returnLivestreamResult)
                .setErrorListener(this::returnLivestreamError)
                .setRunningMode(RunningMode.LIVE_STREAM)

        val options = optionsBuilder.build()
        handLandmarker = HandLandmarker.createFromOptions(context, options)

        val executor = remember { Executors.newSingleThreadExecutor() }

        // Overlay for landmarks
        this.overlay = remember { LandmarkOverlay(context, null) }

        // CAMERA X DISPLAY
        val scaffoldState = rememberBottomSheetScaffoldState()
        val controller = remember {
            LifecycleCameraController(applicationContext).apply {
                setEnabledUseCases(
                    CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE
                )


            }
        }
        controller.setImageAnalysisAnalyzer(executor) {
            processImageProxy(it)
        }
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 0.dp,
            sheetContent = {

            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                CameraPreview(
                    controller = controller,
                    modifier = Modifier.fillMaxSize()
                )
                AndroidView(
                    factory = { overlay },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

    }

    private fun hasRequiredPermissions() : Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) ==  PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }



    // Método para manejar los resultados de la detección de manos
    private fun returnLivestreamResult(result: HandLandmarkerResult, image: MPImage) {
        System.out.println("HOLAAAAAAAAAAAAAAAAAAAAAAAAAAAAA FROM THE LIVE MARKER RESULT")

        // Actualizar el LandmarkOverlay con los nuevos resultados de landmarks
        println("IMAGE HEIGHT: ${image.height}")
        println("IMAGE WIDTH: ${image.width}")
        runOnUiThread {
            this.overlay.setResults(result, image.height, image.width, RunningMode.LIVE_STREAM)
        }

        // Aquí puedes manejar el resultado de la detección
        // Ejemplo: imprimir las posiciones de las manos detectadas
        result.landmarks()?.forEachIndexed { index, landmarks ->
            println("Mano ${index + 1}:")
            landmarks.forEachIndexed { indexLand, landmark ->
                println("Landmark $indexLand: $landmark")
            }

        }
    }


    // Método para manejar errores
    private fun returnLivestreamError(error: RuntimeException) {
        // Aquí puedes manejar los errores que ocurrieron durante la detección
        println("Error en HandLandmarker: ${error.message}")
    }

    // Procesar el frame de la cámara
    private fun processImageProxy(imageProxy: ImageProxy) {
        println("Procesando ImageProxy")
        if (imageProxy.format != ImageFormat.YUV_420_888) {
            imageProxy.close()
            return
        }

        val bitmap = yuvToRgb(imageProxy)
        val mpImage = BitmapImageBuilder(bitmap).build()

        val frameTime = SystemClock.uptimeMillis()
        handLandmarker.detectAsync(mpImage, frameTime)

        imageProxy.close()
    }

    private fun yuvToRgb(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

}
