package com.voidcraft.launcher

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import android.graphics.Color as AndroidColor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
fun RenderBackendScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var renderCount by remember { mutableIntStateOf(0) }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(VoidcraftGlesRenderer())
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
            setBackgroundColor(AndroidColor.rgb(10, 12, 18))
        }
    }

    DisposableEffect(glView) {
        onDispose {
            glView.onPause()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text("← BACK") }
            Column(Modifier.padding(top = 7.dp)) {
                Text("Render Backend", fontSize = 25.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("OpenGL ES 2.0 • Android GPU test", fontSize = 12.sp, color = VoidMuted)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = VoidSurface)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LIVE GPU SURFACE", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                AndroidView(
                    factory = {
                        glView.also { it.onResume() }
                    },
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                )
                Text(
                    "This test draws a shaded triangle directly through the device's OpenGL ES driver and changes its angle/color when you request another frame. It verifies a basic GPU surface only; it is not Minecraft rendering.",
                    color = VoidMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                Text("Render requests: $renderCount", color = VoidMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            renderCount++
                            glView.requestRender()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("RENDER FRAME") }
                    OutlinedButton(
                        onClick = {
                            renderCount = 0
                            glView.requestRender()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("RESET COUNT") }
                }
            }
        }
        Text(
            "Next engine steps: integrate a compatible Minecraft renderer/native backend, EGL lifecycle handling, game framebuffer, input mapping, and device-specific validation.",
            color = VoidMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

private class VoidcraftGlesRenderer : GLSurfaceView.Renderer {
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(
        TRIANGLE_COORDINATES.size * 4
    ).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(TRIANGLE_COORDINATES)
        position(0)
    }

    private var program = 0
    private var positionHandle = 0
    private var angleHandle = 0
    private var colorHandle = 0
    private var frame = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.035f, 0.045f, 0.075f, 1f)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        val vertexShader = compileShader(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec2 aPosition;
            uniform float uAngle;
            void main() {
                float c = cos(uAngle);
                float s = sin(uAngle);
                vec2 rotated = vec2(
                    aPosition.x * c - aPosition.y * s,
                    aPosition.x * s + aPosition.y * c
                );
                gl_Position = vec4(rotated, 0.0, 1.0);
            }
            """.trimIndent()
        )
        val fragmentShader = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
            """.trimIndent()
        )
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            val status = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "OpenGL ES shader program failed to link." }
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        angleHandle = GLES20.glGetUniformLocation(program, "uAngle")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glUniform1f(angleHandle, frame * 0.035f)
        val hue = (frame % 120) / 120f
        GLES20.glUniform4f(colorHandle, 0.35f + hue * 0.35f, 0.45f, 0.95f - hue * 0.25f, 1f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
        GLES20.glDisableVertexAttribArray(positionHandle)
        frame++
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("OpenGL ES shader compilation failed: $message")
        }
        return shader
    }

    companion object {
        private val TRIANGLE_COORDINATES = floatArrayOf(
            0.0f, 0.72f,
            -0.62f, -0.42f,
            0.62f, -0.42f
        )
    }
}
