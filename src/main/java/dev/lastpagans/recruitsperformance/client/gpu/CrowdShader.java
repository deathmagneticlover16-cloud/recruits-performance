package dev.lastpagans.recruitsperformance.client.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lastpagans.recruitsperformance.RecruitsPerformance;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.lwjgl.opengl.GL20C.*;

public final class CrowdShader implements AutoCloseable {
    private static final int SOURCE_GUARD_BYTES = 64;
    private static final int MAX_SOURCE_BYTES = 1024 * 1024;
    private static final String MINIMAL_VERTEX = """
            #version 330 core
            layout(location=0) in vec3 position;
            void main() { gl_Position = vec4(position, 1.0); }
            """;
    private static final String MINIMAL_FRAGMENT = """
            #version 330 core
            out vec4 color;
            void main() { color = vec4(1.0); }
            """;
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
            layout(location=13) in float iScale;

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
            out float vHurt;

            mat3 rotX(float a) { float c=cos(a), s=sin(a); return mat3(1,0,0, 0,c,s, 0,-s,c); }
            mat3 rotY(float a) { float c=cos(a), s=sin(a); return mat3(c,0,-s, 0,1,0, s,0,c); }
            mat3 rotZ(float a) { float c=cos(a), s=sin(a); return mat3(c,s,0, -s,c,0, 0,0,1); }

            vec3 pivot(int bone) {
                return vec3(-aBindPivot.x, -aBindPivot.y + 1.5, aBindPivot.z);
            }

            bool exactPose(int bone) {
                int pose = int(floor(iPoseIndex + 0.5));
                return uAnimationEnabled >= 0.5 && pose >= 0 && pose < uPoseCount && bone >= 0 && bone < 16;
            }

            void applyExactPose(int bone, inout vec3 position, inout vec3 normal) {
                int pose = int(floor(iPoseIndex + 0.5));
                int texel = pose * 64 + bone * 4;
                vec3 c0 = texelFetch(uPoseBuffer, texel).xyz;
                vec3 c1 = texelFetch(uPoseBuffer, texel + 1).xyz;
                vec3 c2 = texelFetch(uPoseBuffer, texel + 2).xyz;
                vec3 translation = texelFetch(uPoseBuffer, texel + 3).xyz;
                mat3 skin = mat3(c0, c1, c2);
                position = skin * position + translation;
                normal = skin * normal;
            }

            mat3 proceduralRotation(int bone) {
                if (uAnimationEnabled < 0.5) return mat3(1.0);
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
                if (iAnimation == 9.0) {
                    float progress = clamp(iPhase / 8.0, 0.0, 1.0);
                    float strike = sin(progress * 3.1415926);
                    float sweep = sin(progress * 6.2831853);
                    if (bone == 1) return rotY(-0.24 * sweep);
                    if (bone == 2) return rotX(-2.35 * strike) * rotY(-0.42 * sweep);
                    if (bone == 3) return rotX(-0.32 * strike) * rotY(0.16 * sweep);
                }
                return mat3(1.0);
            }

            void main() {
                int bone = int(aBone + 0.5);
                vec3 modelPosition = aPosition;
                vec3 modelNormal = aNormal;
                bool exact = exactPose(bone);
                if (exact) {
                    applyExactPose(bone, modelPosition, modelNormal);
                }
                vec3 p = vec3(-modelPosition.x, -modelPosition.y + 1.5, modelPosition.z);
                vec3 n = vec3(-modelNormal.x, -modelNormal.y, modelNormal.z);
                mat3 boneMatrix = exact ? mat3(1.0) : proceduralRotation(bone);
                vec3 bindJoint = pivot(bone);
                p = bindJoint + boneMatrix * (p - bindJoint);
                n = boneMatrix * n;
                p *= iScale;
                if (iAnimation == 8.0) {
                    float death = clamp(sqrt(max(((iPhase - 1.0) / 20.0) * 1.6, 0.0)), 0.0, 1.0);
                    p = rotZ(death * 1.5707963) * p;
                }
                mat3 facing = rotY(iYaw);
                vec3 world = iPosition + facing * p;
                vec4 viewPosition = uView * vec4(world - uCamera, 1.0);
                gl_Position = uProjection * viewPosition;
                vUv = aUv;
                vNormal = normalize(facing * n);
                vColor = aColor * iColor;
                vLight = clamp(max(iLight.x, iLight.y), 0.12, 1.0);
                vFogDistance = length(viewPosition.xyz);
                vHurt = iAnimation == 7.0 ? 1.0 : 0.0;
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec3 vNormal;
            in vec4 vColor;
            in float vLight;
            in float vFogDistance;
            in float vHurt;
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
                lit = mix(lit, vec3(1.0, 0.08, 0.08), vHurt * 0.24);
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
        validateContext();
        runMinimalPreflight();
        int created = buildProgram(VERTEX, FRAGMENT, "crowd");
        int localProjection;
        int localView;
        int localCamera;
        int localAnimationEnabled;
        int localPoseCount;
        int localFogColor;
        int localFogStart;
        int localFogEnd;
        try {
            localProjection = uniform(created, "uProjection");
            localView = uniform(created, "uView");
            localCamera = uniform(created, "uCamera");
            localAnimationEnabled = uniform(created, "uAnimationEnabled");
            localPoseCount = uniform(created, "uPoseCount");
            localFogColor = uniform(created, "uFogColor");
            localFogStart = uniform(created, "uFogStart");
            localFogEnd = uniform(created, "uFogEnd");
            glUseProgram(created);
            glUniform1i(uniform(created, "uTexture"), 0);
            glUniform1i(uniform(created, "uPoseBuffer"), 1);
            checkNoGlError("initializing crowd shader uniforms");
        } catch (RuntimeException | Error failure) {
            glDeleteProgram(created);
            throw failure;
        }
        program = created;
        projection = localProjection;
        view = localView;
        camera = localCamera;
        animationEnabled = localAnimationEnabled;
        poseCount = localPoseCount;
        fogColor = localFogColor;
        fogStart = localFogStart;
        fogEnd = localFogEnd;
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

    private static int buildProgram(String vertexSource, String fragmentSource, String label) {
        int vertex = 0;
        int fragment = 0;
        int linkedProgram = 0;
        boolean success = false;
        try {
            vertex = compile(GL_VERTEX_SHADER, vertexSource, label + "-vertex");
            fragment = compile(GL_FRAGMENT_SHADER, fragmentSource, label + "-fragment");
            linkedProgram = glCreateProgram();
            if (linkedProgram == 0) throw new IllegalStateException("OpenGL returned program id 0 for " + label);
            glAttachShader(linkedProgram, vertex);
            glAttachShader(linkedProgram, fragment);
            glLinkProgram(linkedProgram);
            if (glGetProgrami(linkedProgram, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException(label + " shader link failed: " + glGetProgramInfoLog(linkedProgram));
            }
            checkNoGlError("linking " + label + " shader program");
            success = true;
            return linkedProgram;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
            if (!success && linkedProgram != 0) glDeleteProgram(linkedProgram);
        }
    }

    private static int compile(int type, String source, String label) {
        SourceSnapshot snapshot = captureSource(type, source, label);
        int shader = glCreateShader(type);
        if (shader == 0) throw new IllegalStateException("OpenGL returned shader id 0 for " + label);
        try {
            submitNullTerminated(shader, snapshot);
            String accepted = glGetShaderSource(shader);
            if (accepted == null) {
                throw new IllegalStateException(label + " source round-trip returned null");
            }
            if (!source.equals(accepted)) {
                throw new IllegalStateException(label + " source round-trip mismatch: submitted "
                        + snapshot.byteLength + " UTF-8 bytes, driver returned "
                        + accepted.getBytes(StandardCharsets.UTF_8).length);
            }
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                String log = glGetShaderInfoLog(shader);
                throw new IllegalStateException(label + " compile failed: " + log);
            }
            checkNoGlError("compiling " + label);
            return shader;
        } catch (RuntimeException | Error failure) {
            glDeleteShader(shader);
            RecruitsPerformance.LOGGER.error("Crowd shader stage failed: label={} type={} sha256={} dump={}",
                    label, snapshot.typeName, snapshot.sha256, snapshot.dump, failure);
            throw failure;
        }
    }

    private static void submitNullTerminated(int shader, SourceSnapshot snapshot) {
        ByteBuffer nativeSource = MemoryUtil.memCalloc(snapshot.bytes.length + 1 + SOURCE_GUARD_BYTES);
        PointerBuffer pointers = MemoryUtil.memAllocPointer(1);
        try {
            nativeSource.put(snapshot.bytes);
            nativeSource.position(0);
            pointers.put(0, MemoryUtil.memAddress(nativeSource));
            RecruitsPerformance.LOGGER.info(
                    "Submitting GLSL to OpenGL: label={} type={} chars={} utf8Bytes={} sha256={} "
                            + "lengths=NULL nulTerminated=true guardBytes={} dump={}\n"
                            + "----- BEGIN EXACT {} SOURCE -----\n{}----- END EXACT {} SOURCE -----",
                    snapshot.label, snapshot.typeName, snapshot.charLength, snapshot.byteLength,
                    snapshot.sha256, SOURCE_GUARD_BYTES, snapshot.dump,
                    snapshot.label, snapshot.source, snapshot.label);
            // The CharSequence overload uses LWJGL's temporary stack array and a non-NULL length pointer.
            // Keeping our direct UTF-8 allocation alive and passing lengths=NULL avoids that AMD crash path.
            glShaderSource(shader, pointers, (java.nio.IntBuffer) null);
            checkNoGlError("submitting " + snapshot.label + " source");
        } finally {
            MemoryUtil.memFree(pointers);
            MemoryUtil.memFree(nativeSource);
        }
    }

    private static SourceSnapshot captureSource(int type, String source, String label) {
        if (source.isEmpty()) throw new IllegalArgumentException(label + " source is empty");
        if (source.indexOf('\0') >= 0) throw new IllegalArgumentException(label + " source contains an embedded NUL");
        byte[] bytes = strictUtf8(source, label);
        if (bytes.length > MAX_SOURCE_BYTES) {
            throw new IllegalArgumentException(label + " source is too large: " + bytes.length + " bytes");
        }
        String sha256 = sha256(bytes);
        String typeName = shaderType(type);
        Path dump = shaderDumpPath(label, typeName, sha256);
        try {
            Files.createDirectories(dump.getParent());
            Files.write(dump, bytes);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not persist exact GLSL source before native submission: " + dump,
                    failure);
        }
        return new SourceSnapshot(label, typeName, source, bytes, source.length(), bytes.length, sha256, dump);
    }

    private static byte[] strictUtf8(String source, String label) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(source));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException(label + " source is not valid UTF-8", failure);
        }
    }

    private static String sha256(byte[] source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Path shaderDumpPath(String label, String type, String sha256) {
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
        return gameDirectory.resolve("logs").resolve("recruits-performance-shaders")
                .resolve(label + "-" + type.toLowerCase() + "-" + sha256.substring(0, 12) + ".glsl");
    }

    private static void validateContext() {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Crowd shader initialization attempted off the render thread");
        }
        var capabilities = GL.getCapabilities();
        if (!capabilities.OpenGL33) {
            throw new IllegalStateException("Crowd renderer requires an OpenGL 3.3 context");
        }
        drainPreexistingGlErrors();
        String vendor = org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VENDOR);
        String renderer = org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER);
        String version = org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION);
        String shadingLanguage = org.lwjgl.opengl.GL11C.glGetString(
                org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION);
        if (vendor == null || renderer == null || version == null || shadingLanguage == null) {
            throw new IllegalStateException("OpenGL context strings are unavailable on the render thread");
        }
        RecruitsPerformance.LOGGER.info(
                "Crowd shader OpenGL context: vendor={} renderer={} version={} glsl={} thread={}",
                vendor, renderer, version, shadingLanguage, Thread.currentThread().getName());
        checkNoGlError("validating the OpenGL context");
    }

    private static void drainPreexistingGlErrors() {
        int error;
        int count = 0;
        while ((error = org.lwjgl.opengl.GL11C.glGetError()) != org.lwjgl.opengl.GL11C.GL_NO_ERROR
                && count++ < 16) {
            RecruitsPerformance.LOGGER.warn(
                    "Clearing pre-existing OpenGL error 0x{} before crowd shader initialization",
                    Integer.toHexString(error));
        }
    }

    private static void runMinimalPreflight() {
        RecruitsPerformance.LOGGER.info("Starting minimal GLSL submission preflight");
        int minimalProgram = buildProgram(MINIMAL_VERTEX, MINIMAL_FRAGMENT, "preflight");
        glDeleteProgram(minimalProgram);
        checkNoGlError("deleting the minimal preflight program");
        RecruitsPerformance.LOGGER.info("Minimal GLSL submission preflight passed");
    }

    private static int uniform(int shaderProgram, String name) {
        int location = glGetUniformLocation(shaderProgram, name);
        if (location < 0) throw new IllegalStateException("Required crowd shader uniform is missing: " + name);
        return location;
    }

    private static void checkNoGlError(String operation) {
        int error = org.lwjgl.opengl.GL11C.glGetError();
        if (error != org.lwjgl.opengl.GL11C.GL_NO_ERROR) {
            throw new IllegalStateException("OpenGL error 0x" + Integer.toHexString(error) + " while " + operation);
        }
    }

    private static String shaderType(int type) {
        return switch (type) {
            case GL_VERTEX_SHADER -> "VERTEX";
            case GL_FRAGMENT_SHADER -> "FRAGMENT";
            default -> "0x" + Integer.toHexString(type);
        };
    }

    private static void matrix(int location, Matrix4f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer data = stack.mallocFloat(16);
            value.get(data);
            GL20C.glUniformMatrix4fv(location, false, data);
        }
    }

    private record SourceSnapshot(String label, String typeName, String source, byte[] bytes,
                                  int charLength, int byteLength, String sha256, Path dump) {}
}
