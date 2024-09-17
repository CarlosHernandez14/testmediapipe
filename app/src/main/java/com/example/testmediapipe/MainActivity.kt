package com.example.testmediapipe

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    private lateinit var handLandmarker: HandLandmarker
    private lateinit var previewView: PreviewView

    @OptIn(ExperimentalMaterial3Api::class)
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
                        val scaffoldState = rememberBottomSheetScaffoldState()
                        val controller = remember {
                            LifecycleCameraController(applicationContext).apply {
                                setEnabledUseCases(
                                    CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE
                                )
                            }
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
                            }
                        }
                        //HandDetection()
                    }
                }
            }
        }
    }


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

        previewView = PreviewView(context)

        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )

        Button(onClick = { /*TODO*/ }) {
            Text(text = "Prueba")
        }

        // Iniciar la cámara
        startCamera()
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
        // Aquí puedes manejar el resultado de la detección
        // Ejemplo: imprimir las posiciones de las manos detectadas
        result.landmarks()?.forEachIndexed { index, landmarks ->
            println("Mano ${index + 1}: ${landmarks}")
        }
    }


    // Método para manejar errores
    private fun returnLivestreamError(error: RuntimeException) {
        // Aquí puedes manejar los errores que ocurrieron durante la detección
        println("Error en HandLandmarker: ${error.message}")
    }

    // Iniciar CameraX
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Configurar la vista previa de la cámara
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(PreviewView(this).surfaceProvider)
            }

            // Configurar el análisis de imágenes
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(windowManager.defaultDisplay.rotation)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this), ImageAnalysis.Analyzer { imageProxy ->
                        processImageProxy(imageProxy)
                    })
                }

            // Seleccionar la cámara frontal
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unir la cámara al ciclo de vida de la actividad
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                println("Error al iniciar la cámara: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // Procesar el frame de la cámara
    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)
        val mpImage = BitmapImageBuilder(bitmap).build()

        // Procesar la imagen con MediaPipe
        handLandmarker.detectAsync(mpImage, System.currentTimeMillis())

        imageProxy.close() // Asegurarse de cerrar el frame después de procesarlo
    }

    // Convertir ImageProxy a Bitmap
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
