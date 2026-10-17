package com.eliteandroid.render

import com.badlogic.gdx.graphics.glutils.ShaderProgram

/** Shaders GLSL ES 2.0 (compatíveis com OpenGL ES 2/3 no Android e GL 2+ no desktop). */
internal object Shaders {

    private const val PRECISION = """
#ifdef GL_ES
precision mediump float;
#endif
"""

    /** Linhas neon: quads em espaço de tela; a_edge vai de -1 a +1 através da largura da linha. */
    val LINE_VERTEX = """
attribute vec4 a_position;
attribute vec4 a_color;
attribute float a_edge;
uniform mat4 u_projTrans;
varying vec4 v_color;
varying float v_edge;
void main() {
    v_color = a_color;
    v_edge = a_edge;
    gl_Position = u_projTrans * a_position;
}
"""

    /** Núcleo quase branco no centro, cor saturada nas bordas e decaimento suave (antialiasing). */
    val LINE_FRAGMENT = PRECISION + """
varying vec4 v_color;
varying float v_edge;
void main() {
    float d = abs(v_edge);
    float body = 1.0 - smoothstep(0.30, 1.0, d);
    float core = 1.0 - smoothstep(0.0, 0.35, d);
    vec3 rgb = mix(v_color.rgb, vec3(1.0), core * 0.55);
    float a = body * v_color.a;
    gl_FragColor = vec4(rgb * a, a);
}
"""

    val QUAD_VERTEX = """
attribute vec4 a_position;
attribute vec2 a_texCoord0;
varying vec2 v_uv;
void main() {
    v_uv = a_texCoord0;
    gl_Position = a_position;
}
"""

    val COPY_FRAGMENT = PRECISION + """
uniform sampler2D u_texture;
varying vec2 v_uv;
void main() {
    gl_FragColor = texture2D(u_texture, v_uv);
}
"""

    /** Gaussiano de 9 taps feito com 5 leituras usando filtragem linear. */
    val BLUR_FRAGMENT = PRECISION + """
uniform sampler2D u_texture;
uniform vec2 u_dir;
varying vec2 v_uv;
void main() {
    vec2 o1 = u_dir * 1.3846153846;
    vec2 o2 = u_dir * 3.2307692308;
    vec4 c = texture2D(u_texture, v_uv) * 0.2270270270;
    c += texture2D(u_texture, v_uv + o1) * 0.3162162162;
    c += texture2D(u_texture, v_uv - o1) * 0.3162162162;
    c += texture2D(u_texture, v_uv + o2) * 0.0702702703;
    c += texture2D(u_texture, v_uv - o2) * 0.0702702703;
    gl_FragColor = c;
}
"""

    /** Composição final: cena + bloom (2 níveis) + scanlines e vinheta sutis de CRT. */
    val COMPOSITE_FRAGMENT = PRECISION + """
uniform sampler2D u_scene;
uniform sampler2D u_bloomNear;
uniform sampler2D u_bloomFar;
uniform float u_bloomNearIntensity;
uniform float u_bloomFarIntensity;
uniform float u_scanline;
uniform float u_scanlinePeriod;
varying vec2 v_uv;
void main() {
    vec3 c = texture2D(u_scene, v_uv).rgb;
    c += texture2D(u_bloomNear, v_uv).rgb * u_bloomNearIntensity;
    c += texture2D(u_bloomFar, v_uv).rgb * u_bloomFarIntensity;
    float scan = 1.0 - u_scanline * (0.5 + 0.5 * sin(gl_FragCoord.y * 6.2831853 / u_scanlinePeriod));
    vec2 q = v_uv - 0.5;
    float vignette = 1.0 - dot(q, q) * 0.55;
    c *= scan * vignette;
    // Tone map leve para que sobreposições aditivas saturem em branco sem estourar.
    c = c / (1.0 + c * 0.35);
    gl_FragColor = vec4(c, 1.0);
}
"""

    fun compile(name: String, vertex: String, fragment: String): ShaderProgram {
        ShaderProgram.pedantic = false
        val program = ShaderProgram(vertex, fragment)
        require(program.isCompiled) { "Shader '$name' não compilou:\n${program.log}" }
        return program
    }
}
