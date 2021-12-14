package client.graphics.vk.instance;

import client.graphics.vk.device.PhysicalDevice;
import client.graphics.vk.instance.properties.ApplicationInfo;
import client.graphics.vk.instance.properties.DebugInfo;
import client.graphics.vk.instance.properties.InstanceExtension;
import client.graphics.vk.instance.properties.InstanceInfo;
import client.graphics.vk.instance.properties.InstanceLayer;
import client.graphics.vk.instance.properties.Version;
import client.graphics.vk.memory.MemoryContext;
import client.graphics.vk.models.function.CheckFunction;
import client.graphics.vk.models.function.EnumerateFunction;
import client.graphics.vk.models.pointers.DestroyableReferencePointer;
import common.util.Buffers;
import common.util.enums.HasValue;
import common.util.enums.Maskable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugReport;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDebugReportCallbackCreateInfoEXT;
import org.lwjgl.vulkan.VkDebugReportCallbackEXT;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.tinylog.Logger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link VkInstance} wrapper. A vulkan instance holds the vulkan state. It can be expanded through {@link InstanceExtension} and
 * inputs can be validated through {@link InstanceLayer}. A debug output can be achieved through {@link DebugInfo} and {@link DebugLogger}.
 * Thus is holds general information regarding the application {@link ApplicationInfo}
 *
 * @see InstanceInfo
 * @see ApplicationInfo
 * @see DebugInfo
 * @see DebugLogger
 */
public class VulkanInstance extends DestroyableReferencePointer<VkInstance> {
    private final VkInstance handle;
    private final InstanceInfo info;
    private final long debugHandle;

    /**
     * Creates new vulkan instance, check extensions and layers, initializes debug reporter.
     * If extensions and layers are not available throw exception (required) or log warning (optional).
     * Checks if required vulkan api version requirement is meet.
     *
     * @param applicationInfo {@link ApplicationInfo} containing application information
     * @param info            {@link InstanceInfo} information for initializing the instance
     * @param debugInfo       {@link DebugInfo} information regarding the debug log. Can be null to skip debug reporter initialization
     */
    public VulkanInstance(@NotNull ApplicationInfo applicationInfo, @NotNull InstanceInfo info, @Nullable DebugInfo debugInfo) {
        this.info = info;

        MemoryStack stack = MemoryContext.getStack();

        // Fetch vulkan api version
        IntBuffer pAvailableApiVersion = stack.mallocInt(1);
        CheckFunction.execute(() -> VK11.vkEnumerateInstanceVersion(pAvailableApiVersion));
        Version availableApiVersion = Version.fromVulkan(pAvailableApiVersion.get(0));

        // Check if requested version <= available version
        if (availableApiVersion.compareTo(applicationInfo.getApiVersion()) < 0) {
            throw new RuntimeException("Available api version " + availableApiVersion + " is lower than required api version " + applicationInfo.getApiVersion());
        }

        Logger.debug("API Version: {}", availableApiVersion);

        // Fetch and check extensions and layers
        PointerBuffer extensions = fetchExtensions(stack, info);
        PointerBuffer layers = fetchLayers(stack, info);

        // Convert application properties to vulkan equivalence
        ByteBuffer applicationName = null;
        if (applicationInfo.getApplicationName() != null) {
            applicationName = stack.ASCII(applicationInfo.getApplicationName());
        }

        ByteBuffer engineName = null;
        if (applicationInfo.getEngineName() != null) {
            engineName = stack.ASCII(applicationInfo.getEngineName());
        }

        int applicationVersion = 0;
        if (applicationInfo.getApplicationVersion() != null) {
            applicationVersion = applicationInfo.getApplicationVersion().toVulkan();
        }

        int engineVersion = 0;
        if (applicationInfo.getEngineVersion() != null) {
            engineVersion = applicationInfo.getEngineVersion().toVulkan();
        }

        // Malloc create infos
        VkApplicationInfo appInfo = VkApplicationInfo.malloc(stack)
                .sType$Default()
                .pNext(0)
                .pApplicationName(applicationName)
                .pEngineName(engineName)
                .applicationVersion(applicationVersion)
                .engineVersion(engineVersion)
                .apiVersion(applicationInfo.getApiVersion().toVulkan());

        VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.malloc(stack)
                .sType$Default()
                .flags(0)
                .pNext(0)
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(extensions)
                .ppEnabledLayerNames(layers);

        // Check if debug report is enabled and which levels should be used
        VkDebugReportCallbackCreateInfoEXT debugCreateInfo = setupDebugCallback(stack, createInfo, debugInfo);

        // Create instance
        PointerBuffer pHandle = stack.mallocPointer(1);
        CheckFunction.execute(() -> VK10.vkCreateInstance(createInfo, null, pHandle));
        handle = new VkInstance(pHandle.get(0), createInfo);

        debugHandle = finishDebugCallback(stack, debugCreateInfo);
    }

    /**
     * Fetches all {@link PhysicalDevice}s and their properties
     *
     * @return {@link PhysicalDevice} list
     */
    @NotNull
    public List<PhysicalDevice> getPhysicalDevices() {
        MemoryStack stack = MemoryContext.getStack();

        // Fetch physical devices
        PointerBuffer pPhysicalDevices = EnumerateFunction.execute(stack.mallocInt(1),
                (pCount, pBuffer) -> VK10.vkEnumeratePhysicalDevices(handle, pCount, pBuffer),
                stack::mallocPointer);

        // Create PhysicalDevice wrapper for every device
        int physicalDeviceCount = pPhysicalDevices.capacity();
        List<PhysicalDevice> physicalDevices = new ArrayList<>(physicalDeviceCount);

        for (int i = 0; i < physicalDeviceCount; i++) {
            physicalDevices.add(new PhysicalDevice(new VkPhysicalDevice(pPhysicalDevices.get(i), handle)));
        }

        return physicalDevices;
    }

    @Override
    protected void internalDestroy() {
        // If debug report exist destroy it
        if (debugHandle != -1L) {
            EXTDebugReport.vkDestroyDebugReportCallbackEXT(handle, debugHandle, null);
        }

        // Destroy instance
        VK10.vkDestroyInstance(handle, null);
    }

    @Override
    protected long getInternalHandle() {
        return handle.address();
    }

    @Override
    protected VkInstance getInternalReference() {
        return handle;
    }

    /**
     * Returns {@link InstanceInfo} about current information regarding this instance
     *
     * @return {@link InstanceInfo}
     * @see InstanceInfo
     */
    @NotNull
    public InstanceInfo getInfo() {
        return info;
    }

    private VkDebugReportCallbackCreateInfoEXT setupDebugCallback(MemoryStack stack, VkInstanceCreateInfo instanceCreateInfo, DebugInfo debugInfo) {
        if (debugInfo == null) {
            return null;
        }

        // If no severities given don't create debug report callback
        if (debugInfo.getSeverities() == null || debugInfo.getSeverities().isEmpty()) {
            return null;
        }

        VkDebugReportCallbackCreateInfoEXT createInfo = VkDebugReportCallbackCreateInfoEXT.malloc(stack)
                .sType$Default()
                .flags(0)
                .pNext(0)
                .flags(Maskable.toBitMask(debugInfo.getSeverities()))
                .pfnCallback(VkDebugReportCallbackEXT.create(new DebugLogger()))
                .pUserData(0);

        // Hand over debug reporter to instance create info to show instance creating errors
        instanceCreateInfo.pNext(createInfo);
        return createInfo;
    }

    private long finishDebugCallback(MemoryStack stack, VkDebugReportCallbackCreateInfoEXT createInfo) {
        if (createInfo == null) {
            return -1;
        }

        LongBuffer pDebugHandle = stack.mallocLong(1);
        CheckFunction.execute(() -> EXTDebugReport.vkCreateDebugReportCallbackEXT(handle, createInfo, null, pDebugHandle));
        return pDebugHandle.get(0);
    }

    private PointerBuffer fetchExtensions(MemoryStack stack, InstanceInfo info) {
        // Fetch available extensions
        VkExtensionProperties.Buffer vkAvailableExtensions = EnumerateFunction.execute(stack.mallocInt(1),
                (pCount, pBuffer) -> VK10.vkEnumerateInstanceExtensionProperties((String) null, pCount, pBuffer),
                (count) -> VkExtensionProperties.malloc(count, stack));

        List<InstanceExtension> requiredExtensions = new ArrayList<>(info.getRequiredExtensions());
        List<InstanceExtension> optionalExtensions = new ArrayList<>(info.getOptionalExtensions());
        List<InstanceExtension> availableExtensions = InstanceExtension.fromBuffer(vkAvailableExtensions);
        List<InstanceExtension> enabledExtensions = info.getEnabledExtensions();

        Logger.debug("Found {} extensions", availableExtensions.size());

        // Check every available extension if it should be added
        checkRequiredOptional(availableExtensions, requiredExtensions, optionalExtensions, enabledExtensions);

        // If not all required extensions are available then throw exception
        if (!requiredExtensions.isEmpty()) {
            throw new RuntimeException("Failed to find required vulkan instance extensions: " + requiredExtensions
                    .stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", ")));
        }

        // If not all optional extensions are available then log warning
        if (!optionalExtensions.isEmpty()) {
            Logger.debug("Could not find optional vulkan instance extensions: {}", optionalExtensions);
        }

        // Return extension names
        return Buffers.toStringBuffer(enabledExtensions);
    }

    private PointerBuffer fetchLayers(MemoryStack stack, InstanceInfo info) {
        // Fetch available layers
        VkLayerProperties.Buffer pAvailableLayers = EnumerateFunction.execute(stack.mallocInt(1),
                VK10::vkEnumerateInstanceLayerProperties,
                count -> VkLayerProperties.malloc(count, stack));

        List<InstanceLayer> requiredLayers = new ArrayList<>(info.getRequiredLayers());
        List<InstanceLayer> optionalLayers = new ArrayList<>(info.getOptionalLayers());
        List<InstanceLayer> availableLayers = InstanceLayer.fromBuffer(pAvailableLayers);
        List<InstanceLayer> enabledLayers = info.getEnabledLayers();

        Logger.debug("Found {} layers", availableLayers.size());

        // Check every available layers if it should be added
        checkRequiredOptional(availableLayers, requiredLayers, optionalLayers, enabledLayers);

        // If not all required layers are available then throw exception
        if (!requiredLayers.isEmpty()) {
            throw new RuntimeException("Failed to find required vulkan instance layers: " + requiredLayers
                    .stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", ")));
        }

        // If not all optional layers are available then log warning
        if (!optionalLayers.isEmpty()) {
            Logger.debug("Could not find optional vulkan instance layers: {}", optionalLayers);
        }

        // Return layer names
        return Buffers.toStringBuffer(enabledLayers);
    }

    private <T extends HasValue<String>> void checkRequiredOptional(List<T> available, List<T> required, List<T> optional, List<T> enabled) {
        // Check every available if it should be added
        for (T t : available) {
            Logger.trace(t.getValue());

            // If is required add to enabled and remove from required layer
            int requiredIndex = required.indexOf(t);
            if (requiredIndex != -1) {
                required.remove(requiredIndex);
                enabled.add(t);
                continue;
            }

            // If is optional add to enabled and remove from optional layer
            int optionalIndex = optional.indexOf(t);
            if (optionalIndex != -1) {
                optional.remove(optionalIndex);
                enabled.add(t);
            }
        }
    }
}
