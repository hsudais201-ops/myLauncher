package com.flame.launcher.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Low-overhead OpenGL ES host surface for Flame's game rendering pipeline.
 *
 * The initial scene is an in-app renderer diagnostic. Minecraft's LWJGL calls
 * are not rendered until a compatible Android native bridge is supplied.
 */
@Composable
fun FlameRenderSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surface = remember(context) { FlameGameSurfaceView(context) }

    DisposableEffect(surface, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surface.onResume()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> surface.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            surface.onPause()
        }
    }

    AndroidView(
        factory = { surface },
        modifier = modifier,
        update = { it.requestRender() }
    )
}

private class FlameGameSurfaceView(context: Context) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(FlameGameRenderer())
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

private class FlameGameRenderer : GLSurfaceView.Renderer {
    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD)
            position(0)
        }

    private var program = 0
    private var positionHandle = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glClearColor(0.035f, 0.045f, 0.065f, 1f)
        program = try {
            createProgram()
        } catch (_: RuntimeException) {
            0
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0) return

        GLES20.glUseProgram(program)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            vertices
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun createProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val linkedProgram = GLES20.glCreateProgram()
        check(linkedProgram != 0) { "Unable to create OpenGL ES program." }
        GLES20.glAttachShader(linkedProgram, vertexShader)
        GLES20.glAttachShader(linkedProgram, fragmentShader)
        GLES20.glLinkProgram(linkedProgram)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linkedProgram, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        check(status[0] == GLES20.GL_TRUE) {
            GLES20.glGetProgramInfoLog(linkedProgram).ifBlank { "OpenGL ES shader link failed." }
        }
        positionHandle = GLES20.glGetAttribLocation(linkedProgram, "aPosition")
        check(positionHandle >= 0) { "OpenGL ES position attribute was not found." }
        return linkedProgram
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Unable to create OpenGL ES shader." }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException(message.ifBlank { "OpenGL ES shader compilation failed." })
        }
        return shader
    }

    companion object {
        private const val FLOAT_BYTES = 4
        private val QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            varying vec2 vPosition;
            void main() {
                vPosition = aPosition;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vPosition;
            void main() {
                float horizon = smoothstep(-0.85, 0.85, vPosition.y);
                float glow = max(0.0, 1.0 - length(vPosition - vec2(0.35, -0.18)));
                vec3 lower = vec3(0.025, 0.035, 0.055);
                vec3 upper = vec3(0.12, 0.055, 0.025);
                vec3 color = mix(lower, upper, horizon);
                color += vec3(0.34, 0.12, 0.035) * glow * 0.18;
                gl_FragColor = vec4(color, 1.0);
            }
        """
    }
}
