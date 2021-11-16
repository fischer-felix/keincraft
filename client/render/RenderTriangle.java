package client.render;

import client.render.vk.device.Device;
import client.render.vk.device.Instance;
import client.render.vk.device.PhysicalDevice;
import client.render.vk.device.queue.Queue;
import client.render.vk.device.queue.QueueFamily;
import client.render.vk.draw.cmd.CommandBuffers;
import client.render.vk.draw.cmd.CommandPool;
import client.render.vk.draw.frame.FrameContext;
import client.render.vk.draw.submit.GraphicsSubmit;
import client.render.vk.draw.submit.ImageAcquire;
import client.render.vk.draw.submit.PresentSubmit;
import client.render.vk.draw.sync.Framebuffer;
import client.render.vk.pipeline.Pipeline;
import client.render.vk.pipeline.RenderPass;
import client.render.vk.pipeline.fixed.ColorBlend;
import client.render.vk.pipeline.fixed.Multisampling;
import client.render.vk.pipeline.fixed.Rasterizer;
import client.render.vk.pipeline.fixed.VertexInput;
import client.render.vk.pipeline.shader.Shader;
import client.render.vk.pipeline.shader.ShaderType;
import client.render.vk.present.Surface;
import client.render.vk.present.SwapChain;
import client.render.vk.present.image.Image;
import client.render.vk.present.image.ImageView;
import org.lwjgl.system.MemoryStack;

import java.util.List;

// TODO Recreate swapchain on window resize
// TODO Add vertex buffers
// TODO Add uniform buffers
// TODO Add projection, model, view matrix
// TODO Add movement
// TODO Add textures
// TODO Add depth buffers
// TODO Load models
// TODO Generate texture mipmaps
// TODO Add multisampling (antialiasing)
// TODO Generate chunk mesh
// TODO Render chunk
public class RenderTriangle {
    private final Window window;
    private final Instance instance;
    private final Surface surface;
    private final PhysicalDevice physicalDevice;
    private final Device device;
    private final Queue graphicsQueue;
    private final Queue presentQueue;

    private SwapChain swapChain;
    private List<ImageView> imageViews;
    private RenderPass renderPass;
    private Pipeline pipeline;
    private List<Framebuffer> framebuffers;
    private CommandPool commandPool;
    private CommandBuffers commandBuffers;
    private FrameContext frameContext;
    private ImageAcquire imageAcquire;

    public RenderTriangle() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            window = new Window(900, 900);
            instance = new Instance(stack);
            surface = new Surface(stack, instance, window);

            physicalDevice = PhysicalDevice.pickPhysicalDevice(stack, instance, surface);
            List<QueueFamily> queueFamilies = QueueFamily.createQueueFamilies(stack, physicalDevice.getQueueFamilies());
            device = new Device(stack, physicalDevice, queueFamilies);

            graphicsQueue = Queue.createQueue(stack, device, queueFamilies, physicalDevice.getQueueFamilies().getGraphicsFamilyIndex());
            presentQueue = Queue.createQueue(stack, device, queueFamilies, physicalDevice.getQueueFamilies().getPresentFamilyIndex());

            createSwapChain(stack);
        }
    }

    public static void main(String[] args) {
        RenderTriangle triangle = new RenderTriangle();
        triangle.loop();
        triangle.destroy();
    }

    public void createSwapChain(MemoryStack stack) {
        swapChain = new SwapChain(stack, physicalDevice, device, surface, window);
        List<Image> images = Image.createImages(stack, device, swapChain);
        imageViews = ImageView.createImageViews(stack, device, swapChain, images);
        List<Shader> shaders = List.of(
                Shader.readFromFile(stack, device, ShaderType.VERTEX_SHADER, "shaders/base.vert.spv"),
                Shader.readFromFile(stack, device, ShaderType.FRAGMENT_SHADER, "shaders/base.frag.spv")
        );

        renderPass = new RenderPass(stack, device, swapChain);
        ColorBlend colorBlend = new ColorBlend(stack);
        Multisampling multisampling = new Multisampling(stack);
        Rasterizer rasterizer = new Rasterizer(stack);
        VertexInput vertexInput = new VertexInput(stack);
        pipeline = new Pipeline(stack, device, swapChain, renderPass, shaders, vertexInput, rasterizer, multisampling, colorBlend);

        for (Shader shader : shaders) {
            shader.destroy(device);
        }

        framebuffers = Framebuffer.createFramebuffers(stack, device, renderPass, swapChain, imageViews);

        commandPool = new CommandPool(stack, physicalDevice, device);

        commandBuffers = new CommandBuffers(stack, device, commandPool, framebuffers, buffer -> {
            buffer.begin();
            buffer.beginRenderPass(swapChain, renderPass);
            buffer.beginPipeline(pipeline);
            buffer.draw(3, 1, 0, 0);
            buffer.endRenderPass();
            buffer.end();
        });

        frameContext = new FrameContext(stack, device, 2);
        imageAcquire = new ImageAcquire(swapChain);
    }

    public void loop() {
        while (!window.shouldClose()) {
            window.input();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                int imageIndex = imageAcquire.acquireImage(stack, device, swapChain, frameContext.getCurrentFrame());
                GraphicsSubmit.submitGraphics(stack, device, commandBuffers, graphicsQueue, frameContext.getCurrentFrame(), imageIndex);
                PresentSubmit.submitPresent(stack, swapChain, presentQueue, frameContext.getCurrentFrame(), imageIndex);
                frameContext.nextFrame();
            }
        }
    }

    public void destroySwapChain() {
        device.waitIdle();

        frameContext.destroy(device);

        commandPool.destroy(device);

        for (Framebuffer framebuffer : framebuffers) {
            framebuffer.destroy(device);
        }

        pipeline.destroy(device);
        renderPass.destroy(device);

        for (ImageView view : imageViews) {
            view.destroy(device);
        }

        swapChain.destroy(device);
    }

    public void destroy() {
        destroySwapChain();

        device.destroy();
        surface.destroy(instance);
        instance.destroy();
        window.destroy();
    }
}
