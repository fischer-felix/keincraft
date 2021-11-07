import org.lwjgl.bgfx.BGFXInit;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;

import java.util.Objects;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main {
    public static void main(String[] args) {
        boolean isServer = false;

        for (String arg : args) {
            if (Objects.equals(arg, "--server")) {
                isServer = true;
                break;
            }
        }

        if (isServer) {
            runServer();
        } else {
            runClient();
        }
    }

    public static void runClient() {
        int width  = 1024;
        int height = 480;

        if (!glfwInit()) {
            throw new RuntimeException("Error initializing GLFW");
        }

        // the client (renderer) API is managed by bgfx
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        if (Platform.get() == Platform.MACOSX) {
            glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW_FALSE);
        }

        long window = glfwCreateWindow(width, height, "25-C99", 0, 0);
        if (window == NULL) {
            throw new RuntimeException("Error creating GLFW window");
        }

        glfwSetKeyCallback(window, (windowHnd, key, scancode, action, mods) -> {
            if (action != GLFW_RELEASE) {
                return;
            }

            switch (key) {
                case GLFW_KEY_ESCAPE:
                    glfwSetWindowShouldClose(windowHnd, true);
                    break;
            }
        });

        try (MemoryStack stack = stackPush()) {
            BGFXInit init = BGFXInit.mallocStack(stack);
            bgfx_init_ctor(init);
            init
                    .type(BGFX_RENDERER_TYPE_OPENGL)
                    .resolution(it -> it
                            .width(width)
                            .height(height));
                            //.reset(BGFX_RESET_VSYNC));

            switch (Platform.get()) {
                case LINUX -> init.platformData()
                        .ndt(GLFWNativeX11.glfwGetX11Display())
                        .nwh(GLFWNativeX11.glfwGetX11Window(window));
                case MACOSX -> init.platformData()
                        .nwh(GLFWNativeCocoa.glfwGetCocoaWindow(window));
                case WINDOWS -> init.platformData()
                        .nwh(GLFWNativeWin32.glfwGetWin32Window(window));
            }

            if (!bgfx_init(init)) {
                throw new RuntimeException("Error initializing bgfx renderer");
            }
        }

        System.out.println("bgfx renderer: " + bgfx_get_renderer_name(bgfx_get_renderer_type()));

        int[] renderTypes = new int[BGFX_RENDERER_TYPE_COUNT];
        int count = bgfx_get_supported_renderers(renderTypes);

        for(int i = 0; i < count; i++) {
            System.out.println(bgfx_get_renderer_name(renderTypes[i]));
        }

        // Enable debug text.
        bgfx_set_debug(BGFX_DEBUG_TEXT);

        bgfx_set_view_clear(0, BGFX_CLEAR_COLOR | BGFX_CLEAR_DEPTH, 0x303030ff, 1.0f, 0);

        //ByteBuffer logo = Logo.createLogo();

        long lastTime = 0L;

        while (!glfwWindowShouldClose(window)) {
            long currentTime = System.nanoTime();
            System.out.println(currentTime - lastTime);
            lastTime = currentTime;

            glfwPollEvents();

            // Set view 0 default viewport.
            bgfx_set_view_rect(0, 0, 0, width, height);

            // This dummy draw call is here to make sure that view 0 is cleared
            // if no other draw calls are submitted to view 0.
            bgfx_touch(0);

            // Use debug font to print information about this example.
            bgfx_dbg_text_clear(0, false);
/*            bgfx_dbg_text_image(Math.max(width / 2 / 8, 20) - 20,
                    Math.max(height / 2 / 16, 6) - 6,
                    40,
                    12,
                    logo,
                    160
            );*/

            bgfx_dbg_text_printf(0, 1, 0x1f, "bgfx/examples/25-c99");
            bgfx_dbg_text_printf(0, 2, 0x3f, "Description: Initialization and debug text with C99 API.");

            bgfx_dbg_text_printf(0, 3, 0x0f, "Color can be changed with ANSI \u001b[9;me\u001b[10;ms\u001b[11;mc\u001b[12;ma\u001b[13;mp\u001b[14;me\u001b[0m code too.");

            bgfx_dbg_text_printf(80, 4, 0x0f, "\u001b[;0m    \u001b[;1m    \u001b[; 2m    \u001b[; 3m    \u001b[; 4m    \u001b[; 5m    \u001b[; 6m    \u001b[; 7m    \u001b[0m");
            bgfx_dbg_text_printf(80, 5, 0x0f, "\u001b[;8m    \u001b[;9m    \u001b[;10m    \u001b[;11m    \u001b[;12m    \u001b[;13m    \u001b[;14m    \u001b[;15m    \u001b[0m");

            // Advance to next frame. Rendering thread will be kicked to
            // process submitted rendering primitives.
            bgfx_frame(false);
        }

        bgfx_shutdown();

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void runServer() {

    }
}