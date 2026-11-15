package dev.lastpagans.recruitsperformance.client.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20C.*;

public final class CrowdShader implements AutoCloseable {
    private static final String VERTEX = """
            #version 330 core
            layout(location=0) in vec3 aPosition;
            layout(location=1) in vec2 aUv;
            layout(location=2) in vec3 aNormal;
            layout(location=3) in float aBone;
            layout(location=4) in vec4 aColor;
            layout(location=5) in vec3 iPosition;
            layout(location=6) in float iYaw;
            layout(location=7) in float iPhase;
            layout(location=8) in float iAnimation;
            layout(location=9) in vec4 iColor;
            layout(location=10) in vec2 iLight;
            layout(location=11) in vec3 aBindPivot;
            layout(location=12) in float iPoseIndex;

            uniform mat4 uProjection;
            uniform mat4 uView;
            uniform vec3 uCamera;
            uniform float uAnimationEnabled;
            uniform samplerBuffer uPoseBuffer;
            uniform int uPoseCount;

            out vec2 vUv;
            out vec3 vNormal;
            out vec4 vColor;
            out float vLight;
            out float vFogDistance;

            mat3 rotX(float a) { float c=cos(a), s=sin(a); return mat3(1,0,0, 0,c,s, 0,-s,c); }
            mat3 rotY(float a) { float c=cos(a), s=sin(a); return mat3(c,0,-s, 0,1,0, s,0,c); }
            mat3 rotZ(float a) { float c=cos(a), s=sin(a); return mat3(c,s,0, -s,c,0, 0,0,1); }

            vec3 pivot(int bone) {
                return vec3(aBindPivot.x, -aBindPivot.y + 1.5, aBindPivot.z);
            }

            mat3 boneRotation(int bone) {
                if (uAnimationEnabled < 0.5) return mat3(1.0);
                int pose = int(iPoseIndex + 0.5);
                if (pose >= 0 && pose < uPoseCount) {
                    int texel = pose * 12 + bone * 2;
                    vec3 angles = texelFetch(uPoseBuffer, texel).xyz;
                    return rotZ(-angles.z) * rotY(angles.y) * rotX(-angles.x);
                }
                float walk = sin(iPhase * (iAnimation == 2.0 ? 0.75 : 0.55));
                if (iAnimation == 1.0 || iAnimation == 2.0) {
                    float amount = iAnimation == 2.0 ? 0.95 : 0.62;
                    if (bone == 2 || bone == 5) return rotX(walk * amount);
                    if (bone == 3 || bone == 4) return rotX(-walk * amount);
                }
                if (iAnimation == 3.0 && bone == 2) return rotX(-1.85 + 0.45 * sin(iPhase * 1.7));
                if (iAnimation == 4.0 && bone == 3) return rotX(-1.05) * rotY(-0.65);
                if (iAnimation == 5.0) {
                    if (bone == 2) return rotX(-1.45) * rotY(-0.35);
                    if (bone == 3) return rotX(-1.45) * rotY(0.35);
                }
                if (iAnimation == 6.0) {
                    if (bone == 2) return rotX(-1.30) * rotY(-0.18);
                    if (bone == 3) return rotX(-1.30) * rotY(0.55);
                }
                if (iAnimation == 7.0 && (bone == 1 || bone == 0)) return rotX(-0.18 * sin(iPhase * 2.4));
                return mat3(1.0);
            }

            void main() {
                int bone = int(aBone + 0.5);
                vec3 p = vec3(aPosition.x, -aPosition.y + 1.5, aPosition.z);
                vec3 n = vec3(aNormal.x, -aNormal.y, aNormal.z);
                vec3 joint = pivot(bone);
                int pose = int(iPoseIndex + 0.5);
                if (uAnimationEnabled >= 0.5 && pose >= 0 && pose < uPoseCount) {
                    vec3 posePivot = texelFetch(uPoseBuffer, pose * 12 + bone * 2 + 1).xyz;
                    joint = vec3(posePivot.x, -posePivot.y + 1.5, posePivot.z);
                }
                mat3 boneMatrix = boneRotation(bone);
                vec3 bindJoint = pivot(bone);
                p = joint + boneMatrix * (p - bindJoint);
                n = boneMatrix * n;
                if (iAnimation == 8.0) p = rotZ(1.35) * (p - vec3(0.0, 0.9, 0.0)) + vec3(0.0, 0.45, 0.0);
                mat3 facing = rotY(iYaw);
                vec3 world = iPosition + facing * p;
                vec4 viewPosition = uView * vec4(world - uCamera, 1.0);
                gl_Position = uProjection * viewPosition;
                vUv = aUv;
                vNormal = normalize(facing * n);
                vColor = aColor * iColor;
                vLight = clamp(max(iLight.x, iLight.y), 0.12, 1.0);
                vFogDistance = length(viewPosition.xyz);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec3 vNormal;
            in vec4 vColor;
            in float vLight;
            in float vFogDistance;
            uniform sampler2D uTexture;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            out vec4 fragColor;
            void main() {
                vec4 texel = texture(uTexture, vUv) * vColor;
                if (texel.a < 0.1) discard;
                float diffuse = 0.68 + 0.32 * max(dot(normalize(vNormal), normalize(vec3(0.2, 1.0, -0.35))), 0.0);
                vec3 lit = texel.rgb * vLight * diffuse;
                float fog = clamp((uFogEnd - vFogDistance) / max(uFogEnd - uFogStart, 0.001), 0.0, 1.0);
                fragColor = vec4(mix(uFogColor, lit, fog), texel.a);
            }
            """;

    private final int program;
    private final int projection;
    private final int view;
    private final int camera;
    private final int animationEnabled;
    private final int poseCount;
    private final int fogColor;
    private final int fogStart;
    private final int fogEnd;

    public CrowdShader() {
        int vertex = compile(GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
            glDeleteProgram(program);
            throw new IllegalStateException("Crowd shader link failed: " + log);
        }
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        projection = glGetUniformLocation(program, "uProjection");
        view = glGetUniformLocation(program, "uView");
        camera = glGetUniformLocation(program, "uCamera");
        animationEnabled = glGetUniformLocation(program, "uAnimationEnabled");
        poseCount = glGetUniformLocation(program, "uPoseCount");
        fogColor = glGetUniformLocation(program, "uFogColor");
        fogStart = glGetUniformLocation(program, "uFogStart");
        fogEnd = glGetUniformLocation(program, "uFogEnd");
        use();
        glUniform1i(glGetUniformLocation(program, "uTexture"), 0);
        glUniform1i(glGetUniformLocation(program, "uPoseBuffer"), 1);
    }

    public void use() { glUseProgram(program); }

    public void uniforms(Matrix4f projectionMatrix, Matrix4f viewMatrix, double cameraX, double cameraY,
                         double cameraZ, boolean animate, int poses) {
        use();
        matrix(projection, projectionMatrix);
        matrix(view, viewMatrix);
        glUniform3f(camera, (float) cameraX, (float) cameraY, (float) cameraZ);
        glUniform1f(animationEnabled, animate ? 1.0F : 0.0F);
        glUniform1i(poseCount, poses);
        float[] color = RenderSystem.getShaderFogColor();
        glUniform3f(fogColor, color[0], color[1], color[2]);
        glUniform1f(fogStart, RenderSystem.getShaderFogStart());
        glUniform1f(fogEnd, RenderSystem.getShaderFogEnd());
    }

    @Override
    public void close() { glDeleteProgram(program); }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            RecruitsPerformance.LOGGER.error("Crowd shader compile failure: {}", log);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    private static void matrix(int location, Matrix4f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer data = stack.mallocFloat(16);
            value.get(data);
            GL20C.glUniformMatrix4fv(location, false, data);
        }
    }
}
