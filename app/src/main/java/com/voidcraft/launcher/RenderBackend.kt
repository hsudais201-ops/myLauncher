package com.voidcraft.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private data class RendererChoice(
    val title: String,
    val rendererId: String?,
    val subtitle: String,
    val description: String
)

private val rendererChoices = listOf(
    RendererChoice(
        "Holy GL4ES (original)",
        "opengles2_holy",
        "OpenGL ES 2 • fast, broad vanilla-version support",
        "Uses the original Pojav GL4ES 1.1.4 native library. Shader and advanced-mod compatibility is more limited than LTW or Zink; device testing is still required."
    ),
    RendererChoice(
        "Krypton Wrapper (GL4ES family)",
        "opengles2",
        "OpenGL ES • broad compatibility",
        "The bundled GL4ES-family backend in this engine revision. It is not the original Holy GL4ES renderer."
    ),
    RendererChoice(
        "Zink (Vulkan)",
        "vulkan_zink",
        "Vulkan • device and native-library dependent",
        "Uses the engine's Zink/Vulkan path. The engine checks Vulkan support and whether the required native library is present, and may fall back if this option is unavailable."
    ),
    RendererChoice(
        "LTW",
        "opengles3_ltw",
        "OpenGL ES 3 • Minecraft 1.17+",
        "Requires OpenGL ES 3 support and the optional LTW native library. If the engine build or device does not provide them, it may select a compatible fallback."
    ),
    RendererChoice(
        "MobileGlues",
        "opengles_mobileglues",
        "OpenGL ES • compatibility fallback",
        "The current engine's default/fallback backend. Keep this selected if another backend fails to start."
    )
)

@Composable
fun RenderBackendScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val rendererPrefs = remember {
        context.getSharedPreferences("voidcraft_preferences", android.content.Context.MODE_PRIVATE)
    }
    var selectedRendererId by remember {
        mutableStateOf(rendererPrefs.getString("selected_renderer_id", null))
    }
    var rendererStatus by remember {
        mutableStateOf("Renderer requests apply to the selected Minecraft profile on the next launch.")
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var requestCount by remember { mutableIntStateOf(0) }
    var renderedFrames by remember { mutableIntStateOf(0) }
    var gpuInfo by remember { mutableStateOf("Waiting for OpenGL ES context…") }

    val renderer = remember {
        VoidcraftGlesRenderer { description, frameCount ->
            mainHandler.post {
                gpuInfo = description
                renderedFrames = frameCount
            }
        }
    }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setPreserveEGLContextOnPause(true)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            setBackgroundColor(AndroidColor.rgb(10, 12, 18))
        }
    }

    DisposableEffect(glView) {
        onDispose { glView.onPause() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("← BACK") }
            Column(Modifier.padding(top = 7.dp)) {
                Text(
                    "Render Backend",
                    fontSize = 25.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text("OpenGL ES 2.0 • GPU diagnostics", fontSize = 12.sp, color = VoidMuted)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MINECRAFT RENDERER", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                Text(
                    "Current request: " + (rendererChoices.firstOrNull { it.rendererId == selectedRendererId }?.title
                        ?: "Use the engine's current profile setting"),
                    color = VoidMuted,
                    fontSize = 12.sp
                )
                Text(
                    "Choose a backend for the next launch. Availability depends on GPU support and native libraries included in the integrated APK; the engine may fall back automatically.",
                    color = VoidMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                rendererChoices.forEach { choice ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF0A0C12))
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(choice.title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(choice.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Text(choice.description, fontSize = 12.sp, color = VoidMuted, lineHeight = 17.sp)
                            if (choice.rendererId == null) {
                                OutlinedButton(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("NOT INCLUDED IN THIS BUILD") }
                            } else {
                                Button(
                                    onClick = {
                                        val id = choice.rendererId
                                        if (id != null) {
                                            rendererPrefs.edit()
                                                .putString("selected_renderer_id", id)
                                                .apply()
                                            selectedRendererId = id
                                            rendererStatus = choice.title +
                                                " saved for the next launch. Device/library compatibility will be checked by the engine."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (selectedRendererId == choice.rendererId) "SELECTED"
                                        else "USE FOR NEXT LAUNCH"
                                    )
                                }
                            }
                        }
                    }
                }
                Text(rendererStatus, color = VoidMuted, fontSize = 11.sp, lineHeight = 16.sp)
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent().setClassName(context, "net.kdt.pojavlaunch.LauncherActivity")
                            selectedRendererId?.let { intent.putExtra("voidcraft.renderer", it) }
                            context.startActivity(intent)
                            rendererStatus = "Opened the integrated engine launcher."
                        } catch (_: ActivityNotFoundException) {
                            rendererStatus = "The integrated renderer controls require the combined engine build; this standalone prototype cannot change Minecraft's actual renderer."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("OPEN INTEGRATED ENGINE") }
                Text(
                    "Vulkan here means the engine's Zink/Vulkan backend, not the separate VulkanMod Minecraft mod. Holy GL4ES and Krypton Wrapper are separate GL4ES-family options.",
                    color = VoidMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3D GPU TEST", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                AndroidView(
                    factory = { glView.also { it.onResume() } },
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                )
                Text(
                    "A 3D cube rotates a little each time you request a frame. The renderer uses a vertex/fragment shader and a depth buffer. This verifies basic OpenGL ES rendering only; it is not the Minecraft renderer.",
                    color = VoidMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Text("Render requests: " + requestCount, color = VoidMuted, fontSize = 11.sp)
                Text("Frames actually drawn: " + renderedFrames, color = VoidMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            requestCount++
                            glView.requestRender()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("RENDER FRAME") }
                    OutlinedButton(
                        onClick = {
                            requestCount = 0
                            glView.requestRender()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("RESET REQUESTS") }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("GPU / DRIVER", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                Text(gpuInfo, color = VoidMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }

        Text(
            "Next: a compatible Minecraft bootstrap, native graphics libraries and OpenGL compatibility layer, game framebuffer, input mapping, and device testing.",
            color = VoidMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

private class VoidcraftGlesRenderer(
    private val onDiagnostics: (String, Int) -> Unit
) : GLSurfaceView.Renderer {
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(CUBE_VERTICES.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(CUBE_VERTICES)
            position(0)
        }

    private var program = 0
    private var positionHandle = -1
    private var colorHandle = -1
    private var angleHandle = -1
    private var aspectHandle = -1
    private var frame = 0
    private var aspectRatio = 1f
    private var gpuDescription = "OpenGL context initializing"
    private var shaderError: String? = null
    private var lastGlError: String? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.045f, 0.075f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown vendor"
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown renderer"
        val version = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown OpenGL version"
        val maxTextureSize = IntArray(1)
        val maxRenderbufferSize = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0)
        GLES20.glGetIntegerv(GLES20.GL_MAX_RENDERBUFFER_SIZE, maxRenderbufferSize, 0)
        gpuDescription = "Vendor: " + vendor +
            "\nRenderer: " + renderer +
            "\nOpenGL: " + version +
            "\nMax texture size: " + maxTextureSize[0] + " px" +
            "\nMax renderbuffer: " + maxRenderbufferSize[0] + " px"

        try {
            val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            check(status[0] == GLES20.GL_TRUE) {
                "Shader program could not link: " + GLES20.glGetProgramInfoLog(program)
            }

            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            colorHandle = GLES20.glGetAttribLocation(program, "aColor")
            angleHandle = GLES20.glGetUniformLocation(program, "uAngle")
            aspectHandle = GLES20.glGetUniformLocation(program, "uAspect")
            shaderError = null
        } catch (error: Exception) {
            if (program != 0) GLES20.glDeleteProgram(program)
            program = 0
            shaderError = error.message ?: "Unknown shader initialization error"
        }
        publishDiagnostics()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        publishDiagnostics()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) {
            publishDiagnostics()
            return
        }

        GLES20.glUseProgram(program)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(colorHandle)
        val stride = 6 * 4
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, stride, vertices)
        vertices.position(3)
        GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, stride, vertices)
        GLES20.glUniform1f(angleHandle, frame * 0.055f)
        GLES20.glUniform1f(aspectHandle, aspectRatio.coerceAtLeast(0.1f))
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, CUBE_VERTEX_COUNT)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        val glError = GLES20.glGetError()
        lastGlError = if (glError == GLES20.GL_NO_ERROR) {
            null
        } else {
            "OpenGL error 0x" + glError.toString(16).uppercase()
        }
        frame++
        publishDiagnostics()
    }

    private fun publishDiagnostics() {
        val sizeNote = "\nSurface: " + surfaceWidth + " × " + surfaceHeight + " px"
        val shaderNote = shaderError?.let { "\nShader error: " + it } ?: ""
        val glNote = lastGlError?.let { "\nGPU draw warning: " + it } ?: "\nLast draw: no GL errors"
        onDiagnostics(gpuDescription + sizeNote + shaderNote + glNote, frame)
    }

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val message = GLES20.glGetShaderInfoLog(shader) ?: "No compiler details"
            GLES20.glDeleteShader(shader)
            error("Shader compilation failed: " + message)
        }
        return shader
    }

    companion object {
        private const val CUBE_VERTEX_COUNT = 36

        private val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aColor;
            uniform float uAngle;
            uniform float uAspect;
            varying vec3 vColor;
            void main() {
                float cy = cos(uAngle);
                float sy = sin(uAngle);
                float cx = cos(uAngle * 0.73);
                float sx = sin(uAngle * 0.73);
                float x = aPosition.x * cy + aPosition.z * sy;
                float z = -aPosition.x * sy + aPosition.z * cy;
                float y = aPosition.y * cx - z * sx;
                z = aPosition.y * sx + z * cx;
                float depth = max(1.4, z + 2.2);
                gl_Position = vec4(x * 1.65 / (depth * uAspect), y * 1.65 / depth, z * 0.45, 1.0);
                vColor = aColor * (0.72 + max(0.0, z) * 0.20);
            }
        """.trimIndent()

        private val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec3 vColor;
            void main() {
                gl_FragColor = vec4(vColor, 1.0);
            }
        """.trimIndent()

        private val CUBE_VERTICES: FloatArray = createCubeVertices()

        private fun createCubeVertices(): FloatArray {
            data class Face(val corners: FloatArray, val color: FloatArray)
            val faces = arrayOf(
                Face(floatArrayOf(-0.55f,-0.55f,0.55f, 0.55f,-0.55f,0.55f, 0.55f,0.55f,0.55f, -0.55f,0.55f,0.55f), floatArrayOf(0.22f,0.58f,1.0f)),
                Face(floatArrayOf(0.55f,-0.55f,-0.55f, -0.55f,-0.55f,-0.55f, -0.55f,0.55f,-0.55f, 0.55f,0.55f,-0.55f), floatArrayOf(0.55f,0.32f,0.98f)),
                Face(floatArrayOf(0.55f,-0.55f,0.55f, 0.55f,-0.55f,-0.55f, 0.55f,0.55f,-0.55f, 0.55f,0.55f,0.55f), floatArrayOf(0.15f,0.83f,0.82f)),
                Face(floatArrayOf(-0.55f,-0.55f,-0.55f, -0.55f,-0.55f,0.55f, -0.55f,0.55f,0.55f, -0.55f,0.55f,-0.55f), floatArrayOf(0.27f,0.43f,0.95f)),
                Face(floatArrayOf(-0.55f,0.55f,0.55f, 0.55f,0.55f,0.55f, 0.55f,0.55f,-0.55f, -0.55f,0.55f,-0.55f), floatArrayOf(0.96f,0.53f,0.26f)),
                Face(floatArrayOf(-0.55f,-0.55f,-0.55f, 0.55f,-0.55f,-0.55f, 0.55f,-0.55f,0.55f, -0.55f,-0.55f,0.55f), floatArrayOf(0.35f,0.76f,0.35f))
            )
            val triangleOrder = intArrayOf(0, 1, 2, 0, 2, 3)
            val result = FloatArray(CUBE_VERTEX_COUNT * 6)
            var output = 0
            for (face in faces) {
                for (cornerIndex in triangleOrder) {
                    val pointOffset = cornerIndex * 3
                    result[output++] = face.corners[pointOffset]
                    result[output++] = face.corners[pointOffset + 1]
                    result[output++] = face.corners[pointOffset + 2]
                    result[output++] = face.color[0]
                    result[output++] = face.color[1]
                    result[output++] = face.color[2]
                }
            }
            return result
        }
    }
}
