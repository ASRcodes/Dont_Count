package com.example.dont_count

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.dontcount.OverlayView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

class CameraActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var tvExercise: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnEndExercise: Button

    private var cameraExecutor: ExecutorService? = null
    private var detector: PoseDetector? = null

    // rep counting state
    private var repCount = 0
    private var isDown = false

    private lateinit var exerciseName: String

    // full-body tracking gating
    private var visibleFrames = 0
    private val minVisibleFrames = 5
    private var trackingStarted = false

    // toast control
    private var lastToastTime: Long = 0
    private val toastInterval = 4000L // 4 seconds
    private var toastShownOnce = false // first-time toast

    // prevent fast repeated rep counts
    private var lastRepTime: Long = 0
    private val minRepIntervalMs = 700L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        tvExercise = findViewById(R.id.tvExercise)
        tvCount = findViewById(R.id.tvCount)
        tvStatus = findViewById(R.id.tvStatus)
        btnEndExercise = findViewById(R.id.btnEndExercise)

        exerciseName = intent.getStringExtra("exercise") ?: "Squats"
        tvExercise.text = exerciseName
        tvCount.text = "$repCount"

        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        detector = PoseDetection.getClient(options)

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnEndExercise.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Exercise Finished")
                .setMessage("You completed $repCount reps of $exerciseName")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss(); finish() }
                .show()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun animateRepCount() {
        tvCount.animate()
            .scaleX(1.5f).scaleY(1.5f)
            .setDuration(180)
            .withEndAction {
                tvCount.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }.start()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor!!) { imageProxy -> processImageProxy(imageProxy) } }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e("CameraActivity", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
            detector?.process(inputImage)
                ?.addOnSuccessListener { pose -> handlePose(pose, imageProxy.width, imageProxy.height, rotation) }
                ?.addOnFailureListener { e -> Log.e("CameraActivity", "Pose detection failure", e) }
                ?.addOnCompleteListener { imageProxy.close() }
        } else imageProxy.close()
    }

    private fun toViewPoint(lmX: Float, lmY: Float, imgW: Int, imgH: Int, rotation: Int): PointF {
        val vw = previewView.width.toFloat()
        val vh = previewView.height.toFloat()
        val scaleX = vw / imgW.toFloat()
        val scaleY = vh / imgH.toFloat()
        return when ((rotation % 360 + 360) % 360) {
            0 -> PointF(vw - lmX * scaleX, lmY * scaleY)
            90 -> PointF(vw - ((imgH - lmY) * scaleX), lmX * scaleY)
            180 -> PointF(lmX * scaleX, vh - lmY * scaleY)
            270 -> PointF(vw - (lmY * scaleX), (imgW - lmX) * scaleY)
            else -> PointF(vw - lmX * scaleX, lmY * scaleY)
        }
    }

    private fun handlePose(pose: Pose, imgWidth: Int, imgHeight: Int, rotation: Int) {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) {
            overlay.update(emptyList(), emptyList())
            showFullBodyToastIfNeeded()
            trackingStarted = false
            visibleFrames = 0
            return
        }

        val lmMap = mutableMapOf<Int, PointF>()
        for (lm in landmarks) {
            lmMap[lm.landmarkType] = toViewPoint(lm.position.x, lm.position.y, imgWidth, imgHeight, rotation)
        }

        val required = listOf(
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
        )
        val fullBodyVisible = required.all { lmMap.containsKey(it) }

        if (fullBodyVisible) {
            visibleFrames++
            if (!trackingStarted && visibleFrames >= minVisibleFrames) {
                trackingStarted = true
                isDown = false
                runOnUiThread { tvStatus.text = "Tracking started" }
            }
            toastShownOnce = false // reset toast when body visible
        } else {
            visibleFrames = 0
            showFullBodyToastIfNeeded()
            trackingStarted = false
        }

        if (trackingStarted) {
            when (exerciseName) {
                "Squats" -> evaluateSquat(lmMap)
                "Push-ups" -> evaluatePushup(lmMap)
                else -> evaluateSquat(lmMap)
            }
        } else {
            runOnUiThread { tvStatus.text = "Align full body in frame" }
        }

        runOnUiThread { tvCount.text = "$repCount" }
    }

    private fun showFullBodyToastIfNeeded() {
        val now = System.currentTimeMillis()
        if (!toastShownOnce || now - lastToastTime > toastInterval) {
            runOnUiThread {
                Toast.makeText(this, "Full body not detected", Toast.LENGTH_SHORT)
                    .apply { setGravity(Gravity.CENTER, 0, 0); show() }
            }
            lastToastTime = now
            toastShownOnce = true
        }
    }

    private fun angleBetweenThreePoints(a: PointF?, b: PointF?, c: PointF?): Double? {
        if (a == null || b == null || c == null) return null
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val dot = abx * cbx + aby * cby
        val mag1 = sqrt(abx.pow(2) + aby.pow(2))
        val mag2 = sqrt(cbx.pow(2) + cby.pow(2))
        if (mag1 == 0f || mag2 == 0f) return null
        val cos = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cos).toDouble())
    }

    private fun evaluateSquat(lm: Map<Int, PointF>) {
        val left = angleBetweenThreePoints(lm[PoseLandmark.LEFT_HIP], lm[PoseLandmark.LEFT_KNEE], lm[PoseLandmark.LEFT_ANKLE])
        val right = angleBetweenThreePoints(lm[PoseLandmark.RIGHT_HIP], lm[PoseLandmark.RIGHT_KNEE], lm[PoseLandmark.RIGHT_ANKLE])
        if (left == null || right == null) { runOnUiThread { tvStatus.text = "Position not fully visible" }; return }
        val kneeAngle = (left + right) / 2.0
        val downThreshold = 110; val upThreshold = 150
        val now = System.currentTimeMillis()
        if (!isDown && kneeAngle < downThreshold) { isDown = true; runOnUiThread { tvStatus.text = "Down" } }
        else if (isDown && kneeAngle > upThreshold && now - lastRepTime > minRepIntervalMs) {
            isDown = false; repCount += 1; lastRepTime = now
            runOnUiThread { tvStatus.text = "Good rep!"; tvCount.text = "$repCount"; animateRepCount() }
        } else runOnUiThread { tvStatus.text = "" }
    }

    private fun evaluatePushup(lm: Map<Int, PointF>) {
        val left = angleBetweenThreePoints(lm[PoseLandmark.LEFT_SHOULDER], lm[PoseLandmark.LEFT_ELBOW], lm[PoseLandmark.LEFT_WRIST])
        val right = angleBetweenThreePoints(lm[PoseLandmark.RIGHT_SHOULDER], lm[PoseLandmark.RIGHT_ELBOW], lm[PoseLandmark.RIGHT_WRIST])
        if (left == null || right == null) { runOnUiThread { tvStatus.text = "Position not visible" }; return }
        val elbowAngle = (left + right) / 2.0
        val downT = 100; val upT = 160
        val now = System.currentTimeMillis()
        if (!isDown && elbowAngle < downT) { isDown = true; runOnUiThread { tvStatus.text = "Down" } }
        else if (isDown && elbowAngle > upT && now - lastRepTime > minRepIntervalMs) {
            isDown = false; repCount += 1; lastRepTime = now
            runOnUiThread { tvStatus.text = "Push-up counted"; tvCount.text = "$repCount"; animateRepCount() }
        } else runOnUiThread { tvStatus.text = "" }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
        detector?.close()
    }
}
